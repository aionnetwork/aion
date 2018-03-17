/*******************************************************************************
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

package org.aion.api.server.http;

import com.sun.xml.internal.ws.api.ha.StickyFeature;
import org.aion.api.server.ApiAion;
import org.aion.api.server.IRpc;
import org.aion.api.server.types.*;
import org.aion.base.db.IRepository;
import org.aion.base.type.*;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.TypeConverter;
import org.aion.crypto.ECKey;
import org.aion.crypto.HashUtil;
import org.aion.equihash.Solution;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallbackA0;
import org.aion.evtmgr.impl.evt.EventTx;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.vm.types.DataWord;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.collections4.map.LRUMap;
import org.json.JSONArray;
import org.json.JSONObject;
import org.libsodium.jni.crypto.Util;
import org.omg.CosNaming.NamingContextExtPackage.StringNameHelper;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class ApiWeb3Aion extends ApiAion implements IRpc {

    ApiWeb3Aion(final IAionChain _ac) {
        super(_ac);
        this.pendingReceipts = Collections.synchronizedMap(new LRUMap<>(FLTRS_MAX, 100));

        IHandler blkHr = this.ac.getAionHub().getEventMgr().getHandler(IHandler.TYPE.BLOCK0.getValue());
        if (blkHr != null) {
            blkHr.eventCallback(new EventCallbackA0<IBlock, ITransaction, ITxReceipt, IBlockSummary, ITxExecSummary, ISolution>() {
                public void onBlock(final IBlockSummary _bs) {
                    System.out.println("onBlock event");
                    AionBlockSummary bs = (AionBlockSummary) _bs;
                    installedFilters.keySet().forEach((k) -> {
                        Fltr f = installedFilters.get(k);
                        if (f.isExpired()) {
                            LOG.debug("<Filter: expired, key={}>", k);
                            installedFilters.remove(k);
                        } else if (f.onBlock(bs)) {
                            LOG.debug("<Filter: append, onBlock type={} blk#={}>", f.getType().name(), bs.getBlock().getNumber());
                        }
                    });
                }
            });
        }

        IHandler txHr = this.ac.getAionHub().getEventMgr().getHandler(IHandler.TYPE.TX0.getValue());
        if (txHr != null) {
            txHr.eventCallback(new EventCallbackA0<IBlock, ITransaction, ITxReceipt, IBlockSummary, ITxExecSummary, ISolution>() {

                public void onPendingTxUpdate(final ITxReceipt _txRcpt, final EventTx.STATE _state, final IBlock _blk) {
                    ByteArrayWrapper txHashW = new ByteArrayWrapper(((AionTxReceipt) _txRcpt).getTransaction().getHash());
                    if (_state.isPending() || _state == EventTx.STATE.DROPPED0) {
                        pendingReceipts.put(txHashW, (AionTxReceipt) _txRcpt);
                    } else {
                        pendingReceipts.remove(txHashW);
                    }
                }

                public void onPendingTxReceived(ITransaction _tx) {
                    // not absolutely neccessary to do eviction on installedFilters here, since we're doing it already
                    // in the onBlock event. eviction done here "just in case ..."
                    installedFilters.keySet().forEach((k) -> {
                        Fltr f = installedFilters.get(k);
                        if (f.isExpired()) {
                            LOG.debug("<filter expired, key={}>", k);
                            installedFilters.remove(k);
                        } else if(f.onTransaction(_tx)) {
                            LOG.info("<filter append, onPendingTransaction fltrSize={} type={} txHash={}>", f.getSize(), f.getType().name(), TypeConverter.toJsonHex(_tx.getHash()));
                        }
                    });
                }
            });
        }
    }

    // --------------------------------------------------------------------
    // Mining Pool
    // --------------------------------------------------------------------

    /* Return a reference to the AIONBlock without converting values to hex
     * Requied for the mining pool implementation
     */
    AionBlock getBlockRaw(int bn) {
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

    // AION Mining Pool
    // TODO Test multiple threads submitting blocks
    synchronized boolean submitBlock(Solution solution) {

        AionBlock block = (AionBlock) solution.getBlock();

        // set the nonce and solution
        block.getHeader().setNonce(solution.getNonce());
        block.getHeader().setSolution(solution.getSolution());

        // This can be improved
        return (AionImpl.inst().addNewMinedBlock(block)).isSuccessful();
    }

    // --------------------------------------------------------------------
    // Ethereum-Compliant JSON RPC Specification Implementation
    // --------------------------------------------------------------------

    public Object web3_clientVersion() {
        return clientVersion();
    }

    public Object web3_sha3(String _data) {
        return TypeConverter.toJsonHex(HashUtil.keccak256(ByteUtil.hexStringToBytes(_data)));
    }

    public Object net_version() {
        return chainId();
    }

    public Object net_peerCount() {
        return peerCount();
    }

    public Object net_listening() {
        return true;
    }

    public Object eth_protocolVersion() {
        return p2pProtocolVersion();
    }

    public Object eth_syncing() {
        SyncInfo syncInfo = getSync();
        if (!syncInfo.done) {
            JSONObject obj = new JSONObject();
            // create obj for when syncing is completed
            obj.put("startingBlock", new NumericalValue(syncInfo.chainStartingBlkNumber).toHexString());
            obj.put("currentBlock", new NumericalValue(syncInfo.chainBestBlkNumber).toHexString());
            obj.put("highestBlock", new NumericalValue(syncInfo.networkBestBlkNumber).toHexString());
            obj.put("importMax", new NumericalValue(syncInfo.blksImportMax).toHexString());
            return obj;
        } else {
            // create obj for when syncing is ongoing
            return false;
        }
    }

    public Object eth_coinbase() {
        return getCoinbase();
    }

    public Object eth_mining() {
        return isMining();
    }

    public Object eth_hashrate() {
        return getHashrate();
    }

    public Object eth_submitHashrate(String hashrate, String clientId) {
        return setReportedHashrate(hashrate, clientId);
    }

    public Object eth_gasPrice() {
        return TypeConverter.toJsonHex(getRecommendedNrgPrice());
    }

    public Object eth_accounts() {
        return new JSONArray(getAccounts());
    }

    public Object eth_blockNumber() {
        return getBestBlock().getNumber();
    }

    public Object eth_getBalance(String _address, Object _bnOrId) throws Exception {
        Address address = new Address(_address);

        String bnOrId = "latest";
        if (_bnOrId != null)
            bnOrId = _bnOrId + "";

        BigInteger balance = getRepoByJsonBlockId(bnOrId).getBalance(address);
        return TypeConverter.toJsonHex(balance);
    }

    public Object eth_getStorageAt(String _address, String _storageIndex, Object _bnOrId) {
        Address address = new Address(_address);

        String bnOrId = "latest";
        if (_bnOrId != null)
            bnOrId = _bnOrId + "";

        DataWord key = DataWord.ZERO;

        try {
            key = new DataWord(ByteUtil.hexStringToBytes(_storageIndex));
        } catch (Exception e) {
            // invalid key
            LOG.debug("eth_getStorageAt: invalid storageIndex. Must be <= 16 bytes.");
            return null;
        }

        DataWord storageValue = (DataWord) getRepoByJsonBlockId(bnOrId).getStorageValue(address, key);
        return storageValue != null ? TypeConverter.toJsonHex(storageValue.getData()) : null;
    }

    public Object eth_getTransactionCount(String _address, Object _bnOrId) {
        Address address = new Address(_address);

        String bnOrId = "latest";
        if (_bnOrId != null)
            bnOrId = _bnOrId + "";

        return TypeConverter.toJsonHex(getRepoByJsonBlockId(bnOrId).getNonce(address));
    }

    public Object eth_getBlockTransactionCountByHash(String hashString) {
        byte[] hash = ByteUtil.hexStringToBytes(hashString);
        AionBlock b = this.ac.getBlockchain().getBlockByHash(hash);
        if (b == null) return null;
        long n = b.getTransactionsList().size();
        return TypeConverter.toJsonHex(n);
    }

    public Object eth_getBlockTransactionCountByNumber(String bnOrId) {
        List<AionTransaction> list = getTransactionsByBlockId(bnOrId);
        if (list == null) return null;
        long n = list.size();
        return TypeConverter.toJsonHex(n);
    }

    public Object eth_getCode(String _address, Object _bnOrId) {
        Address address = new Address(_address);

        String bnOrId = "latest";
        if (_bnOrId != null)
            bnOrId = _bnOrId + "";

        IRepository repo = getRepoByJsonBlockId(bnOrId);
        if (repo == null) return null; // invalid bnOrId

        byte[] code = repo.getCode(address);
        return TypeConverter.toJsonHex(code);
    }

    public Object eth_sign(String _address, String _message) {
        Address address = Address.wrap(_address);
        ECKey key = getAccountKey(address.toString());
        if (key == null)
            return null;

        // Message starts with Unicode Character 'END OF MEDIUM' (U+0019)
        String message = "\u0019Aion Signed Message:\n" + _message.length() + _message;
        byte[] messageHash = HashUtil.keccak256(message.getBytes());

        String signature = TypeConverter.toJsonHex(key.sign(messageHash).getSignature());

        return signature;
    }

    public Object eth_sendTransaction(JSONObject _tx) {
        ArgTxCall txParams = ArgTxCall.fromJSON(_tx, getRecommendedNrgPrice(), getDefaultNrgLimit());

        // check for unlocked account
        Address address = txParams.getFrom();
        ECKey key = getAccountKey(address.toString());
        // TODO: send json-rpc error code + message
        if (key == null)
            return null;

        if (txParams != null) {
            byte[] response = sendTransaction(txParams);
            return TypeConverter.toJsonHex(response);
        }

        return null;
    }

    public Object eth_sendRawTransaction(String rawHexString) {
        if (rawHexString == null)
            return null;

        byte[] rawTransaction = ByteUtil.hexStringToBytes(rawHexString);
        byte[] transactionHash = sendTransaction(rawTransaction);

        return TypeConverter.toJsonHex(transactionHash);
    }

    public Object eth_call(JSONObject _tx, Object _bnOrId) {
        ArgTxCall txParams = ArgTxCall.fromJSON(_tx, getRecommendedNrgPrice(), getDefaultNrgLimit());

        String bnOrId = "latest";
        if (_bnOrId != null)
            bnOrId = _bnOrId + "";

        Long bn = parseBnOrId(bnOrId);
        if (bn == null || bn < 0) return null;

        AionTransaction tx = new AionTransaction(
                txParams.getNonce().toByteArray(),
                txParams.getTo(),
                txParams.getValue().toByteArray(),
                txParams.getData(),
                txParams.getNrg(),
                txParams.getNrgPrice());

        AionBlock b = this.ac.getBlockchain().getBlockByNumber(bn);
        AionTxReceipt receipt = this.ac.callConstant(tx, b);

        return TypeConverter.toJsonHex(receipt.getExecutionResult());
    }

    public Object eth_estimateGas(JSONObject tx) {
        ArgTxCall txParams = ArgTxCall.fromJSON(tx, getRecommendedNrgPrice(), getDefaultNrgLimit());
        NumericalValue estimate = new NumericalValue(estimateGas(txParams));

        return estimate.toHexString();
    }

    public Object eth_getBlockByHash(String hashString, boolean fullTransactions) {
        byte[] hash = ByteUtil.hexStringToBytes(hashString);
        AionBlock block = this.ac.getBlockchain().getBlockByHash(hash);
        BigInteger totalDiff = this.ac.getAionHub().getBlockStore().getTotalDifficultyForHash(hash);

        if (block == null) {
            LOG.debug("<get-block bn={} err=not-found>");
            return null;
        } else {
            try {
                return Blk.AionBlockToJson(block, totalDiff, fullTransactions);
            } catch (Exception ex) {
                if (LOG.isDebugEnabled())
                    LOG.debug("<get-block bh={} err=exception>", hashString);
                return null;
            }
        }
    }

    public Object eth_getBlockByNumber(String _bnOrId, boolean _fullTx) {
        Long bn = this.parseBnOrId(_bnOrId);

        if (bn == null || bn < 0)
            return null;

        AionBlock nb = this.ac.getBlockchain().getBlockByNumber(bn);

        if (nb == null) {
            LOG.debug("<get-block bn={} err=not-found>");
            return null;
        } else {
            BigInteger totalDiff = this.ac.getAionHub().getBlockStore().getTotalDifficultyForHash(nb.getHash());
            return Blk.AionBlockToJson(nb, totalDiff, _fullTx);
        }
    }

    public Object eth_getTransactionByHash(String _txHash) {
        byte[] txHash = ByteUtil.hexStringToBytes(_txHash);
        if (_txHash == null || txHash == null) return null;

        AionTxInfo txInfo = this.ac.getAionHub().getBlockchain().getTransactionInfo(txHash);
        if (txInfo == null) return null;

        return Tx.InfoToJSON(txInfo);
    }

    public Object eth_getTransactionByBlockHashAndIndex(String _blockHash,String _index) {
        byte[] hash = ByteUtil.hexStringToBytes(_blockHash);
        if (_blockHash == null || hash == null) return null;

        AionBlock b = this.ac.getBlockchain().getBlockByHash(hash);
        if (b == null) return null;

        int idx = Integer.decode(_index);
        if (idx >= b.getTransactionsList().size()) return null;

        return Tx.AionTransactionToJSON(b.getTransactionsList().get(idx), idx);
    }

    public Object eth_getTransactionByBlockNumberAndIndex(String _bnOrId, String _index) throws Exception {
        List<AionTransaction> txs = getTransactionsByBlockId(_bnOrId);
        if (txs == null) return null;

        int idx = Integer.decode(_index);
        if (idx >= txs.size()) return null;

        return Tx.AionTransactionToJSON(txs.get(idx), idx);
    }

    public Object eth_getTransactionReceipt(String txHash) {
        TxRecpt r = this.getTransactionReceipt(TypeConverter.StringHexToByteArray(txHash));
        if (r == null) return null;
        return r.toJson();
    }

    /* -------------------------------------------------------------------------
     * compiler
     */

    public Object eth_getCompilers() {
        return new JSONArray(this.compilers);
    }

    public Object eth_compileSolidity(String _contract) {
        @SuppressWarnings("unchecked")
        Map<String, CompiledContr> compiled = contract_compileSolidity(_contract);
        JSONObject obj = new JSONObject();
        for (String key : compiled.keySet()) {
            CompiledContr cc = compiled.get(key);
            obj.put(key, cc.toJSON());
        }
        return obj;
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
    public String eth_newFilter(final ArgFltr rf) {
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

        // "install" the filter after populating historical data;
        // rationale: until the user gets the id back, the user should not expect the filter to be "installed" anyway.
        long id = fltrIndex.getAndIncrement();
        installedFilters.put(id, filter);

        return TypeConverter.toJsonHex(id);
    }

    public String eth_newBlockFilter() {
        long id = fltrIndex.getAndIncrement();
        installedFilters.put(id, new FltrBlk());
        return TypeConverter.toJsonHex(id);
    }

    public String eth_newPendingTransactionFilter() {
        long id = fltrIndex.getAndIncrement();
        installedFilters.put(id, new FltrTx());
        return TypeConverter.toJsonHex(id);
    }

    public boolean eth_uninstallFilter(String id) {
        return id != null && installedFilters.remove(TypeConverter.StringHexToBigInteger(id).longValue()) != null;
    }

    public Object eth_getFilterChanges(final String _id) {
        if (_id == null)
            return null;

        long id = TypeConverter.StringHexToBigInteger(_id).longValue();
        Fltr filter = installedFilters.get(id);

        if (filter == null) return null;

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

    public Object eth_getFilterLogs(final String _id) {
        return eth_getFilterChanges(_id);
    }

    public Object eth_getLogs(final ArgFltr rf) {
        String id = eth_newFilter(rf);
        Object response = eth_getFilterChanges(id);
        eth_uninstallFilter(id);
        return response;
    }

    /* -------------------------------------------------------------------------
     * personal
     */

    public Object personal_unlockAccount() {
        String account = (String) params.get(0);
        String password = (String) params.get(1);
        int duration = new BigInteger(params.get(2).equals(null) ? "300" : params.get(2) + "").intValue();
        return processResult(_id, api.unlockAccount(account, password, duration));
    }

    /* -------------------------------------------------------------------------
     * debug
     */

    public Object debug_getBlocksByNumber(String _bnOrId, boolean _fullTransactions) {
        Long bn = parseBnOrId(_bnOrId);

        if (bn == null || bn < 0)
            return null;

        List<Map.Entry<AionBlock, Map.Entry<BigInteger, Boolean>>> blocks = ((AionBlockStore) this.ac.getAionHub().getBlockchain().getBlockStore()).getBlocksByNumber(bn);
        if (blocks == null) {
            LOG.debug("<get-block bn={} err=not-found>");
            return null;
        }

        JSONArray response = new JSONArray();
        for (Map.Entry<AionBlock, Map.Entry<BigInteger, Boolean>> block : blocks) {
            JSONObject b = (JSONObject) Blk.AionBlockToJson(block.getKey(), block.getValue().getKey(), _fullTransactions);
            b.put("mainchain", block.getValue().getValue());
            response.put(b);
        }
        return response;
    }

    // --------------------------------------------------------------------
    // Helper Functions
    // --------------------------------------------------------------------

    private IRepository getRepoByJsonBlockId(String _bnOrId) {
        Long bn = parseBnOrId(_bnOrId);
        // if you passed in an invalid bnOrId, pending or it's an error
        if (bn == null || bn < 0) return null;

        AionBlock b = this.ac.getBlockchain().getBlockByNumber(bn);
        if (b == null) return null;

        return ac.getRepository().getSnapshotTo(b.getStateRoot());
    }

    private List<AionTransaction> getTransactionsByBlockId(String id) {
        Long bn = parseBnOrId(id);
        if (bn == null || bn < 0) return null;

        AionBlock b = this.ac.getBlockchain().getBlockByNumber(bn);
        if (b == null) return null;

        return b.getTransactionsList();
    }

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
}
