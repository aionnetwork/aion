/* ******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 *
 ******************************************************************************/

package org.aion.api.server.pb;

import static org.aion.base.util.ByteUtil.EMPTY_BYTE_ARRAY;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.aion.api.server.ApiAion;
import org.aion.api.server.ApiUtil;
import org.aion.api.server.IApiAion;
import org.aion.api.server.pb.Message.Retcode;
import org.aion.api.server.pb.Message.Servs;
import org.aion.api.server.types.ArgTxCall;
import org.aion.api.server.types.CompiledContr;
import org.aion.api.server.types.EvtContract;
import org.aion.api.server.types.EvtTx;
import org.aion.api.server.types.Fltr;
import org.aion.api.server.types.FltrCt;
import org.aion.api.server.types.SyncInfo;
import org.aion.api.server.types.TxPendingStatus;
import org.aion.api.server.types.TxRecpt;
import org.aion.api.server.types.TxRecptLg;
import org.aion.base.type.Address;
import org.aion.base.type.Hash256;
import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.base.type.ITxReceipt;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.base.util.TypeConverter;
import org.aion.equihash.EquihashMiner;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallback;
import org.aion.evtmgr.impl.es.EventExecuteService;
import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.evtmgr.impl.evt.EventTx;
import org.aion.mcf.account.Keystore;
import org.aion.mcf.vm.types.Log;
import org.aion.p2p.INode;
import org.aion.solidity.Abi;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.AionHub;
import org.aion.zero.impl.Version;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.collections4.map.LRUMap;
import org.json.JSONArray;

@SuppressWarnings("Duplicates")
public class ApiAion0 extends ApiAion implements IApiAion {

    public static final byte JAVAAPI_VAR = 2;
    private static final int JAVAAPI_REQHEADER_LEN = 4;
    private static final int TX_HASH_LEN = 32;
    private static final int ACCOUNT_CREATE_LIMIT = 100;

    private BlockingQueue<TxPendingStatus> pendingStatus;
    private BlockingQueue<TxWaitingMappingUpdate> txWait;
    private Map<ByteArrayWrapper, Map.Entry<ByteArrayWrapper, ByteArrayWrapper>> msgIdMapping;

    public static boolean heartBeatMsg(byte[] msg) {
        if (msg == null || msg.length != JAVAAPI_REQHEADER_LEN) {
            return false;
        }

        if (msg[0] < JAVAAPI_VAR) {
            return false;
        }

        return msg[1] == Message.Servs.s_hb_VALUE;
    }

    protected void onBlock(AionBlockSummary cbs) {
        Set<Long> keys = installedFilters.keySet();
        for (Long key : keys) {
            Fltr fltr = installedFilters.get(key);
            if (fltr.isExpired()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("<fltr key={} expired removed>", key);
                }
                installedFilters.remove(key);
            } else {
                List<AionTxReceipt> txrs = cbs.getReceipts();
                if (fltr.getType() == Fltr.Type.EVENT
                    && !Optional.ofNullable(txrs).orElse(Collections.emptyList()).isEmpty()) {
                    FltrCt _fltr = (FltrCt) fltr;

                    for (AionTxReceipt txr : txrs) {
                        AionTransaction tx = txr.getTransaction();
                        Address contractAddress =
                            Optional.ofNullable(tx.getTo()).orElse(tx.getContractAddress());

                        Integer cnt = 0;
                        txr.getLogInfoList()
                            .forEach(
                                bi ->
                                    bi.getTopics()
                                        .forEach(
                                            lg -> {
                                                if (_fltr.isFor(
                                                    contractAddress,
                                                    ByteUtil.toHexString(
                                                        lg))) {
                                                    IBlock<AionTransaction, ?>
                                                        blk =
                                                        (cbs)
                                                            .getBlock();
                                                    List<AionTransaction>
                                                        txList =
                                                        blk
                                                            .getTransactionsList();
                                                    int insideCnt = 0;
                                                    for (AionTransaction t :
                                                        txList) {
                                                        if (Arrays.equals(
                                                            t.getHash(),
                                                            tx.getHash())) {
                                                            break;
                                                        }
                                                        insideCnt++;
                                                    }

                                                    EvtContract ec =
                                                        new EvtContract(
                                                            bi.getAddress()
                                                                .toBytes(),
                                                            bi
                                                                .getData(),
                                                            blk
                                                                .getHash(),
                                                            blk
                                                                .getNumber(),
                                                            cnt,
                                                            ByteUtil
                                                                .toHexString(
                                                                    lg),
                                                            false,
                                                            insideCnt,
                                                            tx
                                                                .getHash());

                                                    _fltr.add(ec);
                                                }
                                            }));
                    }
                }
            }
        }
    }

    protected void pendingTxReceived(ITransaction _tx) {
        installedFilters
            .values()
            .forEach(
                (f) -> {
                    if (f.getType() == Fltr.Type.TRANSACTION) {
                        f.add(new EvtTx(_tx));
                    }
                });
    }

    protected void pendingTxUpdate(ITxReceipt _txRcpt, EventTx.STATE _state) {
        ByteArrayWrapper txHashW =
            ByteArrayWrapper.wrap(((AionTxReceipt) _txRcpt).getTransaction().getHash());

        if (LOG.isTraceEnabled()) {
            LOG.trace(
                "ApiAion0.onPendingTransactionUpdate - txHash: [{}], state: [{}]",
                txHashW.toString(),
                _state.getValue());
        }

        if (getMsgIdMapping().get(txHashW) != null) {
            if (pendingStatus.remainingCapacity() == 0) {
                pendingStatus.poll();
                LOG.warn(
                    "ApiAion0.onPendingTransactionUpdate - txPend ingStatus queue full, drop the first message.");
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace(
                    "ApiAion0.onPendingTransactionUpdate - the pending Tx state : [{}]",
                    _state.getValue());
            }

            pendingStatus.add(
                new TxPendingStatus(
                    txHashW,
                    getMsgIdMapping().get(txHashW).getValue(),
                    getMsgIdMapping().get(txHashW).getKey(),
                    _state.getValue(),
                    ByteArrayWrapper.wrap(
                        ((AionTxReceipt) _txRcpt).getExecutionResult() == null
                            ? EMPTY_BYTE_ARRAY
                            : ((AionTxReceipt) _txRcpt).getExecutionResult()),
                    ((AionTxReceipt) _txRcpt).getError()));

            if (_state.isPending()) {
                pendingReceipts.put(txHashW, ((AionTxReceipt) _txRcpt));
            } else {
                pendingReceipts.remove(txHashW);
                getMsgIdMapping().remove(txHashW);
            }
        } else {
            if (txWait.remainingCapacity() == 0) {
                txWait.poll();
                if (LOG.isTraceEnabled()) {
                    LOG.trace(
                        "ApiAion0.onPendingTransactionUpdate - txWait queue full, drop the first message.");
                }
            }

            // waiting origin Api call status been callback
            try {
                txWait.put(
                    new TxWaitingMappingUpdate(
                        txHashW, _state.getValue(), ((AionTxReceipt) _txRcpt)));
            } catch (InterruptedException e) {
                LOG.error(
                    "ApiAion0.onPendingTransactionUpdate txWait.put exception", e.getMessage());
            }
        }
    }

    private boolean isFilterEnabled;

    private Map<ByteArrayWrapper, AionBlockSummary> explorerBlockCache;

    private void cacheBlock(AionBlockSummary cbs) {
        // put the block summary in the cache
        explorerBlockCache.put(new ByteArrayWrapper(cbs.getBlock().getHash()), cbs);
    }

    private boolean isBlkCacheEnabled;

    private EventExecuteService eesBlkCache;

    private final class EpBlkCache implements Runnable {

        boolean go = true;

        @Override
        public void run() {
            while (go) {
                try {
                    IEvent e = eesBlkCache.take();
                    if (e.getEventType() == IHandler.TYPE.BLOCK0.getValue()
                        && e.getCallbackType() == EventBlock.CALLBACK.ONBLOCK0.getValue()) {
                        cacheBlock((AionBlockSummary) e.getFuncArgs().get(0));
                    } else if (e.getEventType() == IHandler.TYPE.POISONPILL.getValue()) {
                        go = false;
                    }
                } catch (Exception e) {
                    LOG.debug("EpBlkCache - excepted out", e);
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    public ApiAion0(IAionChain ac) {
        super(ac);
        this.pendingReceipts = Collections.synchronizedMap(new LRUMap<>(10000, 100));

        int MAP_SIZE = 50_000;
        this.pendingStatus = new LinkedBlockingQueue(MAP_SIZE);
        this.txWait = new LinkedBlockingQueue(MAP_SIZE);
        this.msgIdMapping = Collections.synchronizedMap(new LRUMap<>(MAP_SIZE, 100));

        initNrgOracle(ac);

        isFilterEnabled = CfgAion.inst().getApi().getZmq().isFiltersEnabled();

        isBlkCacheEnabled = CfgAion.inst().getApi().getZmq().isBlockSummaryCacheEnabled();

        if (isBlkCacheEnabled) {
            explorerBlockCache =
                Collections.synchronizedMap(new LRUMap<>(20)); // use the default loadfactor
            eesBlkCache =
                new EventExecuteService(
                    100_000, "explorer-blk-cache", Thread.MIN_PRIORITY, LOG);
            Set<Integer> eventSN = new HashSet<>();
            int sn = IHandler.TYPE.BLOCK0.getValue() << 8;
            eventSN.add(sn + EventBlock.CALLBACK.ONBLOCK0.getValue());
            eesBlkCache.setFilter(eventSN);
            eesBlkCache.start(new EpBlkCache());

            IHandler hdrBlk =
                this.ac.getAionHub().getEventMgr().getHandler(IHandler.TYPE.BLOCK0.getValue());
            if (hdrBlk != null) {
                hdrBlk.eventCallback(new EventCallback(eesBlkCache, LOG));
            }
        }

        if (isFilterEnabled) {
            startES("EpApi");

            IHandler hdrTx =
                this.ac.getAionHub().getEventMgr().getHandler(IHandler.TYPE.TX0.getValue());
            if (hdrTx != null) {
                hdrTx.eventCallback(new EventCallback(ees, LOG));
            }

            IHandler hdrBlk =
                this.ac.getAionHub().getEventMgr().getHandler(IHandler.TYPE.BLOCK0.getValue());
            if (hdrBlk != null) {
                hdrBlk.eventCallback(new EventCallback(ees, LOG));
            }
        }
    }

    public byte[] process(byte[] request, byte[] socketId) {
        if (request == null || (request.length < this.getApiHeaderLen())) {
            return ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_fail_header_len_VALUE);
        }

        byte[] msgHash = ApiUtil.getApiMsgHash(request);
        if (request[0] < this.getApiVersion()) {
            return msgHash == null
                ? ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_fail_api_version_VALUE)
                : ApiUtil.toReturnHeader(
                    getApiVersion(), Retcode.r_fail_api_version_VALUE, msgHash);
        }

        short service = (short) request[1];

        switch ((short) request[2]) {
            // General Module
            case Message.Funcs.f_protocolVersion_VALUE: {
                if (service != Message.Servs.s_net_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                // TODO : create query API for every module
                Message.rsp_protocolVersion rsp =
                    Message.rsp_protocolVersion
                        .newBuilder()
                        .setApi(String.valueOf(this.getApiVersion()))
                        .setDb(AionHub.getRepoVersion())
                        .setKernel(Version.KERNEL_VERSION)
                        .setMiner(EquihashMiner.VERSION)
                        .setNet(this.p2pProtocolVersion())
                        .setTxpool(this.ac.getAionHub().getPendingState().getVersion())
                        .setVm("0.1.0")
                        .build();

                byte[] retHeader =
                    ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
            }
            case Message.Funcs.f_minerAddress_VALUE: {
                if (service != Message.Servs.s_wallet_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                String cb = this.getCoinbase();
                if (cb == null) {
                    LOG.debug("ApiAion0.process.coinbase - null coinbase");
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_wallet_nullcb_VALUE);
                }

                Message.rsp_minerAddress rsp =
                    Message.rsp_minerAddress
                        .newBuilder()
                        .setMinerAddr(
                            ByteString.copyFrom(
                                TypeConverter.StringHexToByteArray(cb)))
                        .build();

                byte[] retHeader =
                    ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
            }

            case Message.Funcs.f_contractDeploy_VALUE: {
                if (service != Message.Servs.s_tx_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE, msgHash);
                }

                Message.req_contractDeploy req;
                byte[] data = parseMsgReq(request, msgHash);
                ContractCreateResult result;
                try {
                    req = Message.req_contractDeploy.parseFrom(data);
                    // TODO: the client api should send server binary code directly
                    // instead of str format like "0xhex".!
                    byte[] bytes = req.getData().toByteArray();
                    if (bytes == null || bytes.length <= 4) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_ct_bytecode_VALUE, msgHash);
                    }

                    ArgTxCall params =
                        new ArgTxCall(
                            Address.wrap(req.getFrom().toByteArray()),
                            null,
                            Hex.decode(new String(bytes).substring(2)),
                            BigInteger.ZERO,
                            BigInteger.ZERO,
                            req.getNrgLimit(),
                            req.getNrgPrice());

                    LOG.debug(
                        "ApiAion0.process.ContractDeploy - ArgsTxCall: [{}] ",
                        params.toString());

                    result = this.createContract(params);

                    if (result != null) {
                        getMsgIdMapping()
                            .put(
                                ByteArrayWrapper.wrap(result.transId),
                                new AbstractMap.SimpleEntry<>(
                                    ByteArrayWrapper.wrap(msgHash),
                                    ByteArrayWrapper.wrap(socketId)));
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(
                                "ApiAion0.process.ContractDeploy - msgIdMapping.put: [{}] ",
                                ByteArrayWrapper.wrap(result.transId).toString());
                        }
                    }
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.ContractDeploy exception [{}] ", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE, msgHash);
                }

                Message.rsp_contractDeploy rsp =
                    Message.rsp_contractDeploy
                        .newBuilder()
                        .setContractAddress(
                            ByteString.copyFrom(
                                result != null
                                    ? result.address.toBytes()
                                    : EMPTY_BYTE_ARRAY))
                        .setTxHash(
                            ByteString.copyFrom(
                                result != null
                                    ? result.transId
                                    : EMPTY_BYTE_ARRAY))
                        .build();

                byte[] retHeader =
                    ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_tx_Recved_VALUE, msgHash);
                return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
            }

            // Authenication Module
            case Message.Funcs.f_accounts_VALUE: {
                if (service != Message.Servs.s_wallet_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                // noinspection unchecked
                List<String> accounts = this.getAccounts();
                ArrayList<ByteString> al = new ArrayList<>();
                for (String s : accounts) {
                    al.add(ByteString.copyFrom(TypeConverter.StringHexToByteArray(s)));
                }
                Message.rsp_accounts rsp =
                    Message.rsp_accounts.newBuilder().addAllAccout(al).build();

                byte[] retHeader =
                    ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
            }
            case Message.Funcs.f_blockNumber_VALUE: {
                if (service != Message.Servs.s_chain_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                Message.rsp_blockNumber rsp =
                    Message.rsp_blockNumber
                        .newBuilder()
                        .setBlocknumber(this.getBestBlock().getNumber())
                        .build();
                byte[] retHeader =
                    ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
            }
            case Message.Funcs.f_unlockAccount_VALUE: {
                if (service != Message.Servs.s_wallet_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                boolean result;
                try {
                    Message.req_unlockAccount req = Message.req_unlockAccount.parseFrom(data);
                    result =
                        this.unlockAccount(
                            Address.wrap(req.getAccount().toByteArray()),
                            req.getPassword(),
                            req.getDuration());
                } catch (InvalidProtocolBufferException e) {
                    LOG.error("ApiAion0.process.unlockAccount exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }

                byte[] retHeader =
                    ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                return ApiUtil.combineRetMsg(retHeader, (byte) (result ? 0x01 : 0x00));
            }

            // Transaction Module
            case Message.Funcs.f_getBalance_VALUE: {
                if (service != Message.Servs.s_chain_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                BigInteger balance;
                try {
                    Message.req_getBalance req = Message.req_getBalance.parseFrom(data);

                    Address addr = Address.wrap(req.getAddress().toByteArray());

                    balance = this.getBalance(addr);
                } catch (InvalidProtocolBufferException e) {
                    LOG.error("ApiAion0.process.getbalance exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }

                Message.rsp_getBalance rsp =
                    Message.rsp_getBalance
                        .newBuilder()
                        .setBalance(ByteString.copyFrom(balance.toByteArray()))
                        .build();

                byte[] retHeader =
                    ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
            }
            case Message.Funcs.f_getNonce_VALUE: {
                if (service != Message.Servs.s_chain_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Message.Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                BigInteger nonce;
                try {
                    Message.req_getNonce req = Message.req_getNonce.parseFrom(data);

                    Address addr = Address.wrap(req.getAddress().toByteArray());

                    nonce = this.getNonce(addr);
                } catch (InvalidProtocolBufferException e) {
                    LOG.error("ApiAionA0.process.getNonce exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Message.Retcode.r_fail_function_exception_VALUE);
                }

                Message.rsp_getNonce rsp =
                    Message.rsp_getNonce
                        .newBuilder()
                        .setNonce(ByteString.copyFrom(nonce.toByteArray()))
                        .build();

                byte[] retHeader =
                    ApiUtil.toReturnHeader(
                        getApiVersion(), Message.Retcode.r_success_VALUE);
                return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
            }
            case Message.Funcs.f_getNrgPrice_VALUE: {
                if (service != Servs.s_tx_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                long nrg = this.getRecommendedNrgPrice();

                try {
                    Message.rsp_getNrgPrice rsp =
                        Message.rsp_getNrgPrice.newBuilder().setNrgPrice(nrg).build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                } catch (Exception e) {
                    LOG.error("ApiAion0.process.getNrgPrice exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_compile_VALUE: {
                if (service != Message.Servs.s_tx_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                try {
                    Message.req_compileSolidity req =
                        Message.req_compileSolidity.parseFrom(data);
                    String source = req.getSource();
                    if (source == null) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_null_compile_source_VALUE);
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, CompiledContr> _contrs = this.contract_compileSolidity(source);
                    if (_contrs != null && !_contrs.isEmpty()) {
                        Message.rsp_compile.Builder b = Message.rsp_compile.newBuilder();

                        for (Entry<String, CompiledContr> entry : _contrs.entrySet()) {
                            if (entry.getKey().contains("AionCompileError")) {
                                byte[] retHeader =
                                    ApiUtil.toReturnHeader(
                                        getApiVersion(),
                                        Retcode.r_fail_compile_contract_VALUE);
                                Message.t_Contract tc =
                                    Message.t_Contract
                                        .newBuilder()
                                        .setError(entry.getValue().error)
                                        .build();
                                return ApiUtil.combineRetMsg(
                                    retHeader,
                                    b.putConstracts(entry.getKey(), tc)
                                        .build()
                                        .toByteArray());
                            }

                            CompiledContr _contr = entry.getValue();
                            JSONArray abi = new JSONArray();
                            for (Abi.Entry f : _contr.info.abiDefinition) {
                                abi.put(f.toJSON());
                            }

                            Message.t_Contract tc =
                                Message.t_Contract
                                    .newBuilder()
                                    .setCode(_contr.code)
                                    .setAbiDef(
                                        ByteString.copyFrom(
                                            abi.toString().getBytes()))
                                    .setSource(_contr.info.source)
                                    .build();

                            b.putConstracts(entry.getKey(), tc);
                        }
                        Message.rsp_compile rsp = b.build();
                        byte[] retHeader =
                            ApiUtil.toReturnHeader(
                                getApiVersion(), Retcode.r_success_VALUE);
                        return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                    } else {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                    }

                } catch (InvalidProtocolBufferException e) {
                    LOG.error("ApiAion0.process.compile exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_sendTransaction_VALUE: {
                if (service != Message.Servs.s_tx_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE, msgHash);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_sendTransaction req;
                byte[] result;
                try {
                    req = Message.req_sendTransaction.parseFrom(data);

                    ArgTxCall params =
                        new ArgTxCall(
                            Address.wrap(req.getFrom().toByteArray()),
                            Address.wrap(req.getTo().toByteArray()),
                            req.getData().toByteArray(),
                            new BigInteger(req.getNonce().toByteArray()),
                            new BigInteger(req.getValue().toByteArray()),
                            req.getNrg(),
                            req.getNrgPrice());

                    result = this.sendTransaction(params);
                } catch (InvalidProtocolBufferException e) {
                    LOG.error(
                        "ApiAion0.process.sendTransaction exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE, msgHash);
                }

                if (result == null) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_sendTx_null_rep_VALUE, msgHash);
                }

                getMsgIdMapping()
                    .put(
                        ByteArrayWrapper.wrap(result),
                        new AbstractMap.SimpleEntry<>(
                            ByteArrayWrapper.wrap(msgHash),
                            ByteArrayWrapper.wrap(socketId)));
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                        "ApiAion0.process.sendTransaction - msgIdMapping.put: [{}]",
                        ByteArrayWrapper.wrap(result).toString());
                }

                Message.rsp_sendTransaction rsp =
                    Message.rsp_sendTransaction
                        .newBuilder()
                        .setTxHash(ByteString.copyFrom(result))
                        .build();

                byte[] retHeader =
                    ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_tx_Recved_VALUE, msgHash);
                return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
            }
            case Message.Funcs.f_getCode_VALUE: {
                if (service != Message.Servs.s_tx_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getCode req;
                try {
                    req = Message.req_getCode.parseFrom(data);
                    Address to = Address.wrap(req.getAddress().toByteArray());

                    byte[] code = this.getCode(to);
                    if (code == null) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_null_rsp_VALUE);
                    }

                    Message.rsp_getCode rsp =
                        Message.rsp_getCode
                            .newBuilder()
                            .setCode(ByteString.copyFrom(code))
                            .build();
                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());

                } catch (Exception e) {
                    LOG.error("ApiAion0.process.getCode exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getTransactionReceipt_VALUE: {
                if (service != Message.Servs.s_tx_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getTransactionReceipt req;
                try {
                    req = Message.req_getTransactionReceipt.parseFrom(data);

                    TxRecpt result = this.getTransactionReceipt(req.getTxHash().toByteArray());
                    if (result == null) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_getTxReceipt_null_recp_VALUE);
                    }

                    List<Message.t_LgEle> logs = new ArrayList<>();
                    for (TxRecptLg log : result.logs) {
                        List<String> al = new ArrayList<>();
                        Collections.addAll(al, log.topics);

                        Message.t_LgEle msgLog =
                            Message.t_LgEle
                                .newBuilder()
                                .setAddress(
                                    ByteString.copyFrom(
                                        Address.wrap(log.address).toBytes()))
                                .setData(
                                    ByteString.copyFrom(
                                        ByteUtil.hexStringToBytes(log.data)))
                                .addAllTopics(al)
                                .build();

                        logs.add(msgLog);
                    }

                    Message.rsp_getTransactionReceipt rsp =
                        Message.rsp_getTransactionReceipt
                            .newBuilder()
                            .setFrom(ByteString.copyFrom(result.fromAddr.toBytes()))
                            .setBlockNumber(result.blockNumber)
                            .setBlockHash(
                                ByteString.copyFrom(
                                    result.blockHash != null
                                        ? ByteUtil.hexStringToBytes(
                                        result.blockHash)
                                        : EMPTY_BYTE_ARRAY))
                            .setContractAddress(
                                ByteString.copyFrom(
                                    result.contractAddress != null
                                        ? ByteUtil.hexStringToBytes(
                                        result.contractAddress)
                                        : EMPTY_BYTE_ARRAY))
                            .setTxIndex(result.transactionIndex)
                            .setTxHash(
                                ByteString.copyFrom(
                                    result.transactionHash != null
                                        ? ByteUtil.hexStringToBytes(
                                        result.transactionHash)
                                        : EMPTY_BYTE_ARRAY))
                            .setTo(
                                ByteString.copyFrom(
                                    result.toAddr == null
                                        ? EMPTY_BYTE_ARRAY
                                        : result.toAddr.toBytes()))
                            .setNrgConsumed(result.nrgUsed)
                            .setCumulativeNrgUsed(result.cumulativeNrgUsed)
                            .addAllLogs(logs)
                            .build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());

                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getTransactionReceipt exception: [{}]",
                        e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_call_VALUE: {
                if (service != Message.Servs.s_tx_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_call req;

                try {
                    req = Message.req_call.parseFrom(data);

                    Address from = Address.wrap(req.getFrom().toByteArray());
                    Address to = Address.wrap(req.getTo().toByteArray());

                    BigInteger value = new BigInteger(req.getValue().toByteArray());
                    byte[] d = req.getData().toByteArray();

                    ArgTxCall params =
                        new ArgTxCall(
                            from,
                            to,
                            d,
                            BigInteger.ZERO,
                            value,
                            req.getNrg(),
                            req.getNrgPrice());
                    Message.rsp_call rsp =
                        Message.rsp_call
                            .newBuilder()
                            .setResult(ByteString.copyFrom(this.doCall(params)))
                            .build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());

                } catch (Exception e) {
                    LOG.error("ApiAion0.process.call exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getBlockByNumber_VALUE: {
                if (service != Message.Servs.s_chain_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getBlockByNumber req;

                try {
                    req = Message.req_getBlockByNumber.parseFrom(data);
                    long num = req.getBlockNumber();
                    AionBlock blk = this.getBlock(num);

                    return createBlockMsg(blk);
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getBlockByNumber exception: [{}]",
                        e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getBlockByHash_VALUE: {
                if (service != Message.Servs.s_chain_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getBlockByHash req;

                try {
                    req = Message.req_getBlockByHash.parseFrom(data);
                    byte[] hash = req.getBlockHash().toByteArray();

                    if (hash == null || hash.length != Hash256.BYTES) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    }

                    AionBlock blk = this.getBlockByHash(hash);
                    return createBlockMsg(blk);
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getBlockByHash exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getTransactionByBlockHashAndIndex_VALUE: {
                if (service != Message.Servs.s_chain_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getTransactionByBlockHashAndIndex req;

                try {
                    req = Message.req_getTransactionByBlockHashAndIndex.parseFrom(data);
                    long txIdx = req.getTxIndex();
                    byte[] hash = req.getBlockHash().toByteArray();

                    if (txIdx < -1 || hash == null || hash.length != Hash256.BYTES) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    }

                    AionTransaction tx = this.getTransactionByBlockHashAndIndex(hash, txIdx);
                    if (tx == null) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_call_VALUE);
                    }

                    Message.rsp_getTransaction rsp = getRsp_getTransaction(tx);

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getTransactionByBlockHashAndIndex exception: [{}]",
                        e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getTransactionByBlockNumberAndIndex_VALUE: {
                if (service != Message.Servs.s_chain_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getTransactionByBlockNumberAndIndex req;

                try {
                    req = Message.req_getTransactionByBlockNumberAndIndex.parseFrom(data);
                    long blkNr = req.getBlockNumber();
                    long txIdx = req.getTxIndex();

                    if (blkNr < -1 || txIdx < -1) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    }

                    AionTransaction tx = this.getTransactionByBlockNumberAndIndex(blkNr, txIdx);
                    if (tx == null) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_call_VALUE);
                    }

                    Message.rsp_getTransaction rsp = getRsp_getTransaction(tx);

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getTransactionByBlockNumberAndIndex exception: [{}]",
                        e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getBlockTransactionCountByNumber_VALUE: {
                if (service != Message.Servs.s_chain_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getBlockTransactionCountByNumber req;

                try {
                    req = Message.req_getBlockTransactionCountByNumber.parseFrom(data);
                    long blkNr = req.getBlockNumber();

                    if (blkNr < -1) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    }

                    long cnt = this.getBlockTransactionCountByNumber(blkNr);
                    if (cnt == -1) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_call_VALUE);
                    }

                    Message.rsp_getBlockTransactionCount rsp =
                        Message.rsp_getBlockTransactionCount
                            .newBuilder()
                            .setTxCount((int) cnt)
                            .build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getBlockTransactionCountByNumber exception: [{}]",
                        e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getTransactionCount_VALUE: {
                if (service != Message.Servs.s_chain_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getTransactionCount req;

                try {
                    req = Message.req_getTransactionCount.parseFrom(data);
                    long blkNr = req.getBlocknumber();
                    Address addr = Address.wrap(req.getAddress().toByteArray());

                    if (blkNr < -1) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    }

                    long cnt = this.getTransactionCount(addr, blkNr);
                    if (cnt == -1) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_call_VALUE);
                    }

                    Message.rsp_getTransactionCount rsp =
                        Message.rsp_getTransactionCount
                            .newBuilder()
                            .setTxCount((int) cnt)
                            .build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());

                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getTransactionCount exception: [{}]",
                        e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getBlockTransactionCountByHash_VALUE: {
                if (service != Message.Servs.s_chain_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getTransactionCountByHash req;

                try {
                    req = Message.req_getTransactionCountByHash.parseFrom(data);
                    byte[] hash = req.getTxHash().toByteArray();

                    if (hash == null || hash.length != Hash256.BYTES) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    }

                    long cnt = this.getTransactionCountByHash(hash);
                    if (cnt == -1) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_call_VALUE);
                    }

                    Message.rsp_getTransactionCount rsp =
                        Message.rsp_getTransactionCount
                            .newBuilder()
                            .setTxCount((int) cnt)
                            .build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());

                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getTransactionCount exception: [{}]",
                        e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getTransactionByHash_VALUE: {
                if (service != Message.Servs.s_chain_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getTransactionByHash req;

                try {
                    req = Message.req_getTransactionByHash.parseFrom(data);
                    byte[] txHash = req.getTxHash().toByteArray();

                    if (txHash == null || txHash.length != this.getTxHashLen()) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    }

                    AionTransaction tx = this.getTransactionByHash(txHash);
                    if (tx == null) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_call_VALUE);
                    }

                    Message.rsp_getTransaction rsp = getRsp_getTransaction(tx);

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());

                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getTransactionCount exception: [{}]",
                        e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }

            case Message.Funcs.f_getActiveNodes_VALUE: {
                if (service != Message.Servs.s_net_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                List<INode> nodes = new ArrayList<>(
                    this.ac.getAionHub().getP2pMgr().getActiveNodes().values());
                List<Message.t_Node> pl = new ArrayList<>();
                try {
                    for (INode n : nodes) {
                        Message.t_Node node =
                            Message.t_Node
                                .newBuilder()
                                .setBlockNumber(n.getBestBlockNumber())
                                .setNodeId(ByteArrayWrapper.wrap(n.getId()).toString())
                                .setRemoteP2PIp(
                                    ByteArrayWrapper.wrap(n.getIp()).toString())
                                // TODO : api export latency, totalDiff and the
                                // blockHash
                                // .setLatency((float) n.get)
                                .build();

                        pl.add(node);
                    }

                    Message.rsp_getActiveNodes rsp =
                        Message.rsp_getActiveNodes.newBuilder().addAllNode(pl).build();
                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getActiveNodes exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }

            case Message.Funcs.f_getStaticNodes_VALUE: {
                if (service != Message.Servs.s_net_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }
                String[] al = this.getBootNodes();
                List<Message.t_Node> nl = new ArrayList<>();
                for (String s : al) {
                    // TODO : get more node info
                    Message.t_Node n = Message.t_Node.newBuilder().setRemoteP2PIp(s).build();
                    nl.add(n);
                }

                try {
                    Message.rsp_getStaticNodes rsp =
                        Message.rsp_getStaticNodes.newBuilder().addAllNode(nl).build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getStaticNodes exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getSolcVersion_VALUE: {
                if (service != Message.Servs.s_tx_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                String ver = this.solcVersion();

                try {
                    Message.rsp_getSolcVersion rsp =
                        Message.rsp_getSolcVersion.newBuilder().setVer(ver).build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getSolcVersion exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_isSyncing_VALUE: {
                if (service != Message.Servs.s_net_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                try {
                    Message.rsp_isSyncing rsp =
                        Message.rsp_isSyncing
                            .newBuilder()
                            .setSyncing(!this.getSync().done)
                            .build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                } catch (Exception e) {
                    LOG.error("ApiAion0.process.syncing exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_syncInfo_VALUE: {
                if (service != Message.Servs.s_net_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                SyncInfo sync = this.getSync();

                try {
                    Message.rsp_syncInfo rsp =
                        Message.rsp_syncInfo
                            .newBuilder()
                            .setChainBestBlock(sync.chainBestBlkNumber)
                            .setNetworkBestBlock(sync.networkBestBlkNumber)
                            .setSyncing(!sync.done)
                            .setMaxImportBlocks(24)
                            .build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                } catch (Exception e) {
                    LOG.error("ApiAion0.process.syncInfo exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_accountCreate_VALUE: {
                if (service != Message.Servs.s_account_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_accountCreate req;

                try {
                    req = Message.req_accountCreate.parseFrom(data);

                    List<ByteString> addressList = new ArrayList<>();
                    List<ByteString> pKeyList = new ArrayList<>();
                    for (int i = 0;
                        i < req.getPasswordList().size() && i < ACCOUNT_CREATE_LIMIT;
                        i++) {

                        String addr = Keystore.create(req.getPassword(i));
                        byte[] pKey;
                        if (req.getPrivateKey()) {
                            pKey = Keystore.getKey(addr, req.getPassword(i)).getPrivKeyBytes();
                            if (pKey == null) {
                                pKey = ByteArrayWrapper.NULL_BYTE;
                            }

                            pKeyList.add(ByteString.copyFrom(pKey));
                        }

                        addressList.add(ByteString.copyFrom(Address.wrap(addr).toBytes()));
                    }

                    Message.rsp_accountCreate rsp =
                        Message.rsp_accountCreate
                            .newBuilder()
                            .addAllAddress(addressList)
                            .addAllPrivateKey(pKeyList)
                            .build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());

                } catch (Exception e) {
                    LOG.error("ApiAion0.process.accountCreate exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }

            case Message.Funcs.f_accountLock_VALUE: {
                if (service != Message.Servs.s_wallet_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);

                if (data == null) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                }

                boolean result;
                try {
                    Message.req_accountlock req = Message.req_accountlock.parseFrom(data);
                    result =
                        this.lockAccount(
                            Address.wrap(req.getAccount().toByteArray()),
                            req.getPassword());
                } catch (InvalidProtocolBufferException e) {
                    LOG.error("ApiAion0.process.lockAccount exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }

                byte[] retHeader =
                    ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                return ApiUtil.combineRetMsg(retHeader, (byte) (result ? 0x01 : 0x00));
            }

            case Message.Funcs.f_userPrivilege_VALUE: {
                if (service != Message.Servs.s_privilege_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                return ApiUtil.toReturnHeader(
                    getApiVersion(), Retcode.r_fail_unsupport_api_VALUE);
            }
            case Message.Funcs.f_mining_VALUE: {
                if (service != Message.Servs.s_mine_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                Message.rsp_mining rsp =
                    Message.rsp_mining.newBuilder().setMining(this.isMining()).build();

                byte[] retHeader =
                    ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
            }

            case Message.Funcs.f_estimateNrg_VALUE: {
                if (service != Message.Servs.s_tx_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE, msgHash);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_estimateNrg req;
                long result;
                try {
                    req = Message.req_estimateNrg.parseFrom(data);

                    ArgTxCall params =
                        new ArgTxCall(
                            Address.wrap(req.getFrom().toByteArray()),
                            Address.wrap(req.getTo().toByteArray()),
                            req.getData().toByteArray(),
                            BigInteger.ZERO,
                            new BigInteger(req.getValue().toByteArray()),
                            req.getNrg(),
                            req.getNrgPrice());

                    result = this.estimateNrg(params);
                } catch (InvalidProtocolBufferException e) {
                    LOG.error("ApiAion0.process.estimateNrg exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE, msgHash);
                }

                Message.rsp_estimateNrg rsp =
                    Message.rsp_estimateNrg.newBuilder().setNrg(result).build();

                byte[] retHeader =
                    ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
            }

            case Message.Funcs.f_exportAccounts_VALUE:
            case Message.Funcs.f_backupAccounts_VALUE: {
                if (service != Message.Servs.s_account_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_exportAccounts req;

                try {
                    req = Message.req_exportAccounts.parseFrom(data);
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.exportAccounts exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }

                Map<Address, String> addrMap = new HashMap<>();
                for (int i = 0;
                    i < req.getKeyFileList().size() && i < ACCOUNT_CREATE_LIMIT;
                    i++) {
                    addrMap.put(
                        Address.wrap(req.getKeyFile(i).getAddress().toByteArray()),
                        req.getKeyFile(i).getPassword());
                }

                Map<Address, ByteArrayWrapper> res =
                    ((short) request[2] == Message.Funcs.f_exportAccounts_VALUE)
                        ? Keystore.exportAccount(addrMap)
                        : Keystore.backupAccount(addrMap);

                List<ByteString> invalidKey =
                    addrMap.keySet()
                        .parallelStream()
                        .filter(
                            addr ->
                                res.keySet()
                                    .parallelStream()
                                    .noneMatch(ad -> ad.equals(addr)))
                        .map(match -> ByteString.copyFrom(match.toBytes()))
                        .collect(Collectors.toList());

                List<ByteString> keyBins =
                    res.values()
                        .parallelStream()
                        .map(key -> ByteString.copyFrom(key.toBytes()))
                        .collect(Collectors.toList());

                Message.rsp_exportAccounts rsp =
                    Message.rsp_exportAccounts
                        .newBuilder()
                        .addAllKeyFile(keyBins)
                        .addAllFailedKey(invalidKey)
                        .build();

                byte[] retHeader =
                    ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
            }
            case Message.Funcs.f_importAccounts_VALUE: {
                if (service != Message.Servs.s_account_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_importAccounts req;

                try {
                    req = Message.req_importAccounts.parseFrom(data);
                } catch (Exception e) {
                    LOG.error("ApiAion0.process.importAccount exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }

                Map<String, String> importKey =
                    req.getPrivateKeyList()
                        .parallelStream()
                        .collect(
                            Collectors.toMap(
                                Message.t_PrivateKey::getPrivateKey,
                                Message.t_PrivateKey::getPassword));

                if (importKey == null) {
                    LOG.error("ApiAion0.process.importAccount exception: [null importKey]");
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }

                Set<String> res = Keystore.importAccount(importKey);
                if (res == null) {
                    throw new NullPointerException();
                }

                Message.rsp_importAccounts rsp =
                    Message.rsp_importAccounts.newBuilder().addAllInvalidKey(res).build();

                byte[] retHeader =
                    ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
            }
            case Message.Funcs.f_signedTransaction_VALUE:
            case Message.Funcs.f_rawTransaction_VALUE: {
                if (service != Message.Servs.s_tx_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE, msgHash);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_rawTransaction req;
                byte[] result;
                try {
                    req = Message.req_rawTransaction.parseFrom(data);
                    byte[] encodedTx = req.getEncodedTx().toByteArray();
                    if (encodedTx == null) {
                        LOG.error(
                            "ApiAion0.process.rawTransaction exception: [null encodedTx]");
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    }

                    result = this.sendTransaction(encodedTx);
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.rawTransaction exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE, msgHash);
                }

                if (result == null) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_sendTx_null_rep_VALUE, msgHash);
                }

                getMsgIdMapping()
                    .put(
                        ByteArrayWrapper.wrap(result),
                        new AbstractMap.SimpleEntry<>(
                            ByteArrayWrapper.wrap(msgHash),
                            ByteArrayWrapper.wrap(socketId)));
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                        "ApiAion0.process.rawTransaction - msgIdMapping.put: [{}]",
                        ByteArrayWrapper.wrap(result).toString());
                }

                Message.rsp_sendTransaction rsp =
                    Message.rsp_sendTransaction
                        .newBuilder()
                        .setTxHash(ByteString.copyFrom(result))
                        .build();

                byte[] retHeader =
                    ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_tx_Recved_VALUE, msgHash);
                return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
            }
            case Message.Funcs.f_eventRegister_VALUE: {
                if (service != Message.Servs.s_tx_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_eventRegister req;

                try {
                    req = Message.req_eventRegister.parseFrom(data);

                    List<String> evtList = new ArrayList<>(req.getEventsList());
                    if (evtList.isEmpty()) {
                        LOG.error(
                            "ApiNucoNcp.process.eventRegister : [{}]", "empty event list");
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    }

                    Message.t_FilterCt fltr = req.getFilter();
                    List<byte[]> accList = new ArrayList<>();
                    fltr.getAddressesList().forEach(a -> accList.add(a.toByteArray()));

                    long lv = ByteUtil.byteArrayToLong(socketId);
                    FltrCt preFc = (FltrCt) installedFilters.get(lv);
                    if (Optional.ofNullable(preFc).isPresent()) {
                        preFc.getTopics()
                            .forEach(
                                t -> {
                                    if (!fltr.getTopicsList().contains(t)) {
                                        evtList.add(t);
                                    }
                                });
                    }

                    FltrCt fc =
                        new FltrCt(
                            fltr.getContractAddr().toByteArray(),
                            fltr.getTo(),
                            fltr.getFrom(),
                            evtList,
                            accList,
                            fltr.getExpireTime());

                    installedFilters.put(lv, fc);

                    Message.rsp_eventRegister rsp =
                        Message.rsp_eventRegister.newBuilder().setResult(true).build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());

                } catch (Exception e) {
                    LOG.error("ApiAion0.process.eventRegister exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_eventDeregister_VALUE: {
                if (service != Message.Servs.s_tx_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_eventDeregister req;

                try {
                    req = Message.req_eventDeregister.parseFrom(data);

                    List<String> evtList = new ArrayList<>(req.getEventsList());

                    byte[] conrtactAddr;
                    if (req.getContractAddr() == null) {
                        conrtactAddr = null;
                    } else {
                        conrtactAddr = req.getContractAddr().toByteArray();
                    }

                    if (evtList.isEmpty()) {
                        LOG.error("ApiAion0.process.eventRegister : [{}]", "empty event list");
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    }

                    long lv = ByteUtil.byteArrayToLong(socketId);
                    FltrCt preFc = (FltrCt) installedFilters.get(lv);

                    boolean changed = false;
                    if (Optional.ofNullable(preFc).isPresent()
                        && Arrays.equals(preFc.getContractAddr(), conrtactAddr)) {
                        evtList.forEach(
                            ev -> preFc.getTopics().remove(ev));

                        installedFilters.put(lv, preFc);
                        changed = true;
                    }

                    Message.rsp_eventRegister rsp =
                        Message.rsp_eventRegister.newBuilder().setResult(changed).build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());

                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.eventDeregister exception: [{}]", e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getBlockDetailsByNumber_VALUE: {
                if (service != Message.Servs.s_admin_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getBlockDetailsByNumber req;

                try {
                    req = Message.req_getBlockDetailsByNumber.parseFrom(data);
                    long latestBlkNum = this.getBestBlock().getNumber();

                    List<Long> blkNum =
                        req.getBlkNumbersList()
                            .parallelStream()
                            .filter(n -> n <= latestBlkNum)
                            .collect(Collectors.toSet())
                            .parallelStream()
                            .sorted()
                            .collect(Collectors.toList());

                    if (blkNum.size() > 1000) {
                        blkNum = blkNum.subList(0, 1000);
                    }

                    List<Map.Entry<AionBlock, BigInteger>> blks =
                        getBlkAndDifficultyForBlkNumList(blkNum);

                    if (blks == null) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    } else {

                        List<Message.t_BlockDetail> bds = getRsp_getBlockDetails(blks);
                        Message.rsp_getBlockDetailsByNumber rsp =
                            Message.rsp_getBlockDetailsByNumber
                                .newBuilder()
                                .addAllBlkDetails(bds)
                                .build();

                        byte[] retHeader =
                            ApiUtil.toReturnHeader(
                                getApiVersion(), Retcode.r_success_VALUE);
                        return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                    }
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getBlockDetailsByNumber exception: [{}]",
                        e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getBlockSqlByRange_VALUE: {
                if (service != Message.Servs.s_admin_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getBlockSqlByRange req;

                try {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("BlockSqlByRange: start");
                    }

                    req = Message.req_getBlockSqlByRange.parseFrom(data);
                    long latestBlkNum = this.getBestBlock().getNumber();

                    Long _blkStart = req.getBlkNumberStart();
                    Long _blkEnd = req.getBlkNumberEnd();

                    // no null check here

                    long blkStart;
                    long blkEnd;

                    if (_blkStart < 0) {
                        blkStart = 0;
                    } else {
                        blkStart = _blkStart;
                    }

                    if (_blkEnd > latestBlkNum) {
                        blkEnd = latestBlkNum;
                    } else {
                        blkEnd = _blkEnd;
                    }

                    if (blkEnd < blkStart) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    }

                    // truncate the thing
                    if (blkEnd - blkStart > 1000) {
                        blkStart = blkEnd - 1000 + 1;

                        if (blkStart < 0) {
                            blkStart = 0;
                        }
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("BlockSqlByRange: range " + blkStart + "-" + blkEnd);
                    }

                    Long lastBlockTimestamp = null;
                    long listLength = blkEnd - blkStart + 1;
                    List<Message.t_BlockSql> bds = new ArrayList<>();

                    for (int i = 0; i < listLength; i++) {
                        long blkNum = blkStart + i;
                        Map.Entry<AionBlock, BigInteger> entry =
                            getBlockWithTotalDifficulty(blkNum);
                        AionBlock b = entry.getKey();
                        BigInteger td = entry.getValue();
                        long blocktime;
                        if (blkNum != 0 && lastBlockTimestamp == null) {
                            lastBlockTimestamp = getBlock(blkNum - 1).getTimestamp();
                        }
                        if (blkNum == 0) {
                            blocktime = 0;
                        } else {
                            blocktime = b.getTimestamp() - lastBlockTimestamp;
                        }

                        lastBlockTimestamp = b.getTimestamp();

                        String blockSql = generateBlockSqlStatement(b, td, blocktime);

                        List<String> transactionSql = new ArrayList<>();

                        AionBlockSummary bs = null;
                        if (explorerBlockCache != null) {
                            // remove from cache since after consumed, we're probably not gonna
                            // revisit it
                            bs = explorerBlockCache.remove(new ByteArrayWrapper(b.getHash()));
                        }

                        if (bs != null) {

                            if (LOG.isDebugEnabled()) {
                                LOG.debug("BlockSqlByRange: cache HIT for #: " + b.getNumber());
                            }

                            Map<ByteArrayWrapper, AionTxReceipt> receipts = new HashMap<>();
                            for (AionTxReceipt r : bs.getReceipts()) {
                                receipts.put(
                                    new ByteArrayWrapper(r.getTransaction().getHash()), r);
                            }

                            List<AionTransaction> txns = b.getTransactionsList();
                            for (int j = 0; j < txns.size(); j++) {
                                AionTransaction tx = txns.get(j);
                                AionTxReceipt r =
                                    receipts.get(new ByteArrayWrapper(tx.getHash()));
                                if (r == null) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug(
                                            "BlockSqlByRange: transaction not in Block Summary: "
                                                + b.getNumber()
                                                + "."
                                                + j);
                                    }
                                    AionTxInfo ti =
                                        ((AionBlockchainImpl)
                                            this.ac
                                                .getAionHub()
                                                .getBlockchain())
                                            .getTransactionInfoLite(
                                                tx.getHash(), b.getHash());
                                    r = ti.getReceipt();
                                }
                                if (r == null) {
                                    LOG.error(
                                        "BlockSqlByRange: missing DB transaction: "
                                            + ByteUtil.toHexString(tx.getHash()));
                                } else {
                                    transactionSql.add(
                                        generateTransactionSqlStatement(
                                            b,
                                            tx,
                                            r.getLogInfoList(),
                                            j,
                                            r.getEnergyUsed()));
                                }
                            }
                        } else {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug(
                                    "BlockSqlByRange: cache MISS for #: " + b.getNumber());
                            }
                            List<AionTransaction> txs = b.getTransactionsList();

                            transactionSql =
                                txs.parallelStream()
                                    .filter(Objects::nonNull)
                                    .map(
                                        (AionTransaction tx) -> {
                                            AionTxInfo ti =
                                                ((AionBlockchainImpl)
                                                    this.ac
                                                        .getAionHub()
                                                        .getBlockchain())
                                                    .getTransactionInfoLite(
                                                        tx.getHash(),
                                                        b.getHash());
                                            if (ti == null) {
                                                LOG.error(
                                                    "BlockSqlByRange: missing DB transaction: "
                                                        + ByteUtil
                                                        .toHexString(
                                                            tx
                                                                .getHash()));
                                                return null;
                                            } else {
                                                return generateTransactionSqlStatement(
                                                    b,
                                                    tx,
                                                    ti.getReceipt()
                                                        .getLogInfoList(),
                                                    ti.getIndex(),
                                                    ti.getReceipt()
                                                        .getEnergyUsed());
                                            }
                                        })
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList());
                        }

                        Message.t_BlockSql sqlObj =
                            Message.t_BlockSql
                                .newBuilder()
                                .setBlockNumber(b.getNumber())
                                .setBlockHash(ByteUtil.toHexString(b.getHash()))
                                .setParentHash(ByteUtil.toHexString(b.getParentHash()))
                                .setBlock(blockSql)
                                .addAllTx(transactionSql)
                                .build();

                        bds.add(sqlObj);
                    }

                    Message.rsp_getBlockSqlByRange rsp =
                        Message.rsp_getBlockSqlByRange
                            .newBuilder()
                            .addAllBlkSql(bds)
                            .build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getBlockDetailsByNumber exception: [{}]",
                        e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getBlockDetailsByRange_VALUE: {
                if (service != Message.Servs.s_admin_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getBlockDetailsByRange req;

                try {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("getBlockDetailsByRange: start");
                    }

                    req = Message.req_getBlockDetailsByRange.parseFrom(data);
                    long latestBlkNum = this.getBestBlock().getNumber();

                    Long _blkStart = req.getBlkNumberStart();
                    Long _blkEnd = req.getBlkNumberEnd();

                    // no null check here

                    long blkStart;
                    long blkEnd;

                    if (_blkStart < 0) {
                        blkStart = 0;
                    } else {
                        blkStart = _blkStart;
                    }

                    // blocks requested in the future. return empty result
                    if (blkStart > latestBlkNum) {
                        Message.rsp_getBlockDetailsByRange rsp =
                            Message.rsp_getBlockDetailsByRange
                                .newBuilder()
                                .addAllBlkDetails(new ArrayList<>())
                                .build();

                        byte[] retHeader =
                            ApiUtil.toReturnHeader(
                                getApiVersion(), Retcode.r_success_VALUE);
                        return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                    }

                    if (_blkEnd > latestBlkNum) {
                        blkEnd = latestBlkNum;
                    } else {
                        blkEnd = _blkEnd;
                    }

                    if (blkEnd < blkStart) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    }

                    // truncate at the beginning of range
                    if (blkEnd - blkStart > 1000) {
                        blkStart = blkEnd - 1000 + 1;

                        if (blkStart < 0) {
                            blkStart = 0;
                        }
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("getBlockDetailsByRange: range " + blkStart + "-" + blkEnd);
                    }

                    Long lastBlockTimestamp = null;
                    long listLength = blkEnd - blkStart + 1;
                    List<Message.t_BlockDetail> bds = new ArrayList<>();

                    for (int i = 0; i < listLength; i++) {
                        long blkNum = blkStart + i;
                        Map.Entry<AionBlock, BigInteger> entry =
                            getBlockWithTotalDifficulty(blkNum);
                        AionBlock b = entry.getKey();
                        BigInteger td = entry.getValue();
                        long blocktime = 0;
                        if (b.getNumber() > 0 && lastBlockTimestamp == null) {
                            lastBlockTimestamp =
                                getBlockByHash(b.getParentHash()).getTimestamp();
                        }

                        if (lastBlockTimestamp != null) {
                            blocktime = b.getTimestamp() - lastBlockTimestamp;
                        }

                        lastBlockTimestamp = b.getTimestamp();

                        Message.t_BlockDetail.Builder blockDetails =
                            getBlockDetailsObj(b, td, blocktime);

                        List<Message.t_TxDetail> txDetails = new ArrayList<>();

                        AionBlockSummary bs = null;
                        if (explorerBlockCache != null) {
                            // remove from cache since after consumed, we're probably not gonna
                            // revisit it
                            bs = explorerBlockCache.remove(new ByteArrayWrapper(b.getHash()));
                        }

                        if (bs != null) {

                            if (LOG.isDebugEnabled()) {
                                LOG.debug(
                                    "getBlockDetailsByRange: cache HIT for #: "
                                        + b.getNumber());
                            }

                            Map<ByteArrayWrapper, AionTxReceipt> receipts = new HashMap<>();
                            for (AionTxReceipt r : bs.getReceipts()) {
                                receipts.put(
                                    new ByteArrayWrapper(r.getTransaction().getHash()), r);
                            }

                            List<AionTransaction> txns = b.getTransactionsList();
                            for (int j = 0; j < txns.size(); j++) {
                                AionTransaction tx = txns.get(j);
                                AionTxReceipt r =
                                    receipts.get(new ByteArrayWrapper(tx.getHash()));
                                if (r == null) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug(
                                            "getBlockDetailsByRange: transaction not in Block Summary: "
                                                + b.getNumber()
                                                + "."
                                                + j);
                                    }
                                    AionTxInfo ti =
                                        ((AionBlockchainImpl)
                                            this.ac
                                                .getAionHub()
                                                .getBlockchain())
                                            .getTransactionInfoLite(
                                                tx.getHash(), b.getHash());
                                    r = ti.getReceipt();
                                }
                                if (r == null) {
                                    LOG.error(
                                        "getBlockDetailsByRange: missing DB transaction: "
                                            + ByteUtil.toHexString(tx.getHash()));
                                } else {
                                    txDetails.add(
                                        getTxDetailsObj(
                                            tx,
                                            r.getLogInfoList(),
                                            j,
                                            r.getEnergyUsed(),
                                            r.getError()));
                                }
                            }
                        } else {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug(
                                    "getBlockDetailsByRange: cache MISS for #: "
                                        + b.getNumber());
                            }
                            List<AionTransaction> txs = b.getTransactionsList();

                            txDetails =
                                txs.parallelStream()
                                    .filter(Objects::nonNull)
                                    .map(
                                        (AionTransaction tx) -> {
                                            AionTxInfo ti =
                                                ((AionBlockchainImpl)
                                                    this.ac
                                                        .getAionHub()
                                                        .getBlockchain())
                                                    .getTransactionInfoLite(
                                                        tx.getHash(),
                                                        b.getHash());
                                            if (ti == null) {
                                                LOG.error(
                                                    "getBlockDetailsByRange: missing DB transaction: "
                                                        + ByteUtil
                                                        .toHexString(
                                                            tx
                                                                .getHash()));
                                                return null;
                                            } else {
                                                return getTxDetailsObj(
                                                    tx,
                                                    ti.getReceipt()
                                                        .getLogInfoList(),
                                                    ti.getIndex(),
                                                    ti.getReceipt()
                                                        .getEnergyUsed(),
                                                    ti.getReceipt().getError());
                                            }
                                        })
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList());
                        }

                        bds.add(blockDetails.addAllTx(txDetails).build());
                    }

                    Message.rsp_getBlockDetailsByRange rsp =
                        Message.rsp_getBlockDetailsByRange
                            .newBuilder()
                            .addAllBlkDetails(bds)
                            .build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getBlockDetailsByNumber exception: [{}]",
                        e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getBlockDetailsByLatest_VALUE: {
                if (service != Message.Servs.s_admin_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getBlockDetailsByLatest req;

                try {
                    req = Message.req_getBlockDetailsByLatest.parseFrom(data);

                    // clip the requested count up to 1000
                    Long count = req.getCount();
                    if (count > 1000) {
                        count = 1000L;
                    }

                    // clip start block to 0 at the bottom
                    Long endBlock = this.getBestBlock().getNumber();
                    Long startBlock = (endBlock - count + 1) >= 0 ? (endBlock - count + 1) : 0;

                    List<Long> blkNum =
                        LongStream.rangeClosed(startBlock, endBlock)
                            .boxed()
                            .collect(Collectors.toList());

                    List<Map.Entry<AionBlock, BigInteger>> blks =
                        getBlkAndDifficultyForBlkNumList(blkNum);

                    if (blks == null) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    } else {
                        List<Message.t_BlockDetail> bds = getRsp_getBlockDetails(blks);
                        Message.rsp_getBlockDetailsByLatest rsp =
                            Message.rsp_getBlockDetailsByLatest
                                .newBuilder()
                                .addAllBlkDetails(bds)
                                .build();

                        byte[] retHeader =
                            ApiUtil.toReturnHeader(
                                getApiVersion(), Retcode.r_success_VALUE);
                        return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                    }
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getBlockDetailsByLatest exception: [{}]",
                        e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getBlocksByLatest_VALUE: {
                if (service != Message.Servs.s_admin_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getBlocksByLatest req;

                try {
                    req = Message.req_getBlocksByLatest.parseFrom(data);

                    // clip the requested count up to 1000
                    Long count = req.getCount();
                    if (count > 1000) {
                        count = 1000L;
                    }

                    // clip start block to 0 at the bottom
                    Long endBlock = this.getBestBlock().getNumber();
                    Long startBlock = (endBlock - count + 1) >= 0 ? (endBlock - count + 1) : 0;

                    List<Long> blkNum =
                        LongStream.rangeClosed(startBlock, endBlock)
                            .boxed()
                            .collect(Collectors.toList());

                    List<Map.Entry<AionBlock, BigInteger>> blks =
                        getBlkAndDifficultyForBlkNumList(blkNum);

                    if (blks == null) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    } else {
                        List<Message.t_Block> bs = getRsp_getBlocks(blks);
                        Message.rsp_getBlocksByLatest rsp =
                            Message.rsp_getBlocksByLatest
                                .newBuilder()
                                .addAllBlks(bs)
                                .build();

                        byte[] retHeader =
                            ApiUtil.toReturnHeader(
                                getApiVersion(), Retcode.r_success_VALUE);
                        return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
                    }
                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getBlocksByLatest exception: [{}]",
                        e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }
            case Message.Funcs.f_getAccountDetailsByAddressList_VALUE: {
                if (service != Message.Servs.s_admin_VALUE) {
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_service_call_VALUE);
                }

                byte[] data = parseMsgReq(request, msgHash);
                Message.req_getAccountDetailsByAddressList req;

                try {
                    req = Message.req_getAccountDetailsByAddressList.parseFrom(data);
                    List<ByteString> num = req.getAddressesList();

                    if (num.size() > 1000) {
                        num = num.subList(0, 1000);
                    }

                    List<Message.t_AccountDetail> accounts =
                        num.parallelStream()
                            .map(
                                a -> {
                                    BigInteger b =
                                        this.getBalance(
                                            Address.wrap(a.toByteArray()));

                                    Message.t_AccountDetail.Builder builder =
                                        Message.t_AccountDetail.newBuilder();
                                    if (b != null) {
                                        builder.setBalance(
                                            ByteString.copyFrom(
                                                b.toByteArray()));
                                    }

                                    builder.setAddress(a);

                                    return builder.build();
                                })
                            .collect(Collectors.toList());

                    if (accounts == null) {
                        return ApiUtil.toReturnHeader(
                            getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
                    }

                    Message.rsp_getAccountDetailsByAddressList rsp =
                        Message.rsp_getAccountDetailsByAddressList
                            .newBuilder()
                            .addAllAccounts(accounts)
                            .build();

                    byte[] retHeader =
                        ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
                    return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());

                } catch (Exception e) {
                    LOG.error(
                        "ApiAion0.process.getBlockDetailsByNumber exception: [{}]",
                        e.getMessage());
                    return ApiUtil.toReturnHeader(
                        getApiVersion(), Retcode.r_fail_function_exception_VALUE);
                }
            }

            // case Message.Funcs.f_eventQuery_VALUE:
            // case Message.Funcs.f_submitWork_VALUE:
            // case Message.Funcs.f_getWork_VALUE:
            default:
                return ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_fail_function_call_VALUE);
        }
    }

    @Override
    public void shutDown() {
        if (isFilterEnabled) {
            shutDownES();
        }

        if (isBlkCacheEnabled) {
            eesBlkCache.shutdown();
        }
    }

    @Override
    public Map<ByteArrayWrapper, Entry<ByteArrayWrapper, ByteArrayWrapper>> getMsgIdMapping() {
        return this.msgIdMapping;
    }

    @Override
    public TxWaitingMappingUpdate takeTxWait() throws Throwable {
        return txWait.take();
    }

    private byte[] createBlockMsg(AionBlock blk) {
        if (blk == null) {
            return ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_fail_function_arguments_VALUE);
        } else {

            List<ByteString> al = new ArrayList<>();
            for (AionTransaction tx : blk.getTransactionsList()) {
                al.add(ByteString.copyFrom(tx.getHash()));
            }

            BigInteger td =
                this.ac.getBlockchain().getTotalDifficultyByHash(Hash256.wrap(blk.getHash()));
            Message.rsp_getBlock rsp = getRsp_getBlock(blk, al, td);

            byte[] retHeader = ApiUtil.toReturnHeader(getApiVersion(), Retcode.r_success_VALUE);
            return ApiUtil.combineRetMsg(retHeader, rsp.toByteArray());
        }
    }

    private Message.rsp_getTransaction getRsp_getTransaction(AionTransaction tx) {
        return Message.rsp_getTransaction
            .newBuilder()
            .setBlockhash(ByteString.copyFrom(tx.getBlockHash()))
            .setBlocknumber(tx.getBlockNumber())
            .setFrom(ByteString.copyFrom(tx.getFrom().toBytes()))
            .setNrgConsume(tx.getNrgConsume())
            .setNrgPrice(tx.getNrgPrice())
            .setTxHash(ByteString.copyFrom(tx.getHash()))
            .setData(
                ByteString.copyFrom(tx.getData() == null ? EMPTY_BYTE_ARRAY : tx.getData()))
            .setNonce(ByteString.copyFrom(tx.getNonce()))
            .setTo(
                ByteString.copyFrom(
                    tx.getTo() == null ? EMPTY_BYTE_ARRAY : tx.getTo().toBytes()))
            .setValue(ByteString.copyFrom(tx.getValue()))
            .setTxIndex((int) tx.getTxIndexInBlock())
            .setTimeStamp(ByteUtil.byteArrayToLong(tx.getTimeStamp()))
            .build();
    }

    private Message.rsp_getBlock getRsp_getBlock(
        AionBlock blk, List<ByteString> al, BigInteger td) {
        return Message.rsp_getBlock
            .newBuilder()
            .setParentHash(ByteString.copyFrom(blk.getParentHash()))
            .setMinerAddress(ByteString.copyFrom(blk.getCoinbase().toBytes()))
            .setStateRoot(ByteString.copyFrom(blk.getStateRoot()))
            .setTxTrieRoot(ByteString.copyFrom(blk.getTxTrieRoot()))
            .setDifficulty(ByteString.copyFrom(blk.getDifficulty()))
            .setExtraData(ByteString.copyFrom(blk.getExtraData()))
            .setNrgConsumed(blk.getNrgConsumed())
            .setNrgLimit(blk.getNrgLimit())
            .setHash(ByteString.copyFrom(blk.getHash()))
            .setLogsBloom(ByteString.copyFrom(blk.getLogBloom()))
            .setNonce(ByteString.copyFrom(blk.getNonce()))
            .setReceiptTrieRoot(ByteString.copyFrom(blk.getReceiptsRoot()))
            .setTimestamp(blk.getTimestamp())
            .setBlockNumber(blk.getNumber())
            .setSolution(ByteString.copyFrom(blk.getHeader().getSolution()))
            .addAllTxHash(al)
            .setSize(blk.size())
            .setTotalDifficulty(ByteString.copyFrom(td.toByteArray()))
            .build();
    }

    private List<Message.t_Block> getRsp_getBlocks(List<Map.Entry<AionBlock, BigInteger>> blks) {

        return blks.parallelStream()
            .filter(Objects::nonNull)
            .map(
                blk -> {
                    AionBlock b = blk.getKey();

                    return Message.t_Block
                        .newBuilder()
                        .setBlockNumber(b.getNumber())
                        .setDifficulty(ByteString.copyFrom(b.getDifficulty()))
                        .setExtraData(ByteString.copyFrom(b.getExtraData()))
                        .setHash(ByteString.copyFrom(b.getHash()))
                        .setLogsBloom(ByteString.copyFrom(b.getLogBloom()))
                        .setMinerAddress(
                            ByteString.copyFrom(b.getCoinbase().toBytes()))
                        .setNonce(ByteString.copyFrom(b.getNonce()))
                        .setNrgConsumed(b.getNrgConsumed())
                        .setNrgLimit(b.getNrgLimit())
                        .setParentHash(ByteString.copyFrom(b.getParentHash()))
                        .setTimestamp(b.getTimestamp())
                        .setTxTrieRoot(ByteString.copyFrom(b.getTxTrieRoot()))
                        .setReceiptTrieRoot(
                            ByteString.copyFrom(b.getReceiptsRoot()))
                        .setStateRoot(ByteString.copyFrom(b.getStateRoot()))
                        .setSize(b.getEncoded().length)
                        .setSolution(
                            ByteString.copyFrom(
                                b.getHeader().getSolution()))
                        .setTotalDifficulty(
                            ByteString.copyFrom(
                                blk.getValue().toByteArray()))
                        .build();
                })
            .collect(Collectors.toList());
    }

    private Message.t_BlockDetail.Builder getBlockDetailsObj(
        AionBlock b, BigInteger td, long blocktime) {

        return Message.t_BlockDetail
            .newBuilder()
            .setBlockNumber(b.getNumber())
            .setDifficulty(ByteString.copyFrom(b.getDifficulty()))
            .setExtraData(ByteString.copyFrom(b.getExtraData()))
            .setHash(ByteString.copyFrom(b.getHash()))
            .setLogsBloom(ByteString.copyFrom(b.getLogBloom()))
            .setMinerAddress(ByteString.copyFrom(b.getCoinbase().toBytes()))
            .setNonce(ByteString.copyFrom(b.getNonce()))
            .setNrgConsumed(b.getNrgConsumed())
            .setNrgLimit(b.getNrgLimit())
            .setParentHash(ByteString.copyFrom(b.getParentHash()))
            .setTimestamp(b.getTimestamp())
            .setTxTrieRoot(ByteString.copyFrom(b.getTxTrieRoot()))
            .setReceiptTrieRoot(ByteString.copyFrom(b.getReceiptsRoot()))
            .setStateRoot(ByteString.copyFrom(b.getStateRoot()))
            .setSize(b.getEncoded().length)
            .setSolution(ByteString.copyFrom(b.getHeader().getSolution()))
            .setTotalDifficulty(ByteString.copyFrom(td.toByteArray()))
            .setBlockTime(blocktime);
    }

    private Message.t_TxDetail getTxDetailsObj(
        AionTransaction t, List<Log> _logs, int txIndex, long nrgConsumed, String error) {

        List<Message.t_LgEle> tles =
            _logs.parallelStream()
                .map(
                    log -> {
                        List<String> topics = new ArrayList<>();
                        for (int i = 0; i < log.getTopics().size(); i++) {
                            topics.add(TypeConverter.toJsonHex(log.getTopics().get(i)));
                        }

                        return Message.t_LgEle
                            .newBuilder()
                            .setData(ByteString.copyFrom(log.getData()))
                            .setAddress(
                                ByteString.copyFrom(log.getAddress().toBytes()))
                            .addAllTopics(topics)
                            .build();
                    })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Address contract = t.getContractAddress();

        Message.t_TxDetail.Builder tdBuilder =
            Message.t_TxDetail
                .newBuilder()
                .setData(ByteString.copyFrom(t.getData()))
                .setTo(ByteString.copyFrom(t.getTo().toBytes()))
                .setFrom(ByteString.copyFrom(t.getFrom().toBytes()))
                .setNonce(ByteString.copyFrom(t.getNonce()))
                .setValue(ByteString.copyFrom(t.getValue()))
                .setNrgConsumed(nrgConsumed)
                .setNrgPrice(t.getNrgPrice())
                .setTxHash(ByteString.copyFrom(t.getHash()))
                .setTxIndex(txIndex)
                .setTimestamp(ByteUtil.byteArrayToLong(t.getTimeStamp()))
                .setError(error)
                .addAllLogs(tles);

        if (contract != null) {
            tdBuilder.setContract(ByteString.copyFrom(contract.toBytes()));
        }

        return tdBuilder.build();
    }

    private String generateBlockSqlStatement(AionBlock b, BigInteger td, long blocktime) {
        /*
        create table block_cache(
            block_number bigint(64) primary key,

            block_hash varchar(64),
            miner_address varchar(64),
            parent_hash varchar(64),

            receipt_tx_root varchar(64),
            state_root varchar(64),
            tx_trie_root varchar(64),

            extra_data varchar(64),
            nonce text,
            bloom text,
            solution text,

            difficulty text,
            total_difficulty text,

            nrg_consumed bigint(64),
            nrg_limit bigint(64),
            size bigint(64),

            block_timestamp bigint(64),
            num_transactions bigint(64),
            block_time bigint(64));
         */

        return b.getNumber()
            + ","
            + "'"
            + ByteUtil.toHexString(b.getHash())
            + "',"
            + "'"
            + ByteUtil.toHexString(b.getCoinbase().toBytes())
            + "',"
            + "'"
            + ByteUtil.toHexString(b.getParentHash())
            + "',"
            + "'"
            + ByteUtil.toHexString(b.getReceiptsRoot())
            + "',"
            + "'"
            + ByteUtil.toHexString(b.getStateRoot())
            + "',"
            + "'"
            + ByteUtil.toHexString(b.getTxTrieRoot())
            + "',"
            + "'"
            + ByteUtil.toHexString(b.getExtraData())
            + "',"
            + "'"
            + ByteUtil.toHexString(b.getNonce())
            + "',"
            + "'"
            + ByteUtil.toHexString(b.getLogBloom())
            + "',"
            + "'"
            + ByteUtil.toHexString(b.getHeader().getSolution())
            + "',"
            + "'"
            + ByteUtil.toHexString(b.getDifficulty())
            + "',"
            + "'"
            + ByteUtil.toHexString(td.toByteArray())
            + "',"
            + b.getNrgConsumed()
            + ","
            + b.getNrgLimit()
            + ","
            + b.getEncoded().length
            + ","
            + b.getTimestamp()
            + ","
            + b.getTransactionsList().size()
            + ","
            + blocktime;
    }

    private String generateTransactionSqlStatement(
        AionBlock b, AionTransaction t, List<Log> _logs, int txIndex, long nrgConsumed) {
        JSONArray logs = new JSONArray();
        for (Log l : _logs) {
            JSONArray log = new JSONArray();
            log.put(l.getAddress().toString()); // address
            log.put(ByteUtil.toHexString(l.getData())); // data
            JSONArray topics = new JSONArray();
            for (byte[] topic : l.getTopics()) {
                topics.put(ByteUtil.toHexString(topic));
            }
            log.put(topics); // topics
            logs.put(log);
        }

        /*
        create table transaction_cache(
            transaction_hash varchar(64),
            block_hash varchar(64),

            block_number bigint(64),
            transaction_index bigint(64),

            from_addr varchar(64),
            to_addr varchar(64),

            nrg_consumed bigint(64),
            nrg_price bigint(64),

            transaction_timestamp bigint(64),
            block_timestamp bigint(64),

            value text,
            transaction_log text,

            data text,
            nonce text,

            primary key(block_number,transaction_index));
         */

        return "'"
            + ByteUtil.toHexString(t.getHash())
            + "',"
            + "'"
            + ByteUtil.toHexString(b.getHash())
            + "',"
            + b.getNumber()
            + ","
            + txIndex
            + ","
            + "'"
            + ByteUtil.toHexString(t.getFrom().toBytes())
            + "',"
            + "'"
            + ByteUtil.toHexString(t.getTo().toBytes())
            + "',"
            + nrgConsumed
            + ","
            + t.getNrgPrice()
            + ","
            + ByteUtil.byteArrayToLong(t.getTimeStamp())
            + ","
            + b.getTimestamp()
            + ","
            + "'"
            + ByteUtil.toHexString(t.getValue())
            + "',"
            + "'"
            + logs.toString()
            + "',"
            + "'"
            + ByteUtil.toHexString(t.getData())
            + "',"
            + "'"
            + ByteUtil.toHexString(t.getNonce())
            + "'";
    }

    private List<Message.t_BlockDetail> getRsp_getBlockDetails(
        List<Map.Entry<AionBlock, BigInteger>> blks) {

        return blks.parallelStream()
            .filter(Objects::nonNull)
            .map(
                blk -> {
                    AionBlock b = blk.getKey();
                    Message.t_BlockDetail.Builder builder =
                        Message.t_BlockDetail
                            .newBuilder()
                            .setBlockNumber(b.getNumber())
                            .setDifficulty(
                                ByteString.copyFrom(b.getDifficulty()))
                            .setExtraData(
                                ByteString.copyFrom(b.getExtraData()))
                            .setHash(ByteString.copyFrom(b.getHash()))
                            .setLogsBloom(
                                ByteString.copyFrom(b.getLogBloom()))
                            .setMinerAddress(
                                ByteString.copyFrom(
                                    b.getCoinbase().toBytes()))
                            .setNonce(ByteString.copyFrom(b.getNonce()))
                            .setNrgConsumed(b.getNrgConsumed())
                            .setNrgLimit(b.getNrgLimit())
                            .setParentHash(
                                ByteString.copyFrom(b.getParentHash()))
                            .setTimestamp(b.getTimestamp())
                            .setTxTrieRoot(
                                ByteString.copyFrom(b.getTxTrieRoot()))
                            .setReceiptTrieRoot(
                                ByteString.copyFrom(
                                    b.getReceiptsRoot()))
                            .setStateRoot(
                                ByteString.copyFrom(b.getStateRoot()))
                            .setSize(b.getEncoded().length)
                            .setSolution(
                                ByteString.copyFrom(
                                    b.getHeader().getSolution()))
                            .setTotalDifficulty(
                                ByteString.copyFrom(
                                    blk.getValue().toByteArray()));

                    List<AionTransaction> txs = b.getTransactionsList();

                    List<Message.t_TxDetail> tds =
                        txs.parallelStream()
                            .filter(Objects::nonNull)
                            .map(
                                (AionTransaction tx) -> {
                                    AionTxInfo ti =
                                        ((AionBlockchainImpl)
                                            this.ac
                                                .getAionHub()
                                                .getBlockchain())
                                            .getTransactionInfoLite(
                                                tx
                                                    .getHash(),
                                                b
                                                    .getHash());

                                    List<Message.t_LgEle> tles =
                                        ti.getReceipt()
                                            .getLogInfoList()
                                            .parallelStream()
                                            .map(
                                                log -> {
                                                    List<
                                                        String>
                                                        topics =
                                                        new ArrayList<>();
                                                    for (int
                                                        i =
                                                        0;
                                                        i
                                                            < log.getTopics()
                                                            .size();
                                                        i++) {
                                                        topics
                                                            .add(
                                                                TypeConverter
                                                                    .toJsonHex(
                                                                        log.getTopics()
                                                                            .get(
                                                                                i)));
                                                    }

                                                    return Message
                                                        .t_LgEle
                                                        .newBuilder()
                                                        .setData(
                                                            ByteString
                                                                .copyFrom(
                                                                    log
                                                                        .getData()))
                                                        .setAddress(
                                                            ByteString
                                                                .copyFrom(
                                                                    log.getAddress()
                                                                        .toBytes()))
                                                        .addAllTopics(
                                                            topics)
                                                        .build();
                                                })
                                            .filter(
                                                Objects
                                                    ::nonNull)
                                            .collect(
                                                Collectors
                                                    .toList());

                                    Message.t_TxDetail.Builder
                                        tdBuilder =
                                        Message.t_TxDetail
                                            .newBuilder()
                                            .setData(
                                                ByteString
                                                    .copyFrom(
                                                        tx
                                                            .getData()))
                                            .setTo(
                                                ByteString
                                                    .copyFrom(
                                                        tx.getTo()
                                                            .toBytes()))
                                            .setFrom(
                                                ByteString
                                                    .copyFrom(
                                                        tx.getFrom()
                                                            .toBytes()))
                                            .setNonce(
                                                ByteString
                                                    .copyFrom(
                                                        tx
                                                            .getNonce()))
                                            .setValue(
                                                ByteString
                                                    .copyFrom(
                                                        tx
                                                            .getValue()))
                                            .setNrgConsumed(
                                                ti.getReceipt()
                                                    .getEnergyUsed())
                                            .setNrgPrice(
                                                tx
                                                    .getNrgPrice())
                                            .setTxHash(
                                                ByteString
                                                    .copyFrom(
                                                        tx
                                                            .getHash()))
                                            .setTxIndex(
                                                ti
                                                    .getIndex())
                                            .addAllLogs(
                                                tles);

                                    return tdBuilder.build();
                                })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    return builder.addAllTx(tds).build();
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    // all or nothing. if any block from list is not found, unchecked exception gets thrown by
    // Map.entry()
    // causes this function to return in Exception
    private List<Map.Entry<AionBlock, BigInteger>> getBlkAndDifficultyForBlkNumList(
        List<Long> blkNum) {
        return blkNum.parallelStream()
            .map(
                this::getBlockWithTotalDifficulty)
            .collect(Collectors.toList());
    }

    public byte getApiVersion() {
        return JAVAAPI_VAR;
    }

    @Override
    public byte getApiHeaderLen() {
        return JAVAAPI_REQHEADER_LEN;
    }

    @Override
    public int getTxHashLen() {
        return TX_HASH_LEN;
    }

    @Override
    public Map<Long, Fltr> getFilter() {
        return this.getInstalledFltrs();
    }

    @Override
    public Map<ByteArrayWrapper, AionTxReceipt> getPendingReceipts() {
        return this.pendingReceipts;
    }

    @Override
    public BlockingQueue<TxPendingStatus> getPendingStatus() {
        return this.pendingStatus;
    }

    @Override
    public BlockingQueue<TxWaitingMappingUpdate> getTxWait() {
        return this.txWait;
    }

    @Override
    public byte[] parseMsgReq(byte[] request, byte[] msgHash) {
        int headerLen =
            msgHash == null ? this.getApiHeaderLen() : this.getApiHeaderLen() + msgHash.length;
        return ByteBuffer.allocate(request.length - headerLen)
            .put(request, headerLen, request.length - headerLen)
            .array();
    }
}
