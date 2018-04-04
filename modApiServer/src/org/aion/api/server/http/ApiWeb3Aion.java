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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import org.aion.api.server.ApiAion;
import org.aion.api.server.nrgprice.NrgOracle;
import org.aion.api.server.rpc.RpcError;
import org.aion.api.server.rpc.RpcMsg;
import org.aion.api.server.types.*;
import org.aion.base.db.IRepository;
import org.aion.base.type.Address;
import org.aion.base.type.ITransaction;
import org.aion.base.type.ITxReceipt;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.FastByteComparisons;
import org.aion.base.util.TypeConverter;
import org.aion.crypto.ECKey;
import org.aion.crypto.HashUtil;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallback;
import org.aion.evtmgr.impl.evt.EventTx;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.vm.types.DataWord;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.db.AionRepositoryImpl;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.aion.base.util.ByteUtil.hexStringToBytes;
import static org.aion.base.util.ByteUtil.toHexString;

/**
 * @author chris lin, ali sharif
 * TODO: make implementation pass all spec tests: https://github.com/ethereum/rpc-tests
 */
public class ApiWeb3Aion extends ApiAion {

    private final int OPS_RECENT_ENTITY_COUNT = 36;
    private final int OPS_RECENT_ENTITY_CACHE_TIME_SECONDS = 10;
    // TODO: Verify if need to use a concurrent map; locking may allow for use of a simple map
    private HashMap<ByteArrayWrapper, AionBlock> templateMap;
    private ReadWriteLock templateMapLock;
    private IEventMgr evtMgr;
    // doesn't need to be protected for concurrent access, since only one write in the constructor.
    private boolean isFilterEnabled;

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

        // instantiate nrg price oracle
        IAionBlockchain bc = (IAionBlockchain)_ac.getBlockchain();
        long nrgPriceDefault = CfgAion.inst().getApi().getNrg().getNrgPriceDefault();
        long nrgPriceMax = CfgAion.inst().getApi().getNrg().getNrgPriceMax();

        NrgOracle.Strategy oracleStrategy = NrgOracle.Strategy.SIMPLE;
        if (CfgAion.inst().getApi().getNrg().isOracleEnabled())
            oracleStrategy = NrgOracle.Strategy.BLK_PRICE;

        this.nrgOracle = new NrgOracle(bc, nrgPriceDefault, nrgPriceMax, oracleStrategy);

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
                        new CacheLoader<Integer, ChainHeadView>() {
                            public ChainHeadView load(Integer key) { // no checked exception
                                ChainHeadView view = new ChainHeadView(OPS_RECENT_ENTITY_COUNT).update();
                                return view;
                            }

                            public ListenableFuture<ChainHeadView> reload(final Integer key, ChainHeadView prev) {
                                try {
                                    ListenableFutureTask<ChainHeadView> task = ListenableFutureTask.create(new Callable<ChainHeadView>() {
                                        public ChainHeadView call() {
                                            return new ChainHeadView(prev).update();
                                        }
                                    });
                                    cacheUpdateExecutor.execute(task);
                                    return task;
                                } catch (Throwable e) {
                                    LOG.debug("<cache-updater - could not queue up task: ", e);
                                    throw(e);
                                } // exception is swallowed by refresh and load. so just log it for our logs
                            }
                        });

        cacheUpdateExecutor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), new CacheUpdateThreadFactory());
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

    // --------------------------------------------------------------------
    // Ethereum-Compliant JSON RPC Specification Implementation
    // --------------------------------------------------------------------

    public RpcMsg web3_clientVersion() {
        return new RpcMsg(this.clientVersion);
    }

    public RpcMsg web3_sha3(JSONArray _params) {
        String _data = _params.get(0) + "";

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
            obj.put("importMax", new NumericalValue(syncInfo.blksImportMax).toHexString());
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

    public RpcMsg eth_submitHashrate(JSONArray _params) {
        String _hashrate = _params.get(0) + "";
        String _clientId = _params.get(1) + "";
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

    public RpcMsg eth_getBalance(JSONArray _params) {
        String _address = _params.get(0) + "";
        Object _bnOrId = _params.opt(1);

        Address address = new Address(_address);

        String bnOrId = "latest";
        if (_bnOrId != null && !_bnOrId.equals(null))
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

    public RpcMsg eth_getStorageAt(JSONArray _params) {
        String _address = _params.get(0) + "";
        String _index = _params.get(1) + "";
        Object _bnOrId = _params.opt(2);

        Address address = new Address(_address);

        String bnOrId = "latest";
        if (_bnOrId != null && !_bnOrId.equals(null))
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

    public RpcMsg eth_getTransactionCount(JSONArray _params) {
        String _address = _params.get(0) + "";
        Object _bnOrId = _params.opt(1);

        Address address = new Address(_address);

        String bnOrId = "latest";
        if (_bnOrId != null && !_bnOrId.equals(null))
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

    public RpcMsg eth_getBlockTransactionCountByHash(JSONArray _params) {
        String _hash = _params.get(0) + "";

        byte[] hash = ByteUtil.hexStringToBytes(_hash);
        AionBlock b = this.ac.getBlockchain().getBlockByHash(hash);
        if (b == null)
            return new RpcMsg(null, RpcError.EXECUTION_ERROR, "Block not found.");

        long n = b.getTransactionsList().size();
        return new RpcMsg(TypeConverter.toJsonHex(n));
    }

    public RpcMsg eth_getBlockTransactionCountByNumber(JSONArray _params) {
        String _bnOrId = _params.get(0) + "";

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

    public RpcMsg eth_getCode(JSONArray _params) {
        String _address = _params.get(0) + "";
        Object _bnOrId = _params.opt(1);

        Address address = new Address(_address);

        String bnOrId = "latest";
        if (_bnOrId != null && !_bnOrId.equals(null))
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

    public RpcMsg eth_sign(JSONArray _params) {
        String _address = _params.get(0) + "";
        String _message = _params.get(1) + "";

        Address address = Address.wrap(_address);
        ECKey key = getAccountKey(address.toString());
        if (key == null)
            return new RpcMsg(null, RpcError.NOT_ALLOWED, "Account not unlocked.");

        // Message starts with Unicode Character 'END OF MEDIUM' (U+0019)
        String message = "\u0019Aion Signed Message:\n" + _message.length() + _message;
        byte[] messageHash = HashUtil.keccak256(message.getBytes());

        return new RpcMsg(TypeConverter.toJsonHex(key.sign(messageHash).getSignature()));
    }

    public RpcMsg eth_sendTransaction(JSONArray _params) {
        JSONObject _tx = _params.getJSONObject(0);

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

    public RpcMsg eth_sendRawTransaction(JSONArray _params) {
        String _rawTx = _params.get(0) + "";

        if (_rawTx.equals("null"))
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Null raw transaction provided.");

        byte[] rawTransaction = ByteUtil.hexStringToBytes(_rawTx);
        byte[] transactionHash = sendTransaction(rawTransaction);

        return new RpcMsg(TypeConverter.toJsonHex(transactionHash));
    }

    public RpcMsg eth_call(JSONArray _params) {
        JSONObject _tx = _params.getJSONObject(0);
        Object _bnOrId = _params.opt(1);

        ArgTxCall txParams = ArgTxCall.fromJSON(_tx, getNrgOracle(), getDefaultNrgLimit());

        String bnOrId = "latest";
        if (_bnOrId != null && !_bnOrId.equals(null))
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

    public RpcMsg eth_estimateGas(JSONArray _params) {
        JSONObject _tx = _params.getJSONObject(0);

        ArgTxCall txParams = ArgTxCall.fromJSON(_tx, getNrgOracle(), getDefaultNrgLimit());
        NumericalValue estimate = new NumericalValue(estimateGas(txParams));

        return new RpcMsg(estimate.toHexString());
    }

    public RpcMsg eth_getBlockByHash(JSONArray _params) {
        String _hash = _params.get(0) + "";
        boolean _fullTx = _params.optBoolean(1, false);

        byte[] hash = ByteUtil.hexStringToBytes(_hash);
        AionBlock block = this.ac.getBlockchain().getBlockByHash(hash);

        if (block == null) {
            LOG.debug("<get-block hash={} err=not-found>", _hash);
            return new RpcMsg(JSONObject.NULL); // json rpc spec: 'or null when no block was found'
        } else {
            BigInteger totalDiff = this.ac.getAionHub().getBlockStore().getTotalDifficultyForHash(hash);
            return new RpcMsg(Blk.AionBlockToJson(block, totalDiff, _fullTx));
        }
    }

    public RpcMsg eth_getBlockByNumber(JSONArray _params) {
        String _bnOrId = _params.get(0) + "";
        boolean _fullTx = _params.optBoolean(1, false);

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

    public RpcMsg eth_getTransactionByHash(JSONArray _params) {
        String _hash = _params.get(0) + "";

        byte[] txHash = ByteUtil.hexStringToBytes(_hash);
        if (_hash.equals("null") || txHash == null) return null;

        AionTxInfo txInfo = this.ac.getAionHub().getBlockchain().getTransactionInfo(txHash);
        if (txInfo == null)
            return new RpcMsg(JSONObject.NULL); // json rpc spec: 'or null when no transaction was found'

        AionBlock b = this.ac.getBlockchain().getBlockByHash(txInfo.getBlockHash());
        if (b == null) return null; // this is actually an internal error

        return new RpcMsg(Tx.InfoToJSON(txInfo, b));
    }

    public RpcMsg eth_getTransactionByBlockHashAndIndex(JSONArray _params) {
        String _hash = _params.get(0) + "";
        String _index = _params.get(1) + "";

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

    public RpcMsg eth_getTransactionByBlockNumberAndIndex(JSONArray _params) {
        String _bnOrId = _params.get(0) + "";
        String _index = _params.get(1) + "";

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

    public RpcMsg eth_getTransactionReceipt(JSONArray _params) {
        String _hash = _params.get(0) + "";

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

    public RpcMsg eth_compileSolidity(JSONArray _params) {
        String _contract = _params.get(0) + "";

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

    public RpcMsg eth_newFilter(JSONArray _params) {
        if (!isFilterEnabled) {
            return new RpcMsg(null, RpcError.NOT_ALLOWED, "Filters over rpc disabled.");
        }

        JSONObject _filterObj = _params.getJSONObject(0);

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

    public RpcMsg eth_uninstallFilter(JSONArray _params) {
        if (!isFilterEnabled) {
            return new RpcMsg(null, RpcError.NOT_ALLOWED, "Filters over rpc disabled.");
        }

        String _id = _params.get(0) + "";

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

    public RpcMsg eth_getFilterChanges(JSONArray _params) {
        if (!isFilterEnabled) {
            return new RpcMsg(null, RpcError.NOT_ALLOWED, "Filters over rpc disabled.");
        }

        String _id = _params.get(0) + "";

        long id = TypeConverter.StringHexToBigInteger(_id).longValue();
        Fltr filter = installedFilters.get(id);

        if (filter == null)
            return new RpcMsg(null, RpcError.EXECUTION_ERROR, "Filter not found.");

        return new RpcMsg(buildFilterResponse(filter));
    }

    public RpcMsg eth_getLogs(JSONArray _params) {
        JSONObject _filterObj = _params.getJSONObject(0);
        ArgFltr rf = ArgFltr.fromJSON(_filterObj);
        FltrLg filter = createFilter(rf);
        if (filter == null)
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid block ids provided.");

        return new RpcMsg(buildFilterResponse(filter));
    }

    /* -------------------------------------------------------------------------
     * personal
     */

    public RpcMsg personal_unlockAccount(JSONArray _params) {
        String _account = _params.get(0) + "";
        String _password = _params.get(1) + "";
        Object _duration = _params.opt(2);

        int duration = 300;
        if (_duration != null && !_duration.equals(null))
            duration = new BigInteger(_duration + "").intValueExact();

        return new RpcMsg(unlockAccount(_account, _password, duration));
    }

    /* -------------------------------------------------------------------------
     * debug
     */

    public RpcMsg debug_getBlocksByNumber(JSONArray _params) {
        String _bnOrId = _params.get(0) + "";
        boolean _fullTx = _params.optBoolean(1, false);

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
     * operational api
     */

    // always gets the latest account state
    public RpcMsg ops_getAccountState(JSONArray _params) {
        String _address = _params.get(0) + "";

        Address address = new Address(_address);

        long latestBlkNum = this.getBestBlock().getNumber();
        AccountState accountState = ((AionRepositoryImpl) this.ac.getRepository()).getAccountState(address);

        if (accountState == null)
            return new RpcMsg(null, RpcError.INVALID_PARAMS, "Invalid address provided.");

        JSONObject response = new JSONObject();
        response.put("blockNumber", latestBlkNum);
        response.put("balance", TypeConverter.toJsonHex(accountState.getBalance()));
        response.put("nonce", accountState.getNonce());

        return new RpcMsg(response);
    }

    // always gets the latest 20 blocks and transactions
    private class ChainHeadView {
        LinkedList<byte[]> hashQueue;
        Map<byte[], JSONObject> blkList;
        Map<byte[], AionBlock> blkObjList;
        Map<byte[], JSONArray> txnList;

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
            Map.Entry<JSONObject, JSONArray> response;
            BigInteger totalDiff = ac.getAionHub().getBlockStore().getTotalDifficultyForHash(_b.getHash());
            return (JSONObject) Blk.AionBlockToJson(_b, totalDiff, true);
        }

        private JSONObject buildResponse() {
            JSONArray blks = new JSONArray();
            JSONArray txns = new JSONArray();

            // return qSize number of blocks and transactions as json
            ListIterator li = hashQueue.listIterator(hashQueue.size());
            while(li.hasPrevious()) {
                byte[] hash = (byte[]) li.previous();
                blks.put(blkList.get(hash));
                JSONArray t = txnList.get(hash);
                if(txns.length() < qSize) {
                    for (int i = 0; i < t.length(); i++) {
                        if (txns.length() < qSize) {
                            txns.put(t.getJSONObject(i));
                        }
                    }
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
            o.put("blks", blks);
            o.put("txns", txns);
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

            Iterator it = blkObjList.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry)it.next();
                AionBlock b = (AionBlock)pair.getValue();

                if (lastBlkTimestamp != null) {
                    blkTimeAccumulator += (b.getTimestamp() - lastBlkTimestamp);
                }
                lastBlkTimestamp = b.getTimestamp();

                difficultyAccumulator = difficultyAccumulator.add(new BigInteger(b.getDifficulty()));
                lastDifficulty = new BigInteger(b.getDifficulty());

                nrgConsumedAccumulator = nrgConsumedAccumulator.add(new BigInteger(Long.toString(b.getNrgConsumed())));
                nrgLimitAccumulator = nrgLimitAccumulator.add(new BigInteger(Long.toString(b.getNrgLimit())));
                txnCount += b.getTransactionsList().size();
            }

            double blkTime = blkTimeAccumulator / (double)count;
            double hashRate = lastDifficulty.longValue() / blkTime;
            double avgDifficulty = difficultyAccumulator.longValue() / (double)count;
            double avgNrgConsumedPerBlock = nrgConsumedAccumulator.longValue() / (double)count;
            double avgNrgLimitPerBlock = nrgLimitAccumulator.longValue() / (double)count;
            double txnPerSec = txnCount / (double)blkTimeAccumulator;

            JSONObject metrics = new JSONObject();
            metrics.put("blkTime", blkTime);
            metrics.put("hashRate",hashRate);
            metrics.put("avgDifficulty",avgDifficulty);
            metrics.put("avgNrgConsumedPerBlock",avgNrgConsumedPerBlock);
            metrics.put("avgNrgLimitPerBlock",avgNrgLimitPerBlock);
            metrics.put("txnPerSec",txnPerSec);

            return metrics;
        }

        public ChainHeadView update() {
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
                System.out.println("[" + 0 + "]: " + TypeConverter.toJsonHex(hashQueue.peekFirst()) + " - " + blkObjList.get(hashQueue.peekFirst()).getNumber());
                System.out.println("----------------------------------------------------------");
                System.out.println("isParentHashMatch? " + FastByteComparisons.equal(hashQueue.peekFirst(), blk.getParentHash()));
                System.out.println("blk.getNumber() " + blk.getNumber());
            }
            System.out.println("blkNum: " + blk.getNumber() +
                    " parentHash: " + TypeConverter.toJsonHex(blk.getParentHash()) +
                    " blkHash: " + TypeConverter.toJsonHex(blk.getHash()));
            */

            while(FastByteComparisons.equal(hashQueue.peekFirst(), blk.getParentHash()) == false
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
                JSONArray txnJson = (JSONArray) blkJson.remove("transactions");

                hashQueue.push(hash);
                blkList.put(hash, blkJson);
                blkObjList.put(hash, blkObj);
                txnList.put(hash, txnJson);
            }

            /*
            System.out.println("[" + 0 + "]: " + TypeConverter.toJsonHex(hashQueue.peekFirst()) + " - " + blkObjList.get(hashQueue.peekFirst()).getNumber());
            System.out.println("----------------------------------------------------------");
            for (int i = hashQueue.size() - 1; i >= 0; i--) {
                System.out.println("[" + i + "]: " + TypeConverter.toJsonHex(hashQueue.get(i)) + " - " + blkObjList.get(hashQueue.get(i)).getNumber());
            }
            */
            this.response = buildResponse();

            return this;
        }

        public JSONObject getResponse() {
            return response;
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

    private ExecutorService cacheUpdateExecutor;
    private final LoadingCache<Integer, ChainHeadView> CachedRecentEntities;

    public RpcMsg ops_getChainHeadView(JSONArray _params) {
        try {
            ChainHeadView v = CachedRecentEntities.get(CachedResponseType.CHAIN_HEAD.ordinal());
            return new RpcMsg(v.getResponse());
        } catch (Exception e) {
            LOG.error("<rpc-server - cannot get cached response for ops_getChainHeadView: ", e);
            return new RpcMsg(null, RpcError.EXECUTION_ERROR, "Cached response retrieve failed.");
        }
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

    public RpcMsg stratum_getblocktemplate() {
        // TODO: Change this to a synchronized map implementation mapping

        AionBlock bestBlock = getBlockTemplate();
        ByteArrayWrapper key = new ByteArrayWrapper(bestBlock.getHeader().getStaticHash());

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
                bestBlock = new AionBlock(bestBlock);

                if (!templateMap.keySet().isEmpty()) {
                    if (templateMap.get(templateMap.keySet().iterator().next()).getNumber() < bestBlock.getNumber()) {
                        // Found a higher block, clear any remaining cached entries and start on new height
                        templateMap.clear();
                    }
                }
                templateMap.put(key, bestBlock);

            }finally {
                templateMapLock.writeLock().unlock();
            }
        }

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

        return new RpcMsg(obj);
    }

    public RpcMsg stratum_dumpprivkey() {
        return new RpcMsg("");
    }

    public RpcMsg stratum_validateaddress(JSONArray _params) {
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
        String _address = _params.get(0) + "";

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

        return new RpcMsg(obj);
    }

    public RpcMsg stratum_getdifficulty() {
        // TODO: This needs to be refactored to return valid data
        return new RpcMsg(0x4000);
    }

    public RpcMsg stratum_getmininginfo() {
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

        return new RpcMsg(obj);
    }

    public RpcMsg stratum_submitblock(JSONArray _params) {
        Object nce = _params.opt(0);
        Object soln = _params.opt(1);
        Object hdrHash = _params.opt(2);
        Object ts = _params.opt(3);

        JSONObject obj = new JSONObject();

        if (nce != null && soln != null && hdrHash != null && ts != null &&
                !nce.equals(null) && !soln.equals(null) && !hdrHash.equals(null) && !ts.equals(null)) {

            try {
                templateMapLock.writeLock().lock();

                ByteArrayWrapper key = new ByteArrayWrapper(hexStringToBytes((String) hdrHash));

                AionBlock bestBlock = templateMap.remove(key);
                if (bestBlock != null) {

                    bestBlock.getHeader().setSolution(hexStringToBytes(soln + ""));
                    bestBlock.getHeader().setNonce(hexStringToBytes(nce + ""));
                    bestBlock.getHeader().setTimestamp(Long.parseLong(ts + "", 16));

                    // Directly submit to chain for new due to delays using event, explore event submission again
                    ImportResult importResult = AionImpl.inst().addNewMinedBlock(bestBlock);
                    if(importResult == ImportResult.IMPORTED_BEST || importResult == ImportResult.IMPORTED_NOT_BEST) {
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

    public RpcMsg stratum_getHeaderByBlockNumber(JSONArray _params) {
        Object _blockNum = _params.opt(0);

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

        return new RpcMsg(obj);
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
