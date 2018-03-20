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

import org.aion.api.server.ApiAion;
import org.aion.api.server.IRpc;
import org.aion.api.server.nrgprice.NrgOracle;
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
import org.aion.mcf.vm.types.DataWord;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.collections4.map.LRUMap;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.aion.base.util.ByteUtil.hexStringToBytes;
import static org.aion.base.util.ByteUtil.numBytes;
import static org.aion.base.util.ByteUtil.toHexString;

final class ApiWeb3Aion extends ApiAion implements IRpc {

    // TODO: Verify currentMining will follow kernel block generation schedule.
    // private static AtomicReference<AionBlock> currentMining;
    // TODO: Verify if need to use a concurrent map; locking may allow for use
    // of a simple map
    private static HashMap<String, AionBlock> templateMap;
    private static ReadWriteLock templateMapLock;

    ApiWeb3Aion(final IAionChain _ac) {
        super(_ac);
        pendingReceipts = Collections.synchronizedMap(new LRUMap<>(FLTRS_MAX, 100));
        templateMap = new HashMap<>();
        templateMapLock = new ReentrantReadWriteLock();


        // Fill data on block and transaction events into the filters and pending receipts
        IHandler blkHr = this.ac.getAionHub().getEventMgr().getHandler(IHandler.TYPE.BLOCK0.getValue());
        if (blkHr != null) {
            blkHr.eventCallback(new EventCallbackA0<IBlock, ITransaction, ITxReceipt, IBlockSummary, ITxExecSummary, ISolution>() {
                public void onBlock(final IBlockSummary _bs) {
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

        // instantiate nrg price oracle
        IAionBlockchain bc = (IAionBlockchain)_ac.getBlockchain();
        IHandler hldr = _ac.getAionHub().getEventMgr().getHandler(IHandler.TYPE.BLOCK0.getValue());
        long nrgPriceDefault = CfgAion.inst().getApi().getNrg().getNrgPriceDefault();
        long nrgPriceMax = CfgAion.inst().getApi().getNrg().getNrgPriceMax();
        this.nrgOracle = new NrgOracle(bc, hldr, nrgPriceDefault, nrgPriceMax);
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
        block.getHeader().setTimestamp(solution.getTimeStamp());

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

    public Object eth_submitHashrate(String _hashrate, String _clientId) {
        return setReportedHashrate(_hashrate, _clientId);
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
        if (_bnOrId != null && !_bnOrId.equals(null))
            bnOrId = _bnOrId + "";

        BigInteger balance = getRepoByJsonBlockId(bnOrId).getBalance(address);
        return TypeConverter.toJsonHex(balance);
    }

    public Object eth_getStorageAt(String _address, String _storageIndex, Object _bnOrId) {
        Address address = new Address(_address);

        String bnOrId = "latest";
        if (_bnOrId != null && !_bnOrId.equals(null))
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
        if (_bnOrId != null && !_bnOrId.equals(null))
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

    public Object eth_getBlockTransactionCountByNumber(String _bnOrId) {
        List<AionTransaction> list = getTransactionsByBlockId(_bnOrId);
        if (list == null) return null;
        long n = list.size();
        return TypeConverter.toJsonHex(n);
    }

    public Object eth_getCode(String _address, Object _bnOrId) {
        Address address = new Address(_address);

        String bnOrId = "latest";
        if (_bnOrId != null && !_bnOrId.equals(null))
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

    public Object eth_sendRawTransaction(String _rawTx) {
        if (_rawTx == null)
            return null;

        byte[] rawTransaction = ByteUtil.hexStringToBytes(_rawTx);
        byte[] transactionHash = sendTransaction(rawTransaction);

        return TypeConverter.toJsonHex(transactionHash);
    }

    public Object eth_call(JSONObject _tx, Object _bnOrId) {
        ArgTxCall txParams = ArgTxCall.fromJSON(_tx, getRecommendedNrgPrice(), getDefaultNrgLimit());

        String bnOrId = "latest";
        if (_bnOrId != null && !_bnOrId.equals(null))
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

    public Object eth_getBlockByHash(String _hashString, boolean _fullTx) {
        byte[] hash = ByteUtil.hexStringToBytes(_hashString);
        AionBlock block = this.ac.getBlockchain().getBlockByHash(hash);
        BigInteger totalDiff = this.ac.getAionHub().getBlockStore().getTotalDifficultyForHash(hash);

        if (block == null) {
            LOG.debug("<get-block bn={} err=not-found>");
            return null;
        } else {
            try {
                return Blk.AionBlockToJson(block, totalDiff, _fullTx);
            } catch (Exception ex) {
                if (LOG.isDebugEnabled())
                    LOG.debug("<get-block bh={} err=exception>", _hashString);
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

        AionBlock b = this.ac.getBlockchain().getBlockByHash(txInfo.getBlockHash());
        if (b == null) return null;

        return Tx.InfoToJSON(txInfo, b);
    }

    public Object eth_getTransactionByBlockHashAndIndex(String _blockHash,String _index) {
        byte[] hash = ByteUtil.hexStringToBytes(_blockHash);
        if (_blockHash == null || hash == null) return null;

        AionBlock b = this.ac.getBlockchain().getBlockByHash(hash);
        if (b == null) return null;

        List<AionTransaction> txs = b.getTransactionsList();

        int idx = Integer.decode(_index);
        if (idx >= txs.size()) return null;

        return Tx.AionTransactionToJSON(txs.get(idx), b, idx);
    }

    public Object eth_getTransactionByBlockNumberAndIndex(String _bnOrId, String _index) {
        Long bn = parseBnOrId(_bnOrId);
        if (bn == null || bn < 0) return null;

        AionBlock b = this.ac.getBlockchain().getBlockByNumber(bn);
        if (b == null) return null;

        List<AionTransaction> txs = b.getTransactionsList();

        int idx = Integer.decode(_index);
        if (idx >= txs.size()) return null;

        return Tx.AionTransactionToJSON(txs.get(idx), b, idx);
    }

    public Object eth_getTransactionReceipt(String _txHash) {
        byte[] txHash = TypeConverter.StringHexToByteArray(_txHash);
        TxRecpt r = getTransactionReceipt(txHash);

        // if we can't find the receipt on the mainchain, try looking for it in pending receipts cache
        if (r == null) {
            AionTxReceipt pendingReceipt = pendingReceipts.get(new ByteArrayWrapper(txHash));
            r = new TxRecpt(pendingReceipt, null, null, null, true);
        }

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
    public String eth_newFilter(JSONObject _filterObj) {
        ArgFltr rf = ArgFltr.fromJSON(_filterObj);

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

    public boolean eth_uninstallFilter(String _id) {
        return _id != null && installedFilters.remove(TypeConverter.StringHexToBigInteger(_id).longValue()) != null;
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

    public Object eth_getFilterLogs(String _id) {
        return eth_getFilterChanges(_id);
    }

    public Object eth_getLogs(JSONObject _filterObj) {
        String id = eth_newFilter(_filterObj);
        Object response = eth_getFilterChanges(id);
        eth_uninstallFilter(id);
        return response;
    }

    /* -------------------------------------------------------------------------
     * personal
     */

    public Object personal_unlockAccount(String _account, String _password, Object _duration) {
        int duration = 300;
        if (_duration != null && !_duration.equals(null))
            duration = new BigInteger(_duration + "").intValueExact();

        return unlockAccount(_account, _password, duration);
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

    /* -------------------------------------------------------------------------
     * stratum pool
     */

    public Object stratum_getinfo() {
        JSONObject obj = new JSONObject();

        obj.put("balance", 0);
        obj.put("blocks", 0);
        obj.put("connections", peerCount());
        obj.put("proxy", "");
        obj.put("generate", true);
        obj.put("genproclimit", 100);
        obj.put("difficulty", 0);

        return obj;
    }

    public Object stratum_getblocktemplate() {
        // TODO: Change this to a synchronized map implementation mapping
        // block hashes to the block. Allow multiple block templates at same height.
        templateMapLock.writeLock().lock();

        AionBlock bestBlock = getBlockTemplate();

        // Check first entry in the map; if its height is higher a sync may
        // have switch branches, abandon current work to start on new branch
        if (!templateMap.keySet().isEmpty()) {
            if (templateMap.get(templateMap.keySet().iterator().next()).getNumber() < bestBlock.getNumber()) {
                // Found a higher block, clear any remaining cached entries and start on new height
                templateMap.clear();
            }
        }

        templateMap.put(toHexString(bestBlock.getHeader().getStaticHash()), bestBlock);

        JSONObject coinbaseaux = new JSONObject();
        coinbaseaux.put("flags", "062f503253482f");

        JSONObject obj = new JSONObject();
        obj.put("previousblockhash", toHexString(bestBlock.getParentHash()));
        obj.put("height", bestBlock.getNumber());
        obj.put("target", toHexString(BigInteger.valueOf(2).pow(256)
                .divide(new BigInteger(bestBlock.getHeader().getDifficulty())).toByteArray())); // TODO: Pool eventually calculates itself
        obj.put("transactions", new JSONArray());
        obj.putOpt("blockHeader", bestBlock.getHeader().toJSON());
        obj.put("coinbaseaux", coinbaseaux);
        obj.put("headerHash", toHexString(bestBlock.getHeader().getStaticHash()));

        templateMapLock.writeLock().unlock();

        return obj;
    }

    public Object stratum_dumpprivkey() {
        return "";
    }

    public Object stratum_validateaddress(String _address) {
        /*
         * "isvalid" : true|false, (boolean) If the address is valid or not.
         * If not, this is the only property returned. "address" :
         * "address", (string) The bitcoin address validated "scriptPubKey"
         * : "hex", (string) The hex encoded scriptPubKey generated by the
         * address "ismine" : true|false, (boolean) If the address is yours
         * or not "iswatchonly" : true|false, (boolean) If the address is
         * watchonly "isscript" : true|false, (boolean) If the key is a
         * script "pubkey" : "publickeyhex", (string) The hex value of the
         * raw public key "iscompressed" : true|false, (boolean) If the
         * address is compressed "account" : "account" (string) DEPRECATED.
         * The account associated with the address, "" is the default
         * account "timestamp" : timestamp, (number, optional) The creation
         * time of the key if available in seconds since epoch (Jan 1 1970
         * GMT) "hdkeypath" : "keypath" (string, optional) The HD keypath if
         * the key is HD and available "hdmasterkeyid" : "<hash160>"
         * (string, optional) The Hash160 of the HD master pubkey
         */
        JSONObject obj = new JSONObject();

        obj.put("isvalid", true);
        obj.put("address", _address + "");
        obj.put("scriptPubKey", "hex");
        obj.put("ismine", true);
        obj.put("iswatchonly", true);
        obj.put("isscript", true);
        obj.put("timestamp", 0);
        obj.put("hdkeypath", "");
        obj.put("hdmasterkeyid", new byte[0]); // new byte[160]

        return obj;
    }

    public Object stratum_getdifficulty() {
        // TODO: This needs to be refactored to return valid data
        return 0x4000;
    }

    public Object stratum_getmininginfo() {
        AionBlock bestBlock = getBestBlock();

        JSONObject obj = new JSONObject();
        obj.put("blocks", bestBlock.getNumber());
        obj.put("currentblocksize", 0);
        obj.put("currentblocktx", bestBlock.getTransactionsList().size());
        obj.put("difficulty", 3368767.14053294);
        obj.put("errors", "");
        obj.put("genproclimit", -1);
        obj.put("hashespersec", 0);
        obj.put("pooledtx", 0);
        obj.put("testnet", false);

        return obj;
    }

    public Object stratum_submitblock(Object nce, Object soln, Object hdrHash, Object ts) {
        JSONObject obj = new JSONObject();

        if (nce != null && soln != null && hdrHash != null && ts != null &&
                !nce.equals(null) && !soln.equals(null) && !hdrHash.equals(null) && !ts.equals(null)) {

            templateMapLock.writeLock().lock();

            AionBlock bestBlock = templateMap.get(hdrHash + "");

            boolean successfulSubmit = false;
            // TODO Clean up this section once decided on event vs direct call
            if (bestBlock != null) {
                successfulSubmit = submitBlock(new Solution(bestBlock, hexStringToBytes(nce + ""), hexStringToBytes(soln + ""), Long.parseLong(ts + "", 16)));
            }

            if (successfulSubmit) {
                // Found a solution for this height and successfully submitted, clear all entries for next height
                LOG.info("block sealed via api <num={}, hash={}, diff={}, tx={}>", bestBlock.getNumber(),
                        bestBlock.getShortHash(), // LogUtil.toHexF8(newBlock.getHash()),
                        bestBlock.getHeader().getDifficultyBI().toString(), bestBlock.getTransactionsList().size());
                templateMap.clear();
            }

            templateMapLock.writeLock().unlock();

            // TODO: Simplified response for now, need to provide better feedback to caller in next update
            obj.put("result", true);
        } else {
            obj.put("message", "success");
            obj.put("code", -1);
        }

        return obj;
    }

    public Object stratum_getHeaderByBlockNumber(Object _blockNum) {
        JSONObject obj = new JSONObject();

        if (_blockNum != null && !_blockNum.equals(null)) {
            String bnStr = _blockNum + "";
            try {
                int bnInt = Integer.decode(bnStr);
                AionBlock block = getBlockRaw(bnInt);

                if (block != null) {
                    A0BlockHeader header = block.getHeader();
                    obj.put("code", 0); // 0 = success
                    obj.put("nonce", toHexString(header.getNonce()));
                    obj.put("solution", toHexString(header.getSolution()));
                    obj.put("headerHash", toHexString(HashUtil.h256(header.getHeaderBytes(false))));
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

        return obj;
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
