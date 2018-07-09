/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.api.server.http;

import static org.aion.base.util.ByteUtil.hexStringToBytes;
import static org.aion.base.util.ByteUtil.toHexString;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.aion.api.server.ApiAion;
import org.aion.api.server.rpc.RpcError;
import org.aion.api.server.rpc.RpcMsg;
import org.aion.api.server.types.ArgFltr;
import org.aion.api.server.types.ArgTxCall;
import org.aion.api.server.types.Blk;
import org.aion.api.server.types.CompiledContr;
import org.aion.api.server.types.Evt;
import org.aion.api.server.types.Fltr;
import org.aion.api.server.types.FltrBlk;
import org.aion.api.server.types.FltrLg;
import org.aion.api.server.types.FltrTx;
import org.aion.api.server.types.NumericalValue;
import org.aion.api.server.types.SyncInfo;
import org.aion.api.server.types.Tx;
import org.aion.api.server.types.TxRecpt;
import org.aion.base.db.IRepository;
import org.aion.base.type.Address;
import org.aion.base.type.Hash256;
import org.aion.base.type.ITransaction;
import org.aion.base.type.ITxReceipt;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.FastByteComparisons;
import org.aion.base.util.TypeConverter;
import org.aion.base.util.Utils;
import org.aion.crypto.ECKey;
import org.aion.crypto.HashUtil;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallback;
import org.aion.evtmgr.impl.evt.EventTx;
import org.aion.mcf.account.Keystore;
import org.aion.mcf.config.CfgApi;
import org.aion.mcf.config.CfgApiNrg;
import org.aion.mcf.config.CfgApiRpc;
import org.aion.mcf.config.CfgApiZmq;
import org.aion.mcf.config.CfgNet;
import org.aion.mcf.config.CfgNetP2p;
import org.aion.mcf.config.CfgSync;
import org.aion.mcf.config.CfgTx;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.Log;
import org.aion.p2p.INode;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.BlockContext;
import org.aion.zero.impl.Version;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.config.CfgConsensusPow;
import org.aion.zero.impl.config.CfgEnergyStrategy;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.sync.PeerState;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.collections4.map.LRUMap;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author chris lin, ali sharif
 * TODO: make implementation pass all spec tests: https://github.com/ethereum/rpc-tests
 */
@SuppressWarnings("Duplicates")
public class ApiWeb3Aion extends ApiAion {

    private final int OPS_RECENT_ENTITY_COUNT = 32;
    private final int OPS_RECENT_ENTITY_CACHE_TIME_SECONDS = 4;

    private final int STRATUM_RECENT_BLK_COUNT = 128;
    private final int STRATUM_BLKTIME_INCLUDED_COUNT = 32;
    private final int STRATUM_CACHE_TIME_SECONDS = 15;
    // TODO: Verify if need to use a concurrent map; locking may allow for use of a simple map
    private HashMap<ByteArrayWrapper, AionBlock> templateMap;
    private ReadWriteLock templateMapLock;
    private IEventMgr evtMgr;
    // doesn't need to be protected for concurrent access, since only one write in the constructor.
    private boolean isFilterEnabled;

    private boolean isSeedMode;

    private ExecutorService cacheUpdateExecutor;
    private final LoadingCache<Integer, ChainHeadView> CachedRecentEntities;

    private ExecutorService MinerStatsExecutor;
    private final LoadingCache<String, MinerStatsView> MinerStats;

    protected void onBlock(AionBlockSummary cbs) {
        if (isFilterEnabled) {
            installedFilters.keySet().forEach((k) -> {
                Fltr f = installedFilters.get(k);
                if (f.isExpired()) {
                    LOG.debug("<Filter: expired, key={}>", k);
                    installedFilters.remove(k);
                } else if (f.onBlock(cbs)) {
                    LOG.debug("<Filter: append, onBlock type={} blk#={}>", f.getType().name(), cbs.getBlock().getNumber());
                }
            });
        }
    }

    protected void pendingTxReceived(ITransaction _tx) {
        if (isFilterEnabled) {
            // not absolutely neccessary to do eviction on installedFilters here, since we're doing it already
            // in the onBlock event. eviction done here "just in case ..."
            installedFilters.keySet().forEach((k) -> {
                Fltr f = installedFilters.get(k);
                if (f.isExpired()) {
                    LOG.debug("<filter expired, key={}>", k);
                    installedFilters.remove(k);
                } else if (f.onTransaction(_tx)) {
                    LOG.info("<filter append, onPendingTransaction fltrSize={} type={} txHash={}>", f.getSize(), f.getType().name(), TypeConverter.toJsonHex(_tx.getHash()));
                }
            });
        }
    }

    protected void pendingTxUpdate(ITxReceipt _txRcpt, EventTx.STATE _state) {
        // commenting this out because of lack support for old web3 client that we are using
        // TODO: re-enable this when we upgrade our web3 client
        /*
        if (isFilterEnabled) {
            ByteArrayWrapper txHashW = new ByteArrayWrapper(((AionTxReceipt) _txRcpt).getTransaction().getHash());
            if (_state.isPending() || _state == EventTx.STATE.DROPPED0) {
                pendingReceipts.put(txHashW, (AionTxReceipt) _txRcpt);
            } else {
                pendingReceipts.remove(txHashW);
            }
        }
        */
    }

    public ApiWeb3Aion(final IAionChain _ac) {
        super(_ac);
        pendingReceipts = Collections.synchronizedMap(new LRUMap<>(FLTRS_MAX, 100));
        templateMap = new HashMap<>();
        templateMapLock = new ReentrantReadWriteLock();
        isFilterEnabled = CfgAion.inst().getApi().getRpc().isFiltersEnabled();
        isSeedMode = CfgAion.inst().getConsensus().isSeed();

        initNrgOracle(_ac);

        if (isFilterEnabled) {
            evtMgr = this.ac.getAionHub().getEventMgr();

            startES("EpWeb3");

            // Fill data on block and transaction events into the filters and pending receipts
            IHandler blkHr = evtMgr.getHandler(IHandler.TYPE.BLOCK0.getValue());
            if (blkHr != null) {
                blkHr.eventCallback(new EventCallback(ees, LOG));
            }

            IHandler txHr = evtMgr.getHandler(IHandler.TYPE.TX0.getValue());
            if (txHr != null) {
                txHr.eventCallback(new EventCallback(ees, LOG));
            }
        }

        // ops-related endpoints
        // https://github.com/google/guava/wiki/CachesExplained#refresh
        CachedRecentEntities = CacheBuilder.newBuilder()
                .maximumSize(1)
                .refreshAfterWrite(OPS_RECENT_ENTITY_CACHE_TIME_SECONDS, TimeUnit.SECONDS)
                .build(
                    new CacheLoader<>() {
                        public ChainHeadView load(Integer key) { // no checked exception
                            return new ChainHeadView(OPS_RECENT_ENTITY_COUNT).update();
                        }

                        public ListenableFuture<ChainHeadView> reload(final Integer key,
                            ChainHeadView prev) {
                            try {
                                ListenableFutureTask<ChainHeadView> task = ListenableFutureTask
                                    .create(() -> new ChainHeadView(prev).update());
                                cacheUpdateExecutor.execute(task);
                                return task;
                            } catch (Throwable e) {
                                LOG.debug("<cache-updater - could not queue up task: ", e);
                                throw (e);
                            } // exception is swallowed by refresh and load. so just log it for our logs
                        }
                    });

        cacheUpdateExecutor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), new CacheUpdateThreadFactory());


        MinerStats = CacheBuilder.newBuilder()
                .maximumSize(1)
                .refreshAfterWrite(STRATUM_CACHE_TIME_SECONDS, TimeUnit.SECONDS)
                .build(
                        new CacheLoader<String, MinerStatsView>() {
                            public MinerStatsView load(String key) { // no checked exception
                                Address miner = new Address(key);
                                MinerStatsView view = new MinerStatsView(STRATUM_RECENT_BLK_COUNT, miner.toBytes()).update();
                                return view;
                            }

                            public ListenableFuture<MinerStatsView> reload(final String key, MinerStatsView prev) {
                                try {
                                    ListenableFutureTask<MinerStatsView> task = ListenableFutureTask.create(
                                        () -> new MinerStatsView(prev).update());
                                    MinerStatsExecutor.execute(task);
                                    return task;
                                } catch (Throwable e) {
                                    LOG.debug("<miner-stats - could not queue up task: ", e);
                                    throw(e);
                                } // exception is swallowed by refresh and load. so just log it for our logs
                            }
                        });

        MinerStatsExecutor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), new MinerStatsThreadFactory());
    }

    // --------------------------------------------------------------------
    // Mining Pool
    // --------------------------------------------------------------------

    /* Return a reference to the AIONBlock without converting values to hex
     * Requied for the mining pool implementation
     */
    private AionBlock getBlockRaw(int bn) {
        // long bn = this.parseBnOrId(_bnOrId);
        AionBlock nb = this.ac.getBlockchain().getBlockByNumber(bn);
        if (nb == null) {
            if (LOG.isDebugEnabled())
                LOG.debug("<get-block-raw bn={} err=not-found>", bn);
            return null;
        } else {
            return nb;
        }
    }

    // --------------------------------------------------------------------
    // Ethereum-Compliant JSON RPC Specification Implementation
    // --------------------------------------------------------------------

    public RpcMsg web3_clientVersion() {
        return new RpcMsg(this.clientVersion);
    }

    public RpcMsg web3_sha3(Object _params) {
        String _data;
        if (_params instanceof JSONArray) {
            _data = ((JSONArray)_params).get(0) + "";
        }
        else if (_params instanceof JSONObject) {
            _data = ((JSONObject)_params).get("data") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        return new RpcMsg(TypeConverter.toJsonHex(HashUtil.keccak256(ByteUtil.hexStringToBytes(_data))));
    }

    public RpcMsg net_version() {
        return new RpcMsg(chainId());
    }

    public RpcMsg net_peerCount() {
        return new RpcMsg(peerCount());
    }

    public RpcMsg net_listening() {
        // currently, p2p manager is always listening for peers and is active
        return new RpcMsg(true);
    }

    public RpcMsg eth_protocolVersion() {
        return new RpcMsg(p2pProtocolVersion());
    }

    public RpcMsg eth_syncing() {
        SyncInfo syncInfo = getSync();
        if (!syncInfo.done) {
            JSONObject obj = new JSONObject();
            // create obj for when syncing is completed
            obj.put("startingBlock", new NumericalValue(syncInfo.chainStartingBlkNumber).toHexString());
            obj.put("currentBlock", new NumericalValue(syncInfo.chainBestBlkNumber).toHexString());
            obj.put("highestBlock", new NumericalValue(syncInfo.networkBestBlkNumber).toHexString());
            return new RpcMsg(obj);
        } else {
            // create obj for when syncing is ongoing
            return new RpcMsg(false);
        }
    }

    public RpcMsg eth_coinbase() {
        return new RpcMsg(getCoinbase());
    }

    public RpcMsg eth_mining() {
        return new RpcMsg(isMining());
    }

    public RpcMsg eth_hashrate() {
        return new RpcMsg(getHashrate());
    }

    public RpcMsg eth_submitHashrate(Object _params) {
        String _hashrate;
        String _clientId;
        if (_params instanceof JSONArray) {
            _hashrate = ((JSONArray)_params).get(0) + "";
            _clientId = ((JSONArray)_params).get(1) + "";
        }
        else if (_params instanceof JSONObject) {
            _hashrate = ((JSONObject)_params).get("hashrate") + "";
            _clientId = ((JSONObject)_params).get("clientId") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        return new RpcMsg(setReportedHashrate(_hashrate, _clientId));
    }

    public RpcMsg eth_gasPrice() {
        return new RpcMsg(TypeConverter.toJsonHex(getRecommendedNrgPrice()));
    }

    public RpcMsg eth_accounts() {
        return new RpcMsg(new JSONArray(getAccounts()));
    }

    public RpcMsg eth_blockNumber() {
        return new RpcMsg(getBestBlock().getNumber());
    }

    public RpcMsg eth_getBalance(Object _params) {
        String _address;
        Object _bnOrId;

        if (_params instanceof JSONArray) {
            _address = ((JSONArray)_params).get(0) + "";
            _bnOrId = ((JSONArray)_params).opt(1);
        }
        else if (_params instanceof JSONObject) {
            _address = ((JSONObject)_params).get("address") + "";
            _bnOrId = ((JSONObject)_params).opt("block") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        Address address = new Address(_address);

        String bnOrId = "latest";
        if (_bnOrId != null && !_bnOrId.equals(JSONObject.NULL))
            bnOrId = _bnOrId + "";

        if (!bnOrId.equalsIgnoreCase("latest")) {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Default block parameter temporarily unsupported");
        }
        /*
        IRepository repo = getRepoByJsonBlockId(bnOrId);
        if (repo == null) // invalid bnOrId
            return new RpcMsg(null, RpcError.EXECUTION_ERROR, "Block not found.");
        */
        IRepository repo = this.ac.getRepository();

        BigInteger balance = repo.getBalance(address);
        return new RpcMsg(TypeConverter.toJsonHex(balance));
    }

    public RpcMsg eth_getStorageAt(Object _params) {
        String _address;
        String _index;
        Object _bnOrId;
        if (_params instanceof JSONArray) {
            _address = ((JSONArray)_params).get(0) + "";
            _index = ((JSONArray)_params).get(1) + "";
            _bnOrId = ((JSONArray)_params).opt(2);
        }
        else if (_params instanceof JSONObject) {
            _address = ((JSONObject)_params).get("address") + "";
            _index = ((JSONObject)_params).get("index") + "";
            _bnOrId = ((JSONObject)_params).opt("block");
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        Address address = new Address(_address);

        String bnOrId = "latest";
        if (_bnOrId != null && !_bnOrId.equals(JSONObject.NULL))
            bnOrId = _bnOrId + "";

        DataWord key;

        try {
            key = new DataWord(ByteUtil.hexStringToBytes(_index));
        } catch (Exception e) {
            // invalid key
            LOG.debug("eth_getStorageAt: invalid storageIndex. Must be <= 16 bytes.");
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid storageIndex. Must be <= 16 bytes.");
        }

        if (!bnOrId.equalsIgnoreCase("latest")) {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Default block parameter temporarily unsupported");
        }
        /*
        IRepository repo = getRepoByJsonBlockId(bnOrId);
        if (repo == null) // invalid bnOrId
            return new RpcMsg(null, RpcError.EXECUTION_ERROR, "Block not found.");
        */
        IRepository repo = this.ac.getRepository();

        @SuppressWarnings("unchecked")
        DataWord storageValue = (DataWord) repo.getStorageValue(address, key);
        if (storageValue != null)
            return new RpcMsg(TypeConverter.toJsonHex(storageValue.getData()));
        else
            return new RpcMsg(null, RpcError.EXECUTION_ERROR, "Storage value not found");
    }

    public RpcMsg eth_getTransactionCount(Object _params) {
        String _address;
        Object _bnOrId;
        if (_params instanceof JSONArray) {
            _address = ((JSONArray)_params).get(0) + "";
            _bnOrId = ((JSONArray)_params).opt(1);
        }
        else if (_params instanceof JSONObject) {
            _address = ((JSONObject)_params).get("address") + "";
            _bnOrId = ((JSONObject)_params).opt("block");
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        Address address = new Address(_address);

        String bnOrId = "latest";
        if (_bnOrId != null && !_bnOrId.equals(JSONObject.NULL))
            bnOrId = _bnOrId + "";

        if (!bnOrId.equalsIgnoreCase("latest")) {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Default block parameter temporarily unsupported");
        }
        /*
        IRepository repo = getRepoByJsonBlockId(bnOrId);
        if (repo == null) // invalid bnOrId
            return new RpcMsg(null, RpcError.EXECUTION_ERROR, "Block not found.");
        */
        IRepository repo = this.ac.getRepository();

        return new RpcMsg(TypeConverter.toJsonHex(repo.getNonce(address)));
    }

    public RpcMsg eth_getBlockTransactionCountByHash(Object _params) {
        String _hash;
        if (_params instanceof JSONArray) {
            _hash = ((JSONArray)_params).get(0) + "";
        }
        else if (_params instanceof JSONObject) {
            _hash = ((JSONObject)_params).get("hash") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        byte[] hash = ByteUtil.hexStringToBytes(_hash);
        AionBlock b = this.ac.getBlockchain().getBlockByHash(hash);
        if (b == null)
            return new RpcMsg(null, RpcError.EXECUTION_ERROR, "Block not found.");

        long n = b.getTransactionsList().size();
        return new RpcMsg(TypeConverter.toJsonHex(n));
    }

    public RpcMsg eth_getBlockTransactionCountByNumber(Object _params) {
        String _bnOrId;
        if (_params instanceof JSONArray) {
            _bnOrId = ((JSONArray)_params).get(0) + "";
        }
        else if (_params instanceof JSONObject) {
            _bnOrId = ((JSONObject)_params).get("block") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        Long bn = parseBnOrId(_bnOrId);
        if (bn == null)
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid block number.");

        // pending transactions
        if (bn < 0) {
            long pendingTxCount = this.ac.getAionHub().getPendingState().getPendingTxSize();
            return new RpcMsg(TypeConverter.toJsonHex(pendingTxCount));
        }

        AionBlock b = this.ac.getBlockchain().getBlockByNumber(bn);
        if (b == null)
            return new RpcMsg(null, RpcError.EXECUTION_ERROR, "Block not found.");

        List<AionTransaction> list = b.getTransactionsList();

        long n = list.size();
        return new RpcMsg(TypeConverter.toJsonHex(n));
    }

    public RpcMsg eth_getCode(Object _params) {
        String _address;
        Object _bnOrId;
        if (_params instanceof JSONArray) {
            _address = ((JSONArray)_params).get(0) + "";
            _bnOrId = ((JSONArray)_params).opt(1);
        }
        else if (_params instanceof JSONObject) {
            _address = ((JSONObject)_params).get("address") + "";
            _bnOrId = ((JSONObject)_params).opt("block");
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        Address address = new Address(_address);

        String bnOrId = "latest";
        if (_bnOrId != null && !_bnOrId.equals(JSONObject.NULL))
            bnOrId = _bnOrId + "";

        if (!bnOrId.equalsIgnoreCase("latest")) {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Default block parameter temporarily unsupported");
        }
        /*
        IRepository repo = getRepoByJsonBlockId(bnOrId);
        if (repo == null) // invalid bnOrId
            return new RpcMsg(null, RpcError.EXECUTION_ERROR, "Block not found.");
        */

        IRepository repo = this.ac.getRepository();
        byte[] code = repo.getCode(address);
        return new RpcMsg(TypeConverter.toJsonHex(code));
    }

    public RpcMsg eth_sign(Object _params) {
        String _address;
        String _message;
        if (_params instanceof JSONArray) {
            _address = ((JSONArray)_params).get(0) + "";
            _message = ((JSONArray)_params).get(1) + "";
        }
        else if (_params instanceof JSONObject) {
            _address = ((JSONObject)_params).get("address") + "";
            _message = ((JSONObject)_params).get("message") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        Address address = Address.wrap(_address);
        ECKey key = getAccountKey(address.toString());
        if (key == null)
            return new RpcMsg(null, RpcError.NOT_ALLOWED, "Account not unlocked.");

        // Message starts with Unicode Character 'END OF MEDIUM' (U+0019)
        String message = "\u0019Aion Signed Message:\n" + _message.length() + _message;
        byte[] messageHash = HashUtil.keccak256(message.getBytes());

        return new RpcMsg(TypeConverter.toJsonHex(key.sign(messageHash).getSignature()));
    }

    public RpcMsg eth_sendTransaction(Object _params) {
        JSONObject _tx;
        if (_params instanceof JSONArray) {
            _tx = ((JSONArray)_params).getJSONObject(0);
        }
        else if (_params instanceof JSONObject) {
            _tx = ((JSONObject)_params).getJSONObject("transaction");
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        ArgTxCall txParams = ArgTxCall.fromJSON(_tx, getNrgOracle(), getDefaultNrgLimit());
        if (txParams == null)
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Please check your transaction object.");

        // check for unlocked account
        Address address = txParams.getFrom();
        ECKey key = getAccountKey(address.toString());

        if (key == null)
            return new RpcMsg(null, RpcError.NOT_ALLOWED, "Account not unlocked.");


        byte[] response = sendTransaction(txParams);
        return new RpcMsg(TypeConverter.toJsonHex(response));
    }

    public RpcMsg eth_sendRawTransaction(Object _params) {
        String _rawTx;
        if (_params instanceof JSONArray) {
            _rawTx = ((JSONArray)_params).get(0) + "";
        }
        else if (_params instanceof JSONObject) {
            _rawTx = ((JSONObject)_params).get("transaction") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        if (_rawTx.equals("null"))
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Null raw transaction provided.");

        byte[] rawTransaction = ByteUtil.hexStringToBytes(_rawTx);

        byte[] transactionHash = sendTransaction(rawTransaction);
        return new RpcMsg(TypeConverter.toJsonHex(transactionHash));
    }

    public RpcMsg eth_call(Object _params) {
        JSONObject _tx;
        Object _bnOrId;
        if (_params instanceof JSONArray) {
            _tx = ((JSONArray)_params).getJSONObject(0);
            _bnOrId = ((JSONArray)_params).opt(1);
        }
        else if (_params instanceof JSONObject) {
            _tx = ((JSONObject)_params).getJSONObject("transaction");
            _bnOrId = ((JSONObject)_params).opt("block");
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        ArgTxCall txParams = ArgTxCall.fromJSON(_tx, getNrgOracle(), getDefaultNrgLimit());

        String bnOrId = "latest";
        if (_bnOrId != null && !_bnOrId.equals(JSONObject.NULL))
            bnOrId = _bnOrId + "";

        Long bn = parseBnOrId(bnOrId);
        if (bn == null || bn < 0)
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid block id provided.");

        AionTransaction tx = new AionTransaction(
                txParams.getNonce().toByteArray(),
                txParams.getTo(),
                txParams.getValue().toByteArray(),
                txParams.getData(),
                txParams.getNrg(),
                txParams.getNrgPrice());

        AionBlock b = this.ac.getBlockchain().getBlockByNumber(bn);
        AionTxReceipt receipt = this.ac.callConstant(tx, b);

        return new RpcMsg(TypeConverter.toJsonHex(receipt.getExecutionResult()));
    }

    public RpcMsg eth_estimateGas(Object _params) {
        JSONObject _tx;
        if (_params instanceof JSONArray) {
            _tx = ((JSONArray)_params).getJSONObject(0);
        }
        else if (_params instanceof JSONObject) {
            _tx = ((JSONObject)_params).getJSONObject("transaction");
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        ArgTxCall txParams = ArgTxCall.fromJSON(_tx, getNrgOracle(), getDefaultNrgLimit());
        NumericalValue estimate = new NumericalValue(estimateNrg(txParams));

        return new RpcMsg(estimate.toHexString());
    }

    public RpcMsg eth_getBlockByHash(Object _params) {
        String _hash;
        boolean _fullTx;
        if (_params instanceof JSONArray) {
            _hash = ((JSONArray)_params).get(0) + "";
            _fullTx = ((JSONArray)_params).optBoolean(1, false);
        }
        else if (_params instanceof JSONObject) {
            _hash = ((JSONObject)_params).get("block") + "";
            _fullTx = ((JSONObject)_params).optBoolean("fullTransaction", false);
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        byte[] hash = ByteUtil.hexStringToBytes(_hash);
        AionBlock block = this.ac.getBlockchain().getBlockByHash(hash);

        AionBlock mainBlock = this.ac.getAionHub().getBlockchain().getBlockByNumber(block.getNumber());
        if (!FastByteComparisons.equal(block.getHash(), mainBlock.getHash())) {
            LOG.debug("<rpc-server not mainchain>", _hash);
            return new RpcMsg(JSONObject.NULL);
        }

        if (block == null) {
            LOG.debug("<get-block hash={} err=not-found>", _hash);
            return new RpcMsg(JSONObject.NULL); // json rpc spec: 'or null when no block was found'
        } else {
            BigInteger totalDiff = this.ac.getAionHub().getBlockStore().getTotalDifficultyForHash(hash);
            return new RpcMsg(Blk.AionBlockToJson(block, totalDiff, _fullTx));
        }
    }

    public RpcMsg eth_getBlockByNumber(Object _params) {
        String _bnOrId;
        boolean _fullTx;
        if (_params instanceof JSONArray) {
            _bnOrId = ((JSONArray)_params).get(0) + "";
            _fullTx = ((JSONArray)_params).optBoolean(1, false);
        }
        else if (_params instanceof JSONObject) {
            _bnOrId = ((JSONObject)_params).get("block") + "";
            _fullTx = ((JSONObject)_params).optBoolean("fullTransaction", false);
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        Long bn = this.parseBnOrId(_bnOrId);

        if (bn == null || bn < 0)
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid block id provided.");

        AionBlock nb = this.ac.getBlockchain().getBlockByNumber(bn);

        if (nb == null) {
            LOG.debug("<get-block bn={} err=not-found>", bn);
            return new RpcMsg(JSONObject.NULL); // json rpc spec: 'or null when no block was found'
        } else {
            BigInteger totalDiff = this.ac.getAionHub().getBlockStore().getTotalDifficultyForHash(nb.getHash());
            return new RpcMsg(Blk.AionBlockToJson(nb, totalDiff, _fullTx));
        }
    }

    public RpcMsg eth_getTransactionByHash(Object _params) {
        String _hash;
        if (_params instanceof JSONArray) {
            _hash = ((JSONArray)_params).get(0) + "";
        }
        else if (_params instanceof JSONObject) {
            _hash = ((JSONObject)_params).get("transactionHash") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        byte[] txHash = ByteUtil.hexStringToBytes(_hash);
        if (_hash.equals("null") || txHash == null) return null;

        AionTxInfo txInfo = this.ac.getAionHub().getBlockchain().getTransactionInfo(txHash);
        if (txInfo == null)
            return new RpcMsg(JSONObject.NULL); // json rpc spec: 'or null when no transaction was found'

        AionBlock b = this.ac.getBlockchain().getBlockByHash(txInfo.getBlockHash());
        if (b == null) return null; // this is actually an internal error

        return new RpcMsg(Tx.InfoToJSON(txInfo, b));
    }

    public RpcMsg eth_getTransactionByBlockHashAndIndex(Object _params) {
        String _hash;
        String _index;
        if (_params instanceof JSONArray) {
            _hash = ((JSONArray)_params).get(0) + "";
            _index = ((JSONArray)_params).get(1) + "";
        }
        else if (_params instanceof JSONObject) {
            _hash = ((JSONObject)_params).get("blockHash") + "";
            _index = ((JSONObject)_params).get("index") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        byte[] hash = ByteUtil.hexStringToBytes(_hash);
        if (_hash.equals("null") || hash == null) return null;

        AionBlock b = this.ac.getBlockchain().getBlockByHash(hash);
        if (b == null)
            return new RpcMsg(JSONObject.NULL); // json rpc spec: 'or null when no transaction was found'

        List<AionTransaction> txs = b.getTransactionsList();

        int idx = Integer.decode(_index);
        if (idx >= txs.size())
            return new RpcMsg(JSONObject.NULL); // json rpc spec: 'or null when no transaction was found'

        return new RpcMsg(Tx.AionTransactionToJSON(txs.get(idx), b, idx));
    }

    public RpcMsg eth_getTransactionByBlockNumberAndIndex(Object _params) {
        String _bnOrId;
        String _index;
        if (_params instanceof JSONArray) {
            _bnOrId = ((JSONArray)_params).get(0) + "";
            _index = ((JSONArray)_params).get(1) + "";
        }
        else if (_params instanceof JSONObject) {
            _bnOrId = ((JSONObject)_params).get("block") + "";
            _index = ((JSONObject)_params).get("index") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        Long bn = parseBnOrId(_bnOrId);
        if (bn == null || bn < 0) return null;

        AionBlock b = this.ac.getBlockchain().getBlockByNumber(bn);
        if (b == null)
            return new RpcMsg(JSONObject.NULL); // json rpc spec: 'or null when no transaction was found'

        List<AionTransaction> txs = b.getTransactionsList();

        int idx = Integer.decode(_index);
        if (idx >= txs.size())
            return new RpcMsg(JSONObject.NULL); // json rpc spec: 'or null when no transaction was found'

        return new RpcMsg(Tx.AionTransactionToJSON(txs.get(idx), b, idx));
    }

    public RpcMsg eth_getTransactionReceipt(Object _params) {
        String _hash;
        if (_params instanceof JSONArray) {
            _hash = ((JSONArray)_params).get(0) + "";
        }
        else if (_params instanceof JSONObject) {
            _hash = ((JSONObject)_params).get("hash") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        byte[] txHash = TypeConverter.StringHexToByteArray(_hash);
        TxRecpt r = getTransactionReceipt(txHash);

        // commenting this out because of lack support for old web3 client that we are using
        // TODO: re-enable this when we upgrade our web3 client
        /*
        // if we can't find the receipt on the mainchain, try looking for it in pending receipts cache
        /*
        if (r == null) {
            AionTxReceipt pendingReceipt = pendingReceipts.get(new ByteArrayWrapper(txHash));
            r = new TxRecpt(pendingReceipt, null, null, null, true);
        }
        */

        if (r == null)
            return new RpcMsg(JSONObject.NULL); // json rpc spec: 'or null when no receipt was found'

        return new RpcMsg(r.toJson());
    }

    /* -------------------------------------------------------------------------
     * compiler
     */

    public RpcMsg eth_getCompilers() {
        return new RpcMsg(new JSONArray(this.compilers));
    }

    public RpcMsg eth_compileSolidity(Object _params) {
        String _contract;
        if (_params instanceof JSONArray) {
            _contract = ((JSONArray)_params).get(0) + "";
        }
        else if (_params instanceof JSONObject) {
            _contract = ((JSONObject)_params).get("contract") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        @SuppressWarnings("unchecked")
        Map<String, CompiledContr> compiled = contract_compileSolidity(_contract);
        JSONObject obj = new JSONObject();
        for (String key : compiled.keySet()) {
            CompiledContr cc = compiled.get(key);
            obj.put(key, cc.toJSON());
        }
        return new RpcMsg(obj);
    }

    /* -------------------------------------------------------------------------
     * filters
     */

    /* Web3 Filters Support
     *
     * NOTE: newFilter behaviour is ill-defined in the JSON-rpc spec for the following scenarios:
     * (an explanation of how we resolved these ambiguities follows immediately after)
     *
     * newFilter is used to subscribe for filter on transaction logs for transactions with provided address and topics
     *
     * role of fromBlock, toBlock fields within context of newFilter, newBlockFilter, newPendingTransactionFilter
     * (they seem only more pertinent for getLogs)
     * how we resolve it: populate historical data (best-effort) in the filter response before "installing the filter"
     * onus on the user to flush the filter of the historical data, before depending on it for up-to-date values.
     * apart from loading historical data, fromBlock & toBlock are ignored when loading events on filter queue
     */
    private FltrLg createFilter(ArgFltr rf) {
        FltrLg filter = new FltrLg();
        filter.setTopics(rf.topics);
        filter.setContractAddress(rf.address);

        Long bnFrom = parseBnOrId(rf.fromBlock);
        Long bnTo = parseBnOrId(rf.toBlock);

        if (bnFrom == null || bnTo == null || bnFrom == -1 || bnTo == -1) {
            LOG.debug("jsonrpc - eth_newFilter(): from, to block parse failed");
            return null;
        }

        final AionBlock fromBlock = this.ac.getBlockchain().getBlockByNumber(bnFrom);
        AionBlock toBlock = this.ac.getBlockchain().getBlockByNumber(bnTo);

        if (fromBlock != null) {
            // need to add historical data
            // this is our own policy: what to do in this case is not defined in the spec
            //
            // policy: add data from earliest to latest, until we can't fill the queue anymore
            //
            // caveat: filling up the events-queue with historical data will cause the following issue:
            // the user will miss all events generated between the first poll and filter installation.

            toBlock = toBlock == null ? getBestBlock() : toBlock;
            for (long i = fromBlock.getNumber(); i <= toBlock.getNumber(); i++) {
                if (filter.isFull()) break;
                filter.onBlock(this.ac.getBlockchain().getBlockByNumber(i), this.ac.getAionHub().getBlockchain());
            }
        }

        return filter;
    }

    public RpcMsg eth_newFilter(Object _params) {
        if (!isFilterEnabled) {
            return new RpcMsg(null, RpcError.NOT_ALLOWED, "Filters over rpc disabled.");
        }

        JSONObject _filterObj;
        if (_params instanceof JSONArray) {
            _filterObj = ((JSONArray)_params).getJSONObject(0);
        }
        else if (_params instanceof JSONObject) {
            _filterObj = ((JSONObject)_params).getJSONObject("filter");
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        ArgFltr rf = ArgFltr.fromJSON(_filterObj);

        FltrLg filter = createFilter(rf);
        if (filter == null)
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid block ids provided.");

        // "install" the filter after populating historical data;
        // rationale: until the user gets the id back, the user should not expect the filter to be "installed" anyway.
        long id = fltrIndex.getAndIncrement();
        installedFilters.put(id, filter);

        return new RpcMsg(TypeConverter.toJsonHex(id));
    }

    public RpcMsg eth_newBlockFilter() {
        if (!isFilterEnabled) {
            return new RpcMsg(null, RpcError.NOT_ALLOWED, "Filters over rpc disabled.");
        }

        long id = fltrIndex.getAndIncrement();
        installedFilters.put(id, new FltrBlk());
        return new RpcMsg(TypeConverter.toJsonHex(id));
    }

    public RpcMsg eth_newPendingTransactionFilter() {
        if (!isFilterEnabled) {
            return new RpcMsg(null, RpcError.NOT_ALLOWED, "Filters over rpc disabled.");
        }

        long id = fltrIndex.getAndIncrement();
        installedFilters.put(id, new FltrTx());
        return new RpcMsg(TypeConverter.toJsonHex(id));
    }

    public RpcMsg eth_uninstallFilter(Object _params) {
        if (!isFilterEnabled) {
            return new RpcMsg(null, RpcError.NOT_ALLOWED, "Filters over rpc disabled.");
        }

        String _id;
        if (_params instanceof JSONArray) {
            _id = ((JSONArray)_params).get(0) + "";
        }
        else if (_params instanceof JSONObject) {
            _id = ((JSONObject)_params).get("id") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        return new RpcMsg(installedFilters.remove(TypeConverter.StringHexToBigInteger(_id).longValue()) != null);
    }

    private JSONArray buildFilterResponse(Fltr filter) {
        Object[] events = filter.poll();
        JSONArray response = new JSONArray();
        for (Object event : events) {
            if (event instanceof Evt) {
                // put the Object we get out of the Evt object in here
                response.put(((Evt) event).toJSON());
            }
        }
        return response;
    }

    public RpcMsg eth_getFilterChanges(Object _params) {
        if (!isFilterEnabled) {
            return new RpcMsg(null, RpcError.NOT_ALLOWED, "Filters over rpc disabled.");
        }

        String _id;
        if (_params instanceof JSONArray) {
            _id = ((JSONArray)_params).get(0) + "";
        }
        else if (_params instanceof JSONObject) {
            _id = ((JSONObject)_params).get("id") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        long id = TypeConverter.StringHexToBigInteger(_id).longValue();
        Fltr filter = installedFilters.get(id);

        if (filter == null)
            return new RpcMsg(null, RpcError.EXECUTION_ERROR, "Filter not found.");

        return new RpcMsg(buildFilterResponse(filter));
    }

    public RpcMsg eth_getLogs(Object _params) {
        JSONObject _filterObj;
        if (_params instanceof JSONArray) {
            _filterObj = ((JSONArray)_params).getJSONObject(0);
        }
        else if (_params instanceof JSONObject) {
            _filterObj = ((JSONObject)_params).getJSONObject("filter");
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        ArgFltr rf = ArgFltr.fromJSON(_filterObj);
        FltrLg filter = createFilter(rf);
        if (filter == null)
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid block ids provided.");

        return new RpcMsg(buildFilterResponse(filter));
    }

    /* -------------------------------------------------------------------------
     * personal
     */

    public RpcMsg personal_unlockAccount(Object _params) {
        String _account;
        String _password;
        Object _duration;
        if (_params instanceof JSONArray) {
            _account = ((JSONArray)_params).get(0) + "";
            _password = ((JSONArray)_params).get(1) + "";
            _duration = ((JSONArray)_params).opt(2);
        }
        else if (_params instanceof JSONObject) {
            _account = ((JSONObject)_params).get("address") + "";
            _password = ((JSONObject)_params).get("password") + "";
            _duration = ((JSONObject)_params).opt("duration");
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }


        int duration = 300;
        if (_duration != null && !_duration.equals(JSONObject.NULL))
            duration = new BigInteger(_duration + "").intValueExact();

        return new RpcMsg(unlockAccount(_account, _password, duration));
    }

    public RpcMsg personal_lockAccount(Object _params) {
        String _account;
        String _password;
        if (_params instanceof JSONArray) {
            _account = ((JSONArray)_params).get(0) + "";
            _password = ((JSONArray)_params).get(1) + "";
        }
        else if (_params instanceof JSONObject) {
            _account = ((JSONObject)_params).get("address") + "";
            _password = ((JSONObject)_params).get("password") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        return new RpcMsg(lockAccount(Address.wrap(_account), _password));
    }

    public RpcMsg personal_newAccount(Object _params) {
        String _password;
        if (_params instanceof JSONArray) {
            _password = ((JSONArray)_params).get(0) + "";
        }
        else if (_params instanceof JSONObject) {
            _password = ((JSONObject)_params).get("password") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        String address = Keystore.create(_password);

        return new RpcMsg(TypeConverter.toJsonHex(address));
    }


    /* -------------------------------------------------------------------------
     * debug
     */

    public RpcMsg debug_getBlocksByNumber(Object _params) {
        String _bnOrId;
        boolean _fullTx;
        if (_params instanceof JSONArray) {
            _bnOrId = ((JSONArray)_params).get(0) + "";
            _fullTx = ((JSONArray)_params).optBoolean(1, false);
        }
        else if (_params instanceof JSONObject) {
            _bnOrId = ((JSONObject)_params).get("block") + "";
            _fullTx = ((JSONObject)_params).optBoolean("fullTransaction", false);
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        Long bn = parseBnOrId(_bnOrId);

        if (bn == null || bn < 0)
            return null;

        List<Map.Entry<AionBlock, Map.Entry<BigInteger, Boolean>>> blocks = ((AionBlockStore) this.ac.getAionHub().getBlockchain().getBlockStore()).getBlocksByNumber(bn);
        if (blocks == null) {
            return new RpcMsg(null, RpcError.EXECUTION_ERROR, "Blocks requested not found.");
        }

        JSONArray response = new JSONArray();
        for (Map.Entry<AionBlock, Map.Entry<BigInteger, Boolean>> block : blocks) {
            JSONObject b = (JSONObject) Blk.AionBlockToJson(block.getKey(), block.getValue().getKey(), _fullTx);
            b.put("mainchain", block.getValue().getValue());
            response.put(b);
        }
        return new RpcMsg(response);
    }

    /* -------------------------------------------------------------------------
     * private debugging APIs
     * Reasoning for not adding this to conventional web3 calls is so
     * we can freely change the responses without breaking compatibility
     */
    public RpcMsg priv_peers() {
        Map<Integer, INode> activeNodes = this.ac.getAionHub().getP2pMgr().getActiveNodes();

        JSONArray peerList = new JSONArray();

        for (INode node : activeNodes.values()) {
            JSONObject n = new JSONObject();
            n.put("idShort", node.getIdShort());
            n.put("id", new String(node.getId()));
            n.put("idHash", node.getIdHash());
            n.put("version", node.getBinaryVersion());
            n.put("blockNumber", node.getBestBlockNumber());
            n.put("totalDifficulty", node.getTotalDifficulty());

            JSONObject network = new JSONObject();
            network.put("remoteAddress", node.getIpStr() + ":" + node.getPort());
            n.put("network", network);
            n.put("latestTimestamp", node.getTimestamp());

            // generate a date corresponding to UTC date time (not local)
            String utcTimestampDate = Instant.ofEpochMilli(node.getTimestamp()).atOffset(ZoneOffset.UTC).toString();
            n.put("latestTimestampUTC", utcTimestampDate);
            n.put("version", node.getBinaryVersion());

            peerList.put(n);
        }
        return new RpcMsg(peerList);
    }

    public RpcMsg priv_p2pConfig() {
        CfgNetP2p p2p = CfgAion.inst().getNet().getP2p();

        JSONObject obj = new JSONObject();
        obj.put("localBinding", p2p.getIp() + ":" + p2p.getPort());
        return new RpcMsg(obj);
    }

    // default block for pending transactions
    private static final AionBlock defaultBlock = new AionBlock(new A0BlockHeader.Builder().build(), Collections.emptyList());

    public RpcMsg priv_getPendingTransactions(Object _params) {
        boolean fullTx = ((JSONArray)_params).optBoolean(0, false);
        List<AionTransaction> transactions = this.ac.getPendingStateTransactions();

        JSONArray arr = new JSONArray();
        for (int i = 0; i < transactions.size(); i++) {
            if (fullTx) {
                arr.put(Tx.AionTransactionToJSON(transactions.get(i), defaultBlock, i));
            } else {
                arr.put(ByteUtil.toHexString(transactions.get(i).getHash()));
            }
        }
        return new RpcMsg(arr);
    }

    public RpcMsg priv_getPendingSize() {
        return new RpcMsg(this.ac.getPendingStateTransactions().size());
    }

    public RpcMsg priv_dumpTransaction(Object _params) {
        String transactionHash;
        if (_params instanceof JSONArray) {
            transactionHash = ((JSONArray) _params).get(0) + "";
        } else if (_params instanceof JSONObject) {
            transactionHash = ((JSONObject) _params).get("hash") + "";
        } else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        byte[] hash = ByteUtil.hexStringToBytes(transactionHash);
        if (hash == null)
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid transaction hash");

        // begin output processing
        AionTxInfo transaction = this.ac.getAionHub()
                .getBlockchain()
                .getTransactionInfo(hash);

        if (transaction == null)
            return new RpcMsg(JSONObject.NULL);

        JSONObject tx = Tx.InfoToJSON(transaction,
                this.ac.getBlockchain().getBlockByHash(transaction.getBlockHash()));
        String raw = ByteUtil.toHexString(transaction.getReceipt().getTransaction().getEncoded());

        JSONObject obj = new JSONObject();
        obj.put("transaction", tx);
        obj.put("raw", raw);
        return new RpcMsg(obj);
    }

    public RpcMsg priv_dumpBlockByHash(Object _params) {
        String hashString;
        if (_params instanceof JSONArray) {
            hashString = ((JSONArray) _params).get(0) + "";
        } else if (_params instanceof JSONObject) {
            hashString = ((JSONObject) _params).get("hash") + "";
        } else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        byte[] hash = ByteUtil.hexStringToBytes(hashString);
        if (hash == null)
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid block hash");

        AionBlock block = this.ac.getBlockchain().getBlockByHash(hash);

        if (block == null)
            return new RpcMsg(JSONObject.NULL);

        BigInteger totalDiff = this.ac.getBlockchain().getTotalDifficultyByHash(new Hash256(hash));
        return new RpcMsg(dumpBlock(block, totalDiff, false));
    }

    public RpcMsg priv_dumpBlockByNumber(Object _params) {
        String numberString;
        if (_params instanceof JSONArray) {
            numberString = ((JSONArray) _params).get(0) + "";
        } else if (_params instanceof JSONObject) {
            numberString = ((JSONObject) _params).get("number") + "";
        } else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        // TODO: parse hex
        long number;
        try {
            number = Long.parseLong(numberString);
        } catch (NumberFormatException e) {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Unable to decode input number");
        }
        AionBlock block = this.ac.getBlockchain().getBlockByNumber(number);

        if (block == null)
            return new RpcMsg(JSONObject.NULL);

        BigInteger totalDiff = this.ac.getBlockchain().getTotalDifficultyByHash(new Hash256(block.getHash()));
        return new RpcMsg(dumpBlock(block, totalDiff, false));
    }

    private static JSONObject dumpBlock(AionBlock block, BigInteger totalDiff, boolean full) {
        JSONObject obj = new JSONObject();
        obj.put("block", Blk.AionBlockToJson(block, totalDiff, full));
        obj.put("raw", ByteUtil.toHexString(block.getEncoded()));
        return obj;
    }

    /**
     * Very short blurb generated about our most important stats, intended for
     * quick digestion and monitoring tool usage
     */
    // TODO
    public RpcMsg priv_shortStats() {
        AionBlock block = this.ac.getBlockchain().getBestBlock();
        Map<Integer, INode> peer = this.ac.getAionHub().getP2pMgr().getActiveNodes();

        // this could be optimized (cached)
        INode maxPeer = null;
        for (INode p : peer.values()) {
            if (maxPeer == null) {
                maxPeer = p;
                continue;
            }

            if (p.getTotalDifficulty().compareTo(maxPeer.getTotalDifficulty()) > 0)
                maxPeer = p;
        }

        // basic local configuration
        CfgAion config = CfgAion.inst();

        JSONObject obj = new JSONObject();
        obj.put("id", config.getId());
        obj.put("genesisHash", ByteUtil.toHexString(config.getGenesis().getHash()));
        obj.put("version", Version.KERNEL_VERSION);
        obj.put("bootBlock", this.ac.getAionHub().getStartingBlock().getNumber());


        long time = System.currentTimeMillis();
        obj.put("timestamp", time);
        obj.put("timestampUTC", Instant.ofEpochMilli(time).atOffset(ZoneOffset.UTC).toString());

        // base.blockchain
        JSONObject blockchain = new JSONObject();
        blockchain.put("bestBlockhash", ByteUtil.toHexString(block.getHash()));
        blockchain.put("bestNumber", block.getNumber());
        blockchain.put("totalDifficulty", this.ac.getBlockchain()
                .getTotalDifficultyByHash(new Hash256(block.getHash())));
        // end
        obj.put("local", blockchain);

        // base.network
        JSONObject network = new JSONObject();
        // remote
        if (maxPeer != null) {
            // base.network.best
            JSONObject remote = new JSONObject();
            remote.put("id", new String(maxPeer.getId()));
            remote.put("totalDifficulty", maxPeer.getTotalDifficulty());
            remote.put("bestNumber", maxPeer.getBestBlockNumber());
            remote.put("version", maxPeer.getBinaryVersion());
            remote.put("timestamp", maxPeer.getTimestamp());
            remote.put("timestampUTC", Instant.ofEpochMilli(maxPeer.getTimestamp()).atOffset(ZoneOffset.UTC).toString());
            // end
            network.put("best", remote);
        }

        // end
        network.put("peerCount", peer.size());
        obj.put("network", network);

        return new RpcMsg(obj);
    }

    /**
     * This may seem similar to a superset of peers, with the difference
     * being that this should only contain a subset of peers we are
     * actively syncing from
     */
    public RpcMsg priv_syncPeers() {
        // contract here is we do NOT modify the peerStates in any way
        Map<Integer, PeerState> peerStates = this.ac.getAionHub().getSyncMgr().getPeerStates();

        // also retrieve nodes from p2p to see if we can piece together a full state
        Map<Integer, INode> nodeState = this.ac.getAionHub().getP2pMgr().getActiveNodes();

        JSONArray array = new JSONArray();
        for (Map.Entry<Integer, PeerState> peerState : peerStates.entrySet()) {
            // begin []
            JSONObject peerObj = new JSONObject();
            INode node;
            if ((node = nodeState.get(peerState.getKey())) != null) {
                // base[].node
                JSONObject nodeObj = new JSONObject();
                nodeObj.put("id", new String(node.getId()));
                nodeObj.put("totalDifficulty", node.getTotalDifficulty());
                nodeObj.put("bestNumber", node.getBestBlockNumber());
                nodeObj.put("version", node.getBinaryVersion());
                nodeObj.put("timestamp", node.getTimestamp());
                nodeObj.put("timestampUTC", Instant.ofEpochMilli(node.getTimestamp()).atOffset(ZoneOffset.UTC).toString());

                //end
                peerObj.put("node", nodeObj);
            }

            PeerState ps = peerState.getValue();
            peerObj.put("idHash", peerState.getKey());
            peerObj.put("lastRequestTimestamp", ps.getLastHeaderRequest());
            peerObj.put("lastRequestTimestampUTC", Instant.ofEpochMilli(ps.getLastHeaderRequest()).atOffset(ZoneOffset.UTC).toString());
            peerObj.put("mode", ps.getMode().toString());
            peerObj.put("base", ps.getBase());

            // end
            array.put(peerObj);
        }
        return new RpcMsg(array);
    }


    public RpcMsg priv_config() {
        JSONObject obj = new JSONObject();

        CfgAion config = CfgAion.inst();

        obj.put("id", config.getId());
        obj.put("basePath", config.getBasePath());

        obj.put("net", configNet());
        obj.put("consensus", configConsensus());
        obj.put("sync", configSync());
        obj.put("api", configApi());
        obj.put("db", configDb());
        obj.put("tx", configTx());

        return new RpcMsg(obj);
    }

    // TODO: we can refactor these in the future to be in
    // their respective classes, for now put the toJson here

    private static JSONObject configNet() {
        CfgNet config = CfgAion.inst().getNet();
        JSONObject obj = new JSONObject();

        // begin base.net.p2p
        CfgNetP2p configP2p = config.getP2p();
        JSONObject p2p = new JSONObject();
        p2p.put("ip", configP2p.getIp());
        p2p.put("port", configP2p.getPort());
        p2p.put("bootlistSyncOnly", configP2p.getBootlistSyncOnly());
        p2p.put("discover", configP2p.getDiscover());
        p2p.put("errorTolerance", configP2p.getErrorTolerance());
        p2p.put("maxActiveNodes", configP2p.getMaxActiveNodes());
        p2p.put("maxTempNodes", configP2p.getMaxTempNodes());

        // end
        obj.put("p2p", p2p);

        // begin base.net.nodes[]
        JSONArray nodeArray = new JSONArray();
        for (String n : config.getNodes()) {
            nodeArray.put(n);
        }

        // end
        obj.put("nodes", nodeArray);
        // begin base
        obj.put("id", config.getId());

        // end
        return obj;
    }

    private static JSONObject configConsensus() {
        CfgConsensusPow config = CfgAion.inst().getConsensus();
        JSONObject obj = new JSONObject();
        obj.put("mining", config.getMining());
        obj.put("minerAddress", config.getMinerAddress());
        obj.put("threads", config.getCpuMineThreads());
        obj.put("extraData", config.getExtraData());
        obj.put("isSeed", config.isSeed());

        // base.consensus.energyStrategy
        CfgEnergyStrategy nrg = config.getEnergyStrategy();
        JSONObject nrgObj = new JSONObject();
        nrgObj.put("strategy", nrg.getStrategy());
        nrgObj.put("target", nrg.getTarget());
        nrgObj.put("upper", nrg.getUpperBound());
        nrgObj.put("lower", nrg.getLowerBound());

        // end
        obj.put("energyStrategy", nrgObj);
        return obj;
    }

    private static JSONObject configSync() {
        CfgSync config = CfgAion.inst().getSync();
        JSONObject obj = new JSONObject();
        obj.put("showStatus", config.getShowStatus());
        obj.put("blocksQueueMax", config.getBlocksQueueMax());
        return obj;
    }

    private static JSONObject configApi() {
        CfgApi config = CfgAion.inst().getApi();

        JSONObject obj = new JSONObject();

        // base.api.rpc
        CfgApiRpc rpcConfig = config.getRpc();
        JSONObject rpc = new JSONObject();
        rpc.put("ip", rpcConfig.getIp());
        rpc.put("port", rpcConfig.getPort());
        rpc.put("corsEnabled", rpcConfig.getCorsEnabled());
        rpc.put("active", rpcConfig.getActive());
        rpc.put("maxThread", rpcConfig.getMaxthread());

        // end
        obj.put("rpc", rpc);

        // base.api.zmq
        CfgApiZmq zmqConfig = config.getZmq();
        JSONObject zmq = new JSONObject();

        zmq.put("ip", zmqConfig.getIp());
        zmq.put("port", zmqConfig.getPort());
        zmq.put("active", zmqConfig.getActive());

        // end
        obj.put("zmq", zmq);

        // base.api.nrg
        CfgApiNrg nrgConfig = config.getNrg();
        JSONObject nrg = new JSONObject();

        nrg.put("defaultPrice", nrgConfig.getNrgPriceDefault());
        nrg.put("maxPrice", nrgConfig.getNrgPriceMax());

        // end
        obj.put("nrg", nrg);
        return obj;
    }

    // this is temporarily disabled until DB configuration changes come in
    private static JSONObject configDb() {
        return new JSONObject();
    }

    private static JSONObject configTx() {
        CfgTx config = CfgAion.inst().getTx();
        JSONObject obj = new JSONObject();
        obj.put("cacheMax", config.getCacheMax());
        obj.put("poolBackup", config.getPoolBackup());
        obj.put("buffer", config.getBuffer());
        obj.put("poolDump", config.getPoolDump());
        return obj;
    }

    /* -------------------------------------------------------------------------
     * operational api
     */

    // always gets the latest account state
    public RpcMsg ops_getAccountState(Object _params) {
        String _address;
        if (_params instanceof JSONArray) {
            _address = ((JSONArray)_params).get(0) + "";
        }
        else if (_params instanceof JSONObject) {
            _address = ((JSONObject)_params).get("address") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        Address address;

        try {
            address = new Address(_address);
        } catch (Exception e) {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid address provided.");
        }

        long latestBlkNum = this.getBestBlock().getNumber();
        AccountState accountState = ((AionRepositoryImpl) this.ac.getRepository()).getAccountState(address);

        BigInteger nonce = BigInteger.ZERO;
        BigInteger balance = BigInteger.ZERO;

        if (accountState != null) {
            nonce = accountState.getNonce();
            balance = accountState.getBalance();
        }

        JSONObject response = new JSONObject();
        response.put("address", address.toString());
        response.put("blockNumber", latestBlkNum);
        response.put("balance", TypeConverter.toJsonHex(balance));
        response.put("nonce", TypeConverter.toJsonHex(nonce));

        return new RpcMsg(response);
    }

    // always gets the latest 20 blocks and transactions
    private class ChainHeadView {
        LinkedList<byte[]> hashQueue; // more precisely a dequeue
        Map<byte[], JSONObject> blkList;
        Map<byte[], AionBlock> blkObjList;
        Map<byte[], List<AionTransaction>> txnList;

        private JSONObject response;

        private int qSize;

        public ChainHeadView(ChainHeadView cv) {
            hashQueue = new LinkedList<>(cv.hashQueue);
            blkList = new HashMap<>(cv.blkList);
            blkObjList = new HashMap<>(cv.blkObjList);
            txnList = new HashMap<>(cv.txnList);
            response = new JSONObject(cv.response, JSONObject.getNames(cv.response));
            qSize = cv.qSize;
        }

        public ChainHeadView(int _qSize) {
            hashQueue = new LinkedList<>();
            blkList = new HashMap<>();
            blkObjList = new HashMap<>();
            txnList = new HashMap<>();
            response = new JSONObject();
            qSize = _qSize;
        }

        private JSONObject getJson(AionBlock _b) {
            BigInteger totalDiff = ac.getAionHub().getBlockStore().getTotalDifficultyForHash(_b.getHash());
            return Blk.AionBlockOnlyToJson(_b, totalDiff);
        }

        private JSONObject buildResponse() {
            JSONArray blks = new JSONArray();
            JSONArray txns = new JSONArray();

            // return qSize number of blocks and transactions as json
            for (int i = 0; i < hashQueue.size(); i++) {
                byte[] hash = hashQueue.get(i);
                JSONObject blk = blkList.get(hash);
                if (i < hashQueue.size()-1) {
                    AionBlock blkThis = blkObjList.get(hash);
                    AionBlock blkNext = blkObjList.get(hashQueue.get(i+1));
                    blk.put("blockTime", blkThis.getTimestamp() - blkNext.getTimestamp());
                }
                blks.put(blk);
                List<AionTransaction> t = txnList.get(hash);

                for (int j = 0; (j < t.size() && txns.length() <= qSize); j++) {
                    txns.put(Tx.AionTransactionToJSON(t.get(j), blkObjList.get(hash), j));
                }
            }

            JSONObject metrics;
            try {
                metrics = computeMetrics();
            } catch (Exception e) {
                LOG.error("failed to compute metrics.", e);
                metrics = new JSONObject();
            }

            JSONObject o = new JSONObject();
            o.put("blocks", blks);
            o.put("transactions", txns);
            o.put("metrics", metrics);
            return o;
        }

        private JSONObject computeMetrics() {
            long blkTimeAccumulator = 0L;
            BigInteger lastDifficulty = null;
            BigInteger difficultyAccumulator = new BigInteger("0");
            BigInteger nrgConsumedAccumulator = new BigInteger("0");
            BigInteger nrgLimitAccumulator = new BigInteger("0");
            long txnCount = 0L;

            int count = blkObjList.size();
            Long lastBlkTimestamp = null;
            AionBlock b = null;
            ListIterator li = hashQueue.listIterator(0);
            while(li.hasNext()) {
                byte[] hash = (byte[]) li.next();
                b = blkObjList.get(hash);

                if (lastBlkTimestamp != null) {
                    blkTimeAccumulator += (lastBlkTimestamp - b.getTimestamp());
                }
                lastBlkTimestamp = b.getTimestamp();

                difficultyAccumulator = difficultyAccumulator.add(new BigInteger(b.getDifficulty()));
                lastDifficulty = new BigInteger(b.getDifficulty());

                nrgConsumedAccumulator = nrgConsumedAccumulator.add(new BigInteger(Long.toString(b.getNrgConsumed())));
                nrgLimitAccumulator = nrgLimitAccumulator.add(new BigInteger(Long.toString(b.getNrgLimit())));
                txnCount += b.getTransactionsList().size();
            }

            BigInteger lastBlkReward = ((AionBlockchainImpl)ac.getBlockchain()).getChainConfiguration().getRewardsCalculator().calculateReward(b.getHeader()) ;

            double blkTime = 0;
            double hashRate = 0;
            double avgDifficulty = 0;
            double avgNrgConsumedPerBlock = 0;
            double avgNrgLimitPerBlock = 0;
            double txnPerSec = 0;

            if (count > 0 && blkTimeAccumulator > 0) {
                blkTime = blkTimeAccumulator / (double)count;
                hashRate = lastDifficulty.longValue() / blkTime;
                avgDifficulty = difficultyAccumulator.longValue() / (double)count;
                avgNrgConsumedPerBlock = nrgConsumedAccumulator.longValue() / (double)count;
                avgNrgLimitPerBlock = nrgLimitAccumulator.longValue() / (double)count;
                txnPerSec = txnCount / (double)blkTimeAccumulator;
            }

            long startBlock = 0;
            long endBlock = 0;
            long startTimestamp = 0;
            long endTimestamp = 0;
            long currentBlockchainHead = 0;

            if (hashQueue.size() > 0) {
                AionBlock startBlockObj = blkObjList.get(hashQueue.peekLast());
                AionBlock endBlockObj = blkObjList.get(hashQueue.peekFirst());

                startBlock = startBlockObj.getNumber();
                endBlock = endBlockObj.getNumber();
                startTimestamp = startBlockObj.getTimestamp();
                endTimestamp = endBlockObj.getTimestamp();
                currentBlockchainHead = endBlock;
            }

            JSONObject metrics = new JSONObject();
            metrics.put("averageDifficulty",avgDifficulty);
            metrics.put("averageBlockTime", blkTime);
            metrics.put("hashRate",hashRate);
            metrics.put("transactionPerSecond",txnPerSec);
            metrics.put("lastBlockReward",lastBlkReward);
            metrics.put("targetBlockTime", 10);
            metrics.put("blockWindow", OPS_RECENT_ENTITY_COUNT);

            metrics.put("startBlock", startBlock);
            metrics.put("endBlock", endBlock);
            metrics.put("startTimestamp", startTimestamp);
            metrics.put("endTimestamp", endTimestamp);
            metrics.put("currentBlockchainHead", currentBlockchainHead);

            metrics.put("averageNrgConsumedPerBlock",avgNrgConsumedPerBlock);
            metrics.put("averageNrgLimitPerBlock",avgNrgLimitPerBlock);

            return metrics;
        }

        ChainHeadView update() {
            // get the latest head
            AionBlock blk = getBestBlock();

            if (FastByteComparisons.equal(hashQueue.peekFirst(), blk.getHash())) {
                return this; // nothing to do
            }

            // evict data as necessary
            LinkedList<Map.Entry<byte[],Map.Entry<AionBlock, JSONObject>>> tempStack = new LinkedList<>();
            tempStack.push(Map.entry(blk.getHash(), Map.entry(blk, getJson(blk))));
            int itr = 1; // deliberately 1, since we've already added the 0th element to the stack

            /*
            if (hashQueue.peekFirst() != null) {
                System.out.println("[" + 0 + "]: " + TypeConverter.toJsonHex(hashQueue.peekFirst()) + " - " + blocks.get(hashQueue.peekFirst()).getNumber());
                System.out.println("----------------------------------------------------------");
                System.out.println("isParentHashMatch? " + FastByteComparisons.equal(hashQueue.peekFirst(), blk.getParentHash()));
                System.out.println("blk.getNumber() " + blk.getNumber());
            }
            System.out.println("blkNum: " + blk.getNumber() +
                    " parentHash: " + TypeConverter.toJsonHex(blk.getParentHash()) +
                    " blkHash: " + TypeConverter.toJsonHex(blk.getHash()));
            */

            while(!FastByteComparisons.equal(hashQueue.peekFirst(), blk.getParentHash())
                    && itr < qSize
                    && blk.getNumber() > 2) {

                blk = getBlockByHash(blk.getParentHash());
                tempStack.push(Map.entry(blk.getHash(), Map.entry(blk, getJson(blk))));
                itr++;
                /*
                System.out.println("blkNum: " + blk.getNumber() +
                        " parentHash: " + TypeConverter.toJsonHex(blk.getParentHash()) +
                        " blkHash: " + TypeConverter.toJsonHex(blk.getHash()));
                */
            }

            // evict out the right number of elements first
            for (int i = 0; i < tempStack.size(); i++) {
                byte[] tailHash = hashQueue.pollLast();
                if (tailHash != null) {
                    blkList.remove(tailHash);
                    blkObjList.remove(tailHash);
                    txnList.remove(tailHash);
                }
            }

            // empty out the stack into the queue
            while (!tempStack.isEmpty()) {
                // add to the queue
                Map.Entry<byte[], Map.Entry<AionBlock,JSONObject>> element = tempStack.pop();
                byte[] hash = element.getKey();
                AionBlock blkObj = element.getValue().getKey();
                JSONObject blkJson = element.getValue().getValue();
                List<AionTransaction> txnJson = blkObj.getTransactionsList();

                hashQueue.push(hash);
                blkList.put(hash, blkJson);
                blkObjList.put(hash, blkObj);
                txnList.put(hash, txnJson);
            }

            /*
            System.out.println("[" + 0 + "]: " + TypeConverter.toJsonHex(hashQueue.peekFirst()) + " - " + blocks.get(hashQueue.peekFirst()).getNumber());
            System.out.println("----------------------------------------------------------");
            for (int i = hashQueue.size() - 1; i >= 0; i--) {
                System.out.println("[" + i + "]: " + TypeConverter.toJsonHex(hashQueue.get(i)) + " - " + blocks.get(hashQueue.get(i)).getNumber());
            }
            */
            this.response = buildResponse();

            return this;
        }

        JSONObject getResponse() {
            return response;
        }

        long getViewBestBlock() {
            return blkObjList.get(hashQueue.peekFirst()).getNumber();
        }
    }

    public class CacheUpdateThreadFactory implements ThreadFactory {
        private final AtomicInteger tnum = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "cache-update-" + tnum.getAndIncrement());
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        }
    }

    private enum CachedResponseType {
        CHAIN_HEAD
    }

    public RpcMsg ops_getChainHeadView() {
        try {
            ChainHeadView v = CachedRecentEntities.get(CachedResponseType.CHAIN_HEAD.ordinal());
            return new RpcMsg(v.getResponse());
        } catch (Exception e) {
            LOG.error("<rpc-server - cannot get cached response for ops_getChainHeadView: ", e);
            return new RpcMsg(null, RpcError.EXECUTION_ERROR, "Cached response retrieve failed.");
        }
    }

    public RpcMsg ops_getChainHeadViewBestBlock() {
        try {
            ChainHeadView v = CachedRecentEntities.get(CachedResponseType.CHAIN_HEAD.ordinal());
            return new RpcMsg(v.getViewBestBlock());
        } catch (Exception e) {
            LOG.error("<rpc-server - cannot get cached response for ops_getChainHeadView: ", e);
            return new RpcMsg(null, RpcError.EXECUTION_ERROR, "Cached response retrieve failed.");
        }
    }

    // use a custom implementation to get a receipt with 2 db reads and a constant time op, as opposed to
    // the getTransactionReceipt() in parent, which computes cumulativeNrg computatio for spec compliance
    public RpcMsg ops_getTransaction(Object _params) {
        String _hash;
        if (_params instanceof JSONArray) {
            _hash = ((JSONArray)_params).get(0) + "";
        }
        else if (_params instanceof JSONObject) {
            _hash = ((JSONObject)_params).get("hash") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        byte[] txHash = TypeConverter.StringHexToByteArray(_hash);

        if (txHash == null)
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");

        AionTxInfo txInfo = this.ac.getAionHub().getBlockchain().getTransactionInfo(txHash);

        if (txInfo == null) return new RpcMsg(JSONObject.NULL);

        AionBlock block = this.ac.getAionHub().getBlockchain().getBlockByHash(txInfo.getBlockHash());

        if (block == null) return new RpcMsg(JSONObject.NULL);

        AionTransaction tx = txInfo.getReceipt().getTransaction();

        if (tx == null) return new RpcMsg(JSONObject.NULL);

        JSONObject result = new JSONObject();
        result.put("timestampVal", block.getTimestamp());
        result.put("transactionHash", TypeConverter.toJsonHex(tx.getHash()));
        result.put("blockNumber", block.getNumber());
        result.put("blockHash", TypeConverter.toJsonHex(block.getHash()));
        result.put("nonce", TypeConverter.toJsonHex(tx.getNonce()));
        result.put("fromAddr", TypeConverter.toJsonHex(tx.getFrom().toBytes()));
        result.put("toAddr", TypeConverter.toJsonHex(tx.getTo().toBytes()));
        result.put("value", TypeConverter.toJsonHex(tx.getValue()));
        result.put("nrgPrice", tx.getNrgPrice());
        result.put("nrgConsumed", txInfo.getReceipt().getEnergyUsed());
        result.put("data", TypeConverter.toJsonHex(tx.getData()));
        result.put("transactionIndex", txInfo.getIndex());

        JSONArray logs = new JSONArray();
        for (Log l : txInfo.getReceipt().getLogInfoList()) {
            JSONObject log = new JSONObject();
            log.put("address", l.getAddress().toString());
            log.put("data", TypeConverter.toJsonHex(l.getData()));
            JSONArray topics = new JSONArray();
            for (byte[] topic : l.getTopics()) {
                topics.put(TypeConverter.toJsonHex(topic));
            }
            log.put("topics", topics);
            logs.put(log);
        }
        result.put("logs", logs);

        return new RpcMsg(result);
    }

    public RpcMsg ops_getBlock(Object _params) {
        String _bnOrHash;
        boolean _fullTx;
        if (_params instanceof JSONArray) {
            _bnOrHash = ((JSONArray)_params).get(0) + "";
            _fullTx = ((JSONArray)_params).optBoolean(1, false);
        }
        else if (_params instanceof JSONObject) {
            _bnOrHash = ((JSONObject)_params).get("block") + "";
            _fullTx = ((JSONObject)_params).optBoolean("fullTransaction", false);
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        AionBlock block = null;

        Long bn = this.parseBnOrId(_bnOrHash);

        // user passed a Long block number
        if (bn != null) {
            if (bn >= 0) {
                block = this.ac.getBlockchain().getBlockByNumber(bn);
                if (block == null) return new RpcMsg(JSONObject.NULL);
            } else {
                return new RpcMsg(JSONObject.NULL);
            }

        }

        // see if the user passed in a hash
        if (block == null) {
            block = this.ac.getBlockchain().getBlockByHash(ByteUtil.hexStringToBytes(_bnOrHash));
            if (block == null) return new RpcMsg(JSONObject.NULL);
        }

        AionBlock mainBlock = this.ac.getBlockchain().getBlockByNumber(block.getNumber());
        if (mainBlock == null) return new RpcMsg(JSONObject.NULL);

        if (!FastByteComparisons.equal(block.getHash(), mainBlock.getHash())) {
            return new RpcMsg(JSONObject.NULL);
        }

        // ok so now we have a mainchain block

        BigInteger blkReward = ((AionBlockchainImpl)ac.getBlockchain()).getChainConfiguration()
                .getRewardsCalculator().calculateReward(block.getHeader()) ;
        BigInteger totalDiff = this.ac.getAionHub().getBlockStore().getTotalDifficultyForHash(block.getHash());

        JSONObject blk = new JSONObject();
        blk.put("timestampVal", block.getTimestamp());
        blk.put("blockNumber", block.getNumber());
        blk.put("numTransactions", block.getTransactionsList().size());

        blk.put("blockHash", TypeConverter.toJsonHex(block.getHash()));
        blk.put("parentHash", TypeConverter.toJsonHex(block.getParentHash()));
        blk.put("minerAddress", TypeConverter.toJsonHex(block.getCoinbase().toBytes()));

        blk.put("receiptTxRoot", TypeConverter.toJsonHex(block.getReceiptsRoot()));
        blk.put("txTrieRoot", TypeConverter.toJsonHex(block.getTxTrieRoot()));
        blk.put("stateRoot", TypeConverter.toJsonHex(block.getStateRoot()));

        blk.put("difficulty", TypeConverter.toJsonHex(block.getDifficulty()));
        blk.put("totalDifficulty", totalDiff.toString(16));
        blk.put("nonce", TypeConverter.toJsonHex(block.getNonce()));

        blk.put("blockReward", blkReward);
        blk.put("nrgConsumed", block.getNrgConsumed());
        blk.put("nrgLimit", block.getNrgLimit());

        blk.put("size", block.size());
        blk.put("bloom", TypeConverter.toJsonHex(block.getLogBloom()));
        blk.put("extraData", TypeConverter.toJsonHex(block.getExtraData()));
        blk.put("solution", TypeConverter.toJsonHex(block.getHeader().getSolution()));

        JSONObject result = new JSONObject();
        result.put("blk", blk);

        if (_fullTx) {
            JSONArray txn = new JSONArray();
            for (AionTransaction tx : block.getTransactionsList()) {
                // transactionHash, fromAddr, toAddr, value, timestampVal, blockNumber, blockHash
                JSONArray t = new JSONArray();
                t.put(TypeConverter.toJsonHex(tx.getHash()));
                t.put(TypeConverter.toJsonHex(tx.getFrom().toBytes()));
                t.put(TypeConverter.toJsonHex(tx.getTo().toBytes()));
                t.put(TypeConverter.toJsonHex(tx.getValue()));
                t.put(block.getTimestamp());
                t.put(block.getNumber());

                txn.put(t);
            }
            result.put("txn", txn);
        }

        return new RpcMsg(result);
    }

    /* -------------------------------------------------------------------------
     * stratum pool
     */

    public RpcMsg stratum_getinfo() {
        JSONObject obj = new JSONObject();

        obj.put("balance", 0);
        obj.put("blocks", 0);
        obj.put("connections", peerCount());
        obj.put("proxy", "");
        obj.put("generate", true);
        obj.put("genproclimit", 100);
        obj.put("difficulty", 0);

        return new RpcMsg(obj);
    }

    public RpcMsg stratum_getwork() {
        // TODO: Change this to a synchronized map implementation mapping

        if (isSeedMode) {
            return new RpcMsg(null, RpcError.NOT_ALLOWED, "SeedNodeIsOpened");
        }

        BlockContext bestBlock = getBlockTemplate();
        ByteArrayWrapper key = new ByteArrayWrapper(bestBlock.block.getHeader().getMineHash());

        // Read template map; if block already contained chain has not moved forward, simply return the same block.
        boolean isContained = false;
        try {
            templateMapLock.readLock().lock();
            if(templateMap.containsKey(key)) {
                isContained = true;
            }
        } finally {
            templateMapLock.readLock().unlock();
        }

        // Template not present in map; add it before returning
        if(!isContained) {
            try{
                templateMapLock.writeLock().lock();

                // Deep copy best block to avoid modifying internal best blocks
                bestBlock = new BlockContext(bestBlock);

                if (!templateMap.keySet().isEmpty()) {
                    if (templateMap.get(templateMap.keySet().iterator().next()).getNumber() < bestBlock.block.getNumber()) {
                        // Found a higher block, clear any remaining cached entries and start on new height
                        templateMap.clear();
                    }
                }
                templateMap.put(key, bestBlock.block);

            }finally {
                templateMapLock.writeLock().unlock();
            }
        }

        JSONObject obj = new JSONObject();
        obj.put("previousblockhash", toHexString(bestBlock.block.getParentHash()));
        obj.put("height", bestBlock.block.getNumber());
        obj.put("target", toHexString(bestBlock.block.getHeader().getPowBoundary()));
        obj.put("headerHash", toHexString(bestBlock.block.getHeader().getMineHash()));
        obj.put("blockBaseReward", toHexString(bestBlock.baseBlockReward.toByteArray()));
        obj.put("blockTxFee", toHexString(bestBlock.transactionFee.toByteArray()));

        return new RpcMsg(obj);
    }

    public RpcMsg stratum_dumpprivkey() {
        return new RpcMsg("");
    }

    public RpcMsg stratum_validateaddress(Object _params) {
        /*
         * "isvalid" : true|false, (boolean) If the address is valid or not.
         * "address", (string) The aion address validated to ensure address is valid
         * address "ismine" : true|false, (boolean) If the address is contained in the keystore.
         */
        String _address;
        if (_params instanceof JSONArray) {
            _address = ((JSONArray)_params).get(0) + "";
        }
        else if (_params instanceof JSONObject) {
            _address = ((JSONObject)_params).get("address") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        JSONObject obj = new JSONObject();

        obj.put("isvalid", Utils.isValidAddress(_address));
        obj.put("address", _address + "");
        obj.put("ismine", Keystore.exist(_address));
        return new RpcMsg(obj);
    }


    public RpcMsg stratum_getdifficulty() {
        /*
        * Return the highest known difficulty
         */
        return new RpcMsg(getBestBlock().getDifficultyBI().toString(16));
    }

    public RpcMsg stratum_getmininginfo() {
        AionBlock bestBlock = getBestBlock();

        JSONObject obj = new JSONObject();
        obj.put("blocks", bestBlock.getNumber());
        obj.put("currentblocksize", bestBlock.getEncoded().length);
        obj.put("currentblocktx", bestBlock.getTransactionsList().size());
        obj.put("difficulty", bestBlock.getDifficultyBI().toString(16));
        obj.put("testnet", true);

        return new RpcMsg(obj);
    }

    public RpcMsg stratum_submitblock(Object _params) {
        Object nce;
        Object soln;
        Object hdrHash;
        if (_params instanceof JSONArray) {
            nce = ((JSONArray)_params).opt(0);
            soln = ((JSONArray)_params).opt(1);
            hdrHash = ((JSONArray)_params).opt(2);
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        JSONObject obj = new JSONObject();

        if (nce != null && soln != null && hdrHash != null &&
                !nce.equals(JSONObject.NULL) && !soln.equals(JSONObject.NULL) && !hdrHash.equals(JSONObject.NULL)) {

            try {
                templateMapLock.writeLock().lock();

                ByteArrayWrapper key = new ByteArrayWrapper(hexStringToBytes((String) hdrHash));

                // Grab copy of best block
                AionBlock bestBlock = templateMap.get(key);
                if (bestBlock != null) {
                    bestBlock.getHeader().setSolution(hexStringToBytes(soln + ""));
                    bestBlock.getHeader().setNonce(hexStringToBytes(nce + ""));

                    // Directly submit to chain for new due to delays using event, explore event submission again
                    ImportResult importResult = AionImpl.inst().addNewMinedBlock(bestBlock);
                    if(importResult.isSuccessful()) {
                        templateMap.remove(key);
                        LOG.info("block submitted via api <num={}, hash={}, diff={}, tx={}>", bestBlock.getNumber(),
                                bestBlock.getShortHash(), // LogUtil.toHexF8(newBlock.getHash()),
                                bestBlock.getHeader().getDifficultyBI().toString(), bestBlock.getTransactionsList().size());
                    } else {
                        LOG.info("Unable to submit block via api <num={}, hash={}, diff={}, tx={}>", bestBlock.getNumber(),
                                bestBlock.getShortHash(), // LogUtil.toHexF8(newBlock.getHash()),
                                bestBlock.getHeader().getDifficultyBI().toString(), bestBlock.getTransactionsList().size());
                    }
                }
            } finally {
                templateMapLock.writeLock().unlock();
            }

            // TODO: Simplified response for now, need to provide better feedback to caller in next update
            obj.put("result", true);
        } else {
            obj.put("message", "success");
            obj.put("code", -1);
        }

        return new RpcMsg(obj);
    }

    public RpcMsg stratum_getHeaderByBlockNumber(Object _params) {
        Object _blockNum;
        if (_params instanceof JSONArray) {
            _blockNum = ((JSONArray)_params).opt(0);
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        JSONObject obj = new JSONObject();

        if (_blockNum != null && !_blockNum.equals(JSONObject.NULL)) {
            String bnStr = _blockNum + "";
            try {
                int bnInt = Integer.decode(bnStr);
                AionBlock block = getBlockRaw(bnInt);
                if (block != null) {
                    A0BlockHeader header = block.getHeader();
                    obj.put("code", 0); // 0 = success
                    obj.put("nonce", toHexString(header.getNonce()));
                    obj.put("solution", toHexString(header.getSolution()));
                    obj.put("headerHash", toHexString(header.getMineHash()));
                    obj.putOpt("blockHeader", header.toJSON());
                } else {
                    obj.put("message", "Fail - Unable to find block" + bnStr);
                    obj.put("code", -2);
                }
            } catch (Exception e) {
                obj.put("message", bnStr + " must be an integer value");
                obj.put("code", -3);
            }
        } else {
            obj.put("message", "Missing block number");
            obj.put("code", -1);
        }

        return new RpcMsg(obj);
    }

    // always gets the latest 20 blocks and transactions
    private class MinerStatsView {
        LinkedList<byte[]> hashQueue; // more precisely a dequeue
        Map<byte[], AionBlock> blocks;
        private JSONObject response;
        private int qSize;
        private byte[] miner;

        MinerStatsView(MinerStatsView cv) {
            hashQueue = new LinkedList<>(cv.hashQueue);
            blocks = new HashMap<>(cv.blocks);
            response = new JSONObject(cv.response, JSONObject.getNames(cv.response));
            qSize = cv.qSize;
            miner = cv.miner;
        }

        MinerStatsView(int _qSize, byte[] _miner) {
            hashQueue = new LinkedList<>();
            blocks = new HashMap<>();
            response = new JSONObject();
            qSize = _qSize;
            miner = _miner;
        }

        private JSONObject buildResponse() {
            BigInteger lastDifficulty = BigInteger.ZERO;
            long blkTimeAccumulator = 0;
            long minedCount = 0L;

            int minedByMiner = 0;

            double minerHashrateShare = 0;
            BigDecimal minerHashrate = BigDecimal.ZERO;
            BigDecimal networkHashrate = BigDecimal.ZERO;

            int blkTimesAccumulated = 0;
            Long lastBlkTimestamp = null;
            AionBlock b = null;

            try {
                // index 0 = latest block
                int i = 0;
                ListIterator li = hashQueue.listIterator(0);
                while(li.hasNext()) {
                    byte[] hash = (byte[]) li.next();
                    b = blocks.get(hash);

                    if (i == 0)
                        lastDifficulty = b.getDifficultyBI();

                    // only accumulate block times over the last 32 blocks
                    if (i <= STRATUM_BLKTIME_INCLUDED_COUNT) {
                        if (lastBlkTimestamp != null) {
                            System.out.println("blocktime for [" +  b.getNumber() + "] = " + (lastBlkTimestamp - b.getTimestamp()));
                            blkTimeAccumulator += lastBlkTimestamp - b.getTimestamp();
                            blkTimesAccumulated++;
                        }
                        lastBlkTimestamp = b.getTimestamp();
                    }

                    if (FastByteComparisons.equal(b.getCoinbase().toBytes(), miner)) {
                        minedByMiner++;
                    }

                    i++;
                }

                double blkTime = 0L;
                if (blkTimesAccumulated > 0) {
                    blkTime = blkTimeAccumulator / (double) blkTimesAccumulated;
                }

                if (blkTime > 0) {
                    networkHashrate = (new BigDecimal(lastDifficulty)).divide(BigDecimal.valueOf(blkTime), 4, RoundingMode.HALF_UP);
                }

                if (i > 0) {
                    minerHashrateShare =  minedByMiner / (double) i;
                }

                minerHashrate = BigDecimal.valueOf(minerHashrateShare).multiply(networkHashrate);

            } catch (Throwable t) {
                LOG.error("failed to compute miner metrics", t);
            }

            JSONObject o = new JSONObject();
            o.put("networkHashrate", networkHashrate.toString());
            o.put("minerHashrate", minerHashrate.toString());
            o.put("minerHashrateShare", minerHashrateShare);
            return o;
        }

        MinerStatsView update() {
            // get the latest head
            AionBlock blk = getBestBlock();

            if (blk == null) return this;

            if (FastByteComparisons.equal(hashQueue.peekFirst(), blk.getHash())) {
                return this; // nothing to do
            }

            // evict data as necessary
            LinkedList<Map.Entry<byte[],AionBlock>> tempStack = new LinkedList<>();
            tempStack.push(Map.entry(blk.getHash(), blk));
            int itr = 1; // deliberately 1, since we've already added the 0th element to the stack

            /*
            if (hashQueue.peekFirst() != null) {
                System.out.println("[" + 0 + "]: " + TypeConverter.toJsonHex(hashQueue.peekFirst()) + " - " + blocks.get(hashQueue.peekFirst()).getNumber());
                System.out.println("----------------------------------------------------------");
                System.out.println("isParentHashMatch? " + FastByteComparisons.equal(hashQueue.peekFirst(), blk.getParentHash()));
                System.out.println("blk.getNumber() " + blk.getNumber());
            }
            System.out.println("blkNum: " + blk.getNumber() +
                    " parentHash: " + TypeConverter.toJsonHex(blk.getParentHash()) +
                    " blkHash: " + TypeConverter.toJsonHex(blk.getHash()));
            */

            while(!FastByteComparisons.equal(hashQueue.peekFirst(), blk.getParentHash())
                    && itr < qSize
                    && blk.getNumber() > 2) {

                blk = getBlockByHash(blk.getParentHash());
                tempStack.push(Map.entry(blk.getHash(), blk));
                itr++;
                /*
                System.out.println("blkNum: " + blk.getNumber() +
                        " parentHash: " + TypeConverter.toJsonHex(blk.getParentHash()) +
                        " blkHash: " + TypeConverter.toJsonHex(blk.getHash()));
                */
            }

            // evict out the right number of elements first
            for (int i = 0; i < tempStack.size(); i++) {
                byte[] tailHash = hashQueue.pollLast();
                if (tailHash != null) {
                    blocks.remove(tailHash);
                }
            }

            // empty out the stack into the queue
            while (!tempStack.isEmpty()) {
                // add to the queue
                Map.Entry<byte[], AionBlock> element = tempStack.pop();
                byte[] hash = element.getKey();
                AionBlock blkObj = element.getValue();

                hashQueue.push(hash);
                blocks.put(hash, blkObj);
            }

            /*
            System.out.println("[" + 0 + "]: " + TypeConverter.toJsonHex(hashQueue.peekFirst()) + " - " + blocks.get(hashQueue.peekFirst()).getNumber());
            System.out.println("----------------------------------------------------------");
            for (int i = hashQueue.size() - 1; i >= 0; i--) {
                System.out.println("[" + i + "]: " + TypeConverter.toJsonHex(hashQueue.get(i)) + " - " + blocks.get(hashQueue.get(i)).getNumber());
            }
            */
            this.response = buildResponse();

            return this;
        }

        JSONObject getResponse() {
            return response;
        }
    }

    public class MinerStatsThreadFactory implements ThreadFactory {
        private final AtomicInteger tnum = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "miner-stats-" + tnum.getAndIncrement());
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        }
    }

    public RpcMsg stratum_getMinerStats(Object _params) {
        String _address;
        if (_params instanceof JSONArray) {
            _address = ((JSONArray)_params).get(0) + "";
        }
        else if (_params instanceof JSONObject) {
            _address = ((JSONObject)_params).get("address") + "";
        }
        else {
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid parameters");
        }

        try {
            MinerStatsView v = MinerStats.get(_address);
            return new RpcMsg(v.getResponse());
        } catch (Exception e) {
            LOG.error("<rpc-server - cannot get cached response for stratum_getMinerStats: ", e);
            return new RpcMsg(null, RpcError.EXECUTION_ERROR, "Cached response retrieve failed.");
        }
    }

    // --------------------------------------------------------------------
    // Helper Functions
    // --------------------------------------------------------------------
    /*
    // potential bug introduced by .getSnapshotTo()
    // comment out until resolved
    private IRepository getRepoByJsonBlockId(String _bnOrId) {
        Long bn = parseBnOrId(_bnOrId);
        // if you passed in an invalid bnOrId, pending or it's an error
        if (bn == null || bn < 0) return null;

        AionBlock b = this.ac.getBlockchain().getBlockByNumber(bn);
        if (b == null) return null;

        return ac.getRepository().getSnapshotTo(b.getStateRoot());
    }
    */
    private Long parseBnOrId(String _bnOrId) {
        if (_bnOrId == null)
            return null;

        try
        {
            if ("earliest".equalsIgnoreCase(_bnOrId)) {
                return 0L;
            } else if ("latest".equalsIgnoreCase(_bnOrId)) {
                return getBestBlock().getNumber();
            } else if ("pending".equalsIgnoreCase(_bnOrId)) {
                return -1L;
            } else {
                if (_bnOrId.startsWith("0x")) {
                    return TypeConverter.StringHexToBigInteger(_bnOrId).longValue();
                } else {
                    return Long.parseLong(_bnOrId);
                }
            }
        } catch (Exception e) {
            LOG.debug("err on parsing block number #" + _bnOrId);
            return null;
        }
    }

    public void shutdown() {
        if(isFilterEnabled)
            shutDownES();
    }
}
