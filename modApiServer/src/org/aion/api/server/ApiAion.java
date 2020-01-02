package org.aion.api.server;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aion.api.server.account.AccountManager;
import org.aion.api.server.nrgprice.NrgOracle;
import org.aion.api.server.types.ArgTxCall;
import org.aion.api.server.types.Fltr;
import org.aion.api.server.types.SyncInfo;
import org.aion.api.server.types.TxRecpt;
import org.aion.base.AionTransaction;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.es.EventExecuteService;
import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.blockchain.UnityChain;
import org.aion.zero.impl.types.TxResponse;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.string.StringUtils;
import org.aion.util.types.AddressUtils;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.types.AionGenesis;
import org.aion.zero.impl.blockchain.AionHub;
import org.aion.zero.impl.types.BlockContext;
import org.aion.zero.impl.Version;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.base.AionTxReceipt;

public abstract class ApiAion extends Api {
    public static final int SYNC_TOLERANCE = 1;

    // these variables get accessed by the api worker threads.
    // need to guarantee one of:
    // 1. all access to variables protected by some lock
    // 2. underlying datastructure provides concurrency guarntees

    // delegate concurrency to underlying object
    private static NrgOracle NRG_ORACLE;
    protected IAionChain ac; // assumption: blockchainImpl et al. provide concurrency guarantee

    public static final byte JAVAAPI_VAR = AionHub.getApiVersion();

    // using java.util.concurrent library objects
    protected AtomicLong fltrIndex; // AtomicLong
    protected Map<Long, Fltr> installedFilters; // ConcurrentHashMap
    protected Map<ByteArrayWrapper, AionTxReceipt> pendingReceipts; // Collections.synchronizedMap

    // 'safe-publishing' idiom
    private volatile double reportedHashrate = 0; // volatile, used only for 'publishing'

    // thread safe because value never changing, can be safely read by multiple threads
    protected final String[] compilers = new String[] {"solidity"};
    protected final short FLTRS_MAX = 1024;
    protected final String clientVersion = computeClientVersion();

    private volatile BlockContext currentTemplate;

    protected EventExecuteService ees;

    /**
     * @param ac AionChain instance.
     */
    public ApiAion(final IAionChain ac, final AccountManager am) {
        if (ac == null) {
            throw new NullPointerException("ApiAion construct IAionChain argument is null");
        }

        this.ac = ac;
        this.accountManager = am;

        installedFilters = new ConcurrentHashMap<>();
        fltrIndex = new AtomicLong(0);
        pendingState = ac.getAionHub().getPendingState();
        IEventMgr evtMgr = ac.getAionHub().getEventMgr();
        evtMgr.registerEvent(
                Collections.singletonList(new EventBlock(EventBlock.CALLBACK.ONBLOCK0)));
    }

    public final class EpApi implements Runnable {
        boolean go = true;

        @Override
        public void run() {
            while (go) {
                try {
                    IEvent e = ees.take();
                    if (e.getEventType() == IHandler.TYPE.BLOCK0.getValue()
                        && e.getCallbackType() == EventBlock.CALLBACK.ONBLOCK0.getValue()) {
                        onBlock((AionBlockSummary) e.getFuncArgs().get(0));
                    } else if (e.getEventType() == IHandler.TYPE.POISONPILL.getValue()) {
                        go = false;
                    }
                } catch (Exception e) {
                    LOG.debug("EpApi - excepted out", e);
                }
            }
        }
    }

    protected abstract void onBlock(AionBlockSummary cbs);

    protected abstract void pendingTxReceived(AionTransaction _tx);

    protected abstract void pendingTxUpdate(AionTxReceipt _txRcpt, int _state);

    // General Level
    public byte getApiVersion() {
        return JAVAAPI_VAR;
    }

    protected Map<Long, Fltr> getInstalledFltrs() {
        return installedFilters;
    }

    public String getCoinbase() {
        String coinbase = CfgAion.inst().getConsensus().getMinerAddress();
        return StringUtils.toJsonHex(coinbase);
    }

    protected final class TransactionWithBlockInfo {
        public final byte[] blockHash;
        public final long blockNumber;
        public final long txIndexInBlock;
        public final long nrgUsed;
        public final AionTransaction tx;

        public TransactionWithBlockInfo(
                AionTransaction tx,
                byte[] blockHash,
                long blockNumber,
                long txIndexInBlock,
                long nrgUsed) {
            this.tx = tx;
            this.blockHash = blockHash;
            this.blockNumber = blockNumber;
            this.txIndexInBlock = txIndexInBlock;
            this.nrgUsed = nrgUsed;
        }
    }

    @Override
    public Block getBestBlock() {
        return this.ac.getBlockchain().getBestBlock();
    }

    public AionBlock getBestMiningBlock() {
        return this.ac.getBlockchain().getBestMiningBlock();
    }

    //TODO: AKI-441: Should this method be synchronized? Should currentTemplate be volatile?
    protected BlockContext getBlockTemplate() {
        currentTemplate = ac.getAionHub().getNewMiningBlockTemplate(currentTemplate, TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
        return currentTemplate;
    }

    public Block getBlockByHash(byte[] hash) {
        return this.ac.getBlockchain().getBlockByHash(hash);
    }

    @Override
    public Block getBlock(long blkNr) {
        if (blkNr == -1) {
            return this.ac.getBlockchain().getBestBlock();
        } else if (blkNr >= 0) {
            return this.ac.getBlockchain().getBlockByNumber(blkNr);
        } else {
            LOG.debug("ApiAion.getBlock - incorrect argument");
            return null;
        }
    }

    protected Map.Entry<Block, BigInteger> getBlockWithTotalDifficulty(long blkNr) {
        if (blkNr > 0) {
            Block block = this.ac.getBlockchain().getBlockStore().getChainBlockByNumber(blkNr);
            return (Map.entry(block, block.getTotalDifficulty()));
        } else if (blkNr == 0) {
            AionGenesis genBlk = CfgAion.inst().getGenesis();
            return Map.entry(
                    new AionBlock(genBlk.getHeader(), genBlk.getTransactionsList()),
                    genBlk.getDifficultyBI());
        } else {
            LOG.debug("ApiAion.getBlock - incorrect argument");
            return null;
        }
    }

    /**
     * Returns a {@link SyncInfo} object that reports whether or not syncing has started.
     *
     * <p>Since a node is never really 'done' syncing, we consider a node to be done if it is within
     * {@value SYNC_TOLERANCE} blocks of the network best block number.
     *
     * @param localBestBlockNumber The current block number of the local node.
     * @param networkBestBlockNumber The current block number of the network.
     * @return the syncing statistics.
     */
    protected SyncInfo getSyncInfo(long localBestBlockNumber, long networkBestBlockNumber) {
        SyncInfo sync = new SyncInfo();

        sync.done = localBestBlockNumber + SYNC_TOLERANCE >= networkBestBlockNumber;

        sync.chainStartingBlkNumber = this.ac.getInitialStartingBlockNumber().orElse(0L);
        sync.chainBestBlkNumber = localBestBlockNumber;
        sync.networkBestBlkNumber = networkBestBlockNumber;

        return sync;
    }

    protected TransactionWithBlockInfo getTransactionByBlockHashAndIndex(byte[] hash, long index) {
        Block pBlk = this.getBlockByHash(hash);
        if (pBlk == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error(
                        "ApiAion.getTransactionByBlockHashAndIndex - can't find the block by the block hash");
            }
            return null;
        }

        List<AionTransaction> txList = pBlk.getTransactionsList();
        AionTransaction tx = txList.get((int) index);
        if (tx == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Can't find the transaction!");
            }
            return null;
        }

        TxRecpt receipt = this.getTransactionReceipt(tx.getTransactionHash());
        // @Jay this should not happen!
        // TODO
        if (receipt == null) {
            throw new NullPointerException();
        }

        return new TransactionWithBlockInfo(
                tx, pBlk.getHash(), pBlk.getNumber(), index, receipt.nrgUsed);
    }

    protected TransactionWithBlockInfo getTransactionByBlockNumberAndIndex(long blkNr, long index) {
        Block pBlk = this.getBlock(blkNr);
        if (pBlk == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error(
                        "ApiAion.getTransactionByBlockNumberAndIndex - can't find the block by the block number");
            }
            return null;
        }

        List<AionTransaction> txList = pBlk.getTransactionsList();
        AionTransaction tx = txList.get((int) index);
        if (tx == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Can't find the transaction by the txIndex");
            }
            return null;
        }

        TxRecpt receipt = this.getTransactionReceipt(tx.getTransactionHash());
        // The receipt shouldn't be null!
        if (receipt == null) {
            throw new NullPointerException();
        }

        return new TransactionWithBlockInfo(
                tx, pBlk.getHash(), pBlk.getNumber(), index, receipt.nrgUsed);
    }

    protected long getBlockTransactionCountByNumber(long blkNr) {
        Block pBlk = this.getBlock(blkNr);
        if (pBlk == null) {
            LOG.error(
                    "ApiAion.getTransactionByBlockNumberAndIndex - can't find the block by the block number");
            return -1;
        }

        return pBlk.getTransactionsList().size();
    }

    protected long getTransactionCountByHash(byte[] hash) {
        Block pBlk = this.getBlockByHash(hash);
        if (pBlk == null) {
            LOG.error(
                    "ApiAion.getTransactionByBlockNumberAndIndex - can't find the block by the block number");
            return -1;
        }
        return pBlk.getTransactionsList().size();
    }

    protected long getTransactionCount(AionAddress addr, long blkNr) {
        Block pBlk = this.getBlock(blkNr);
        if (pBlk == null) {
            LOG.error(
                    "ApiAion.getTransactionByBlockNumberAndIndex - can't find the block by the block number");
            return -1;
        }
        long cnt = 0;
        List<AionTransaction> txList = pBlk.getTransactionsList();
        for (AionTransaction tx : txList) {
            if (addr.equals(tx.getSenderAddress())) {
                cnt++;
            }
        }
        return cnt;
    }

    protected TransactionWithBlockInfo getTransactionByHash(byte[] hash) {
        TxRecpt txRecpt = this.getTransactionReceipt(hash);

        if (txRecpt == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Can't find the transaction receipt by the txhash.");
            }
            return null;
        } else {
            TransactionWithBlockInfo txInfo =
                    this.getTransactionByBlockNumberAndIndex(
                            txRecpt.blockNumber, txRecpt.transactionIndex);

            if (txInfo == null) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Can't find the transaction by the blocknumber and the txIndex.");
                }
                return null;
            }

            return txInfo;
        }
    }

    public byte[] getCode(AionAddress addr) {
        return this.ac.getRepository().getCode(addr);
    }

    /* NOTE: only use this if you need receipts for one or small number transactions in a block.
     * (since there is n^2 work happening here to compute cumulative nrg)
     * For use cases where you need all the transaction receipts in a block, please use a different
     * strategy,
     */
    protected TxRecpt getTransactionReceipt(byte[] txHash) {
        if (txHash == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("<get-transaction-receipt msg=tx-hash-null>");
            }
            return null;
        }

        AionTxInfo txInfo = this.ac.getAionHub().getBlockchain().getTransactionInfo(txHash);
        if (txInfo == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("<get-transaction-receipt msg=tx-info-null>");
            }
            return null;
        }
        Block block =
                this.ac.getAionHub().getBlockchain().getBlockByHash(txInfo.getBlockHash());

        if (block == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("<get-transaction-receipt msg=block-null>");
            }
            return null;
        }

        // need to return txes only from main chain
        Block mainBlock =
                this.ac.getAionHub().getBlockchain().getBlockByNumber(block.getNumber());
        if (!Arrays.equals(block.getHash(), mainBlock.getHash())) {
            LOG.debug("<get-transaction-receipt msg=hash-not-match>");
            return null;
        }

        // @Jay
        // TODO : think the good way to calculate the cumulated nrg use
        long cumulateNrg = 0L;
        for (AionTransaction atx : block.getTransactionsList()) {

            // @Jay: This should not happen!
            byte[] hash = atx.getTransactionHash();
            if (hash == null) {
                throw new NullPointerException();
            }

            AionTxInfo info = this.ac.getAionHub().getBlockchain().getTransactionInfo(hash);

            // @Jay: This should not happen!
            if (info == null) {
                throw new NullPointerException();
            }

            cumulateNrg += info.getReceipt().getEnergyUsed();
            if (Arrays.equals(txHash, hash)) {
                break;
            }
        }

        return new TxRecpt(block, txInfo, cumulateNrg, true);
    }

    protected byte[] doCall(ArgTxCall _params) {
        AionTransaction tx =
                AionTransaction.createWithoutKey(
                        _params.getNonce().toByteArray(),
                        _params.getFrom() == null ? AddressUtils.ZERO_ADDRESS : _params.getFrom(),
                        _params.getTo(),
                        _params.getValue().toByteArray(),
                        _params.getData(),
                        _params.getNrg(),
                        _params.getNrgPrice(),
                        _params.getType(), null);
        AionTxReceipt rec =
                this.ac.callConstant(tx, this.ac.getAionHub().getBlockchain().getBestBlock());
        return rec.getTransactionOutput();
    }

    protected long estimateNrg(ArgTxCall params) {
        AionAddress fromAddr =
                (params.getFrom() == null) ? AddressUtils.ZERO_ADDRESS : params.getFrom();
        AionTransaction tx =
                AionTransaction.createWithoutKey(
                        params.getNonce().toByteArray(),
                        fromAddr,
                        params.getTo(),
                        params.getValue().toByteArray(),
                        params.getData(),
                        params.getNrg(),
                        params.getNrgPrice(),
                        params.getType(), null);

        AionTxReceipt receipt =
                this.ac.callConstant(tx, this.ac.getAionHub().getBlockchain().getBestBlock());
        return receipt.getEnergyUsed();
    }

    protected ApiTxResponse createContract(ArgTxCall _params) {

        if (_params == null) {
            return (new ApiTxResponse(TxResponse.INVALID_TX));
        }

        AionAddress from = _params.getFrom();

        if (from == null) {
            LOG.error("<create-contract msg=invalid-from-address>");
            return (new ApiTxResponse(TxResponse.INVALID_FROM));
        }

        ECKey key = this.getAccountKey(from.toString());

        if (key == null) {
            LOG.debug("ApiAion.createContract - null key");
            return (new ApiTxResponse(TxResponse.INVALID_ACCOUNT));
        }

        try {
            synchronized (pendingState) {
                byte[] nonce =
                        !(_params.getNonce().equals(BigInteger.ZERO))
                                ? _params.getNonce().toByteArray()
                                : pendingState
                                        .bestPendingStateNonce(new AionAddress(key.getAddress()))
                                        .toByteArray();

                AionTransaction tx =
                        AionTransaction.create(
                                key,
                                nonce,
                                null,
                                _params.getValue().toByteArray(),
                                _params.getData(),
                                _params.getNrg(),
                                _params.getNrgPrice(),
                                _params.getType(), null);

                TxResponse rsp = pendingState.addPendingTransaction(tx);

                AionAddress address = TxUtil.calculateContractAddress(tx);
                return new ApiTxResponse(rsp, tx.getTransactionHash(), address);
            }
        } catch (Exception ex) {
            LOG.error("ApiAion.createContract - exception: [{}]", ex.getMessage());
            return new ApiTxResponse(TxResponse.EXCEPTION, ex);
        }
    }

    // Transaction Level
    public BigInteger getBalance(String _address) {
        return this.ac.getRepository().getBalance(AddressUtils.wrapAddress(_address));
    }

    public BigInteger getBalance(AionAddress _address) {
        return this.ac.getRepository().getBalance(_address);
    }

    public BigInteger getNonce(String _address) {
        return this.ac.getRepository().getNonce(AddressUtils.wrapAddress(_address));
    }

    public BigInteger getNonce(AionAddress _address) {
        return this.ac.getRepository().getNonce(_address);
    }

    protected ApiTxResponse sendTransaction(ArgTxCall _params) {

        if (_params == null) {
            return (new ApiTxResponse(TxResponse.INVALID_TX));
        }

        AionAddress from = _params.getFrom();

        if (from == null) {
            LOG.error("<send-transaction msg=invalid-from-address>");
            return (new ApiTxResponse(TxResponse.INVALID_FROM));
        }

        ECKey key = this.getAccountKey(from.toString());
        if (key == null) {
            LOG.error("<send-transaction msg=account-not-found>");
            return (new ApiTxResponse(TxResponse.INVALID_ACCOUNT));
        }

        try {
            synchronized (pendingState) {
                // TODO : temp set nrg & price to 1
                byte[] nonce =
                        (!_params.getNonce().equals(BigInteger.ZERO))
                                ? _params.getNonce().toByteArray()
                                : pendingState
                                        .bestPendingStateNonce(new AionAddress(key.getAddress()))
                                        .toByteArray();

                AionTransaction tx =
                        AionTransaction.create(
                                key,
                                nonce,
                                _params.getTo(),
                                _params.getValue().toByteArray(),
                                _params.getData(),
                                _params.getNrg(),
                                _params.getNrgPrice(),
                                _params.getType(),
                                _params.getBeaconHash());

                return (new ApiTxResponse(
                        pendingState.addPendingTransaction(tx), tx.getTransactionHash()));
            }
        } catch (Exception ex) {
            LOG.error("ApiAion.sendTransaction exception: [{}]", ex.getMessage());
            return (new ApiTxResponse(TxResponse.EXCEPTION, ex));
        }
    }

    protected ApiTxResponse sendTransaction(byte[] signedTx) {
        if (signedTx == null) {
            return (new ApiTxResponse(TxResponse.INVALID_TX));
        }

        AionTransaction tx = TxUtil.decode(signedTx);
        if (tx == null) {
            return (new ApiTxResponse(TxResponse.INVALID_TX));
        }

        try {
            return (new ApiTxResponse(
                    pendingState.addPendingTransaction(tx), tx.getTransactionHash()));
        } catch (Exception ex) {
            LOG.error("<send-transaction exception>", ex);
            return (new ApiTxResponse(TxResponse.EXCEPTION, ex));
        }
    }

    protected AionTransaction signTransaction(ArgTxCall _params, String _address) {
        AionAddress address;
        if (_address == null || _address.isEmpty()) {
            LOG.error("<sign-transaction msg=invalid-signing-address>");
            return null;
        } else {
            address = AddressUtils.wrapAddress(_address);
        }

        ECKey key = getAccountKey(address.toString());
        if (key == null) {
            LOG.error("<sign-transaction msg=account-not-unlocked>");
            return null;
        }

        try {
            synchronized (pendingState) {
                byte[] nonce =
                        (!_params.getNonce().equals(BigInteger.ZERO))
                                ? _params.getNonce().toByteArray()
                                : pendingState
                                        .bestPendingStateNonce(new AionAddress(key.getAddress()))
                                        .toByteArray();

                return AionTransaction.create(
                        key,
                        nonce,
                        _params.getTo(),
                        _params.getValue().toByteArray(),
                        _params.getData(),
                        _params.getNrg(),
                        _params.getNrgPrice(),
                        _params.getType(), null);
            }
        } catch (Exception ex) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to sign the transaction");
            }
            return null;
        }
    }

    // --Commented out by Inspection START (02/02/18 6:58 PM):
    // public String getNodeId() {
    // return CfgAion.inst().getId();
    // }
    // --Commented out by Inspection STOP (02/02/18 6:58 PM)

    protected String[] getBootNodes() {
        return CfgAion.inst().getNodes();
    }

    //    private synchronized BigInteger getTxNonce(ECKey key) {
    //        return pendingState.bestPendingStateNonce();
    //    }

    //    private synchronized BigInteger getTxNonce(ECKey key, boolean add) {
    //        return add ? nm.getNonceAndAdd(Address.wrap(key.getAddress())) :
    // nm.getNonce(Address.wrap(key.getAddress()));
    //    }

    public boolean isMining() {
        return ac.getBlockMiner() != null && ac.getBlockMiner().isMining();
    }

    protected int peerCount() {
        return this.ac.getAionHub().getActiveNodesCount();
    }

    // follows the ethereum standard for web3 compliance. DO NOT DEPEND ON IT.
    // Will be changed to Aion-defined spec later
    // https://github.com/ethereum/wiki/wiki/Client-Version-Strings
    private String computeClientVersion() {
        try {
            return Stream.of(
                            "Aion(J)",
                            "v" + Version.KERNEL_VERSION,
                            System.getProperty("os.name"),
                            "Java-" + System.getProperty("java.version"))
                    .collect(Collectors.joining("/"));
        } catch (Exception e) {
            LOG.debug("client version string generation failed", e);
        }

        return ("Aion(J)/v" + Version.KERNEL_VERSION);
    }

    // create a comma-separated string of supported p2p wire protocol versions
    // mainly to keep compatibility with eth_protocolVersion which returns a String
    protected String p2pProtocolVersion() {
        try {
            List<Short> p2pVersions = this.ac.getAionHub().getP2pVersions();
            int i = 0;
            StringBuilder b = new StringBuilder();
            for (Short v : p2pVersions) {
                b.append(ByteUtil.byteArrayToInt(ByteUtil.shortToBytes(v)));
                i++;
                if (i < p2pVersions.size()) {
                    b.append(",");
                }
            }
            return b.toString();
        } catch (Exception e) {
            LOG.error("p2p protocol versions string generation failed");
            return null;
        }
    }

    protected String chainId() {
        return (this.ac.getAionHub().getChainId() + "");
    }

    public String getHashrate() {
        double hashrate = 0;

        // add the the hashrate computed by the internal CPU miner
        if (isMining()) {
            hashrate += this.ac.getBlockMiner().getHashrate();
        }

        hashrate += reportedHashrate;

        return Double.toString(hashrate);
    }

    // hashrate in sol/s should just be a hexadecimal representation of a BigNumber
    // right now, assuming only one external miner is connected to the kernel
    // this needs to change in the future when this client needs to support multiple external miners
    protected boolean setReportedHashrate(String hashrate, String clientId) {
        try {
            reportedHashrate = Double.parseDouble(hashrate);
            return true;
        } catch (Exception e) {
            LOG.debug("api - setReportedHashrate(): bad string supplied", e);
        }

        return false;
    }

    // Returns a fully initialized NrgOracle object.
    protected void initNrgOracle(IAionChain _ac) {
        if (NRG_ORACLE != null) return;

        UnityChain bc = _ac.getBlockchain();
        long nrgPriceDefault = CfgAion.inst().getApi().getNrg().getNrgPriceDefault();
        long nrgPriceMax = CfgAion.inst().getApi().getNrg().getNrgPriceMax();

        NrgOracle.Strategy oracleStrategy = NrgOracle.Strategy.SIMPLE;
        if (CfgAion.inst().getApi().getNrg().isOracleEnabled()) {
            oracleStrategy = NrgOracle.Strategy.BLK_PRICE;
        }

        NRG_ORACLE = new NrgOracle(bc, nrgPriceDefault, nrgPriceMax, oracleStrategy);
    }

    protected long getRecommendedNrgPrice() {
        if (NRG_ORACLE != null) {
            return NRG_ORACLE.getNrgPrice();
        } else {
            return CfgAion.inst().getApi().getNrg().getNrgPriceDefault();
        }
    }

    protected void startES(String thName) {
        ees = new EventExecuteService(100_000, thName, Thread.MIN_PRIORITY, LOG);
        ees.setFilter(setEvtfilter());
        ees.start(new EpApi());
    }

    private Set<Integer> setEvtfilter() {
        Set<Integer> eventSN = new HashSet<>();
        int sn = IHandler.TYPE.BLOCK0.getValue() << 8;
        eventSN.add(sn + EventBlock.CALLBACK.ONBLOCK0.getValue());

        return eventSN;
    }

    protected void shutDownES() {
        ees.shutdown();
    }

    protected Block getBlockWithInfo(long blkNr) {
        if (blkNr == -1) {
            return this.ac.getBlockchain().getBestBlockWithInfo();
        } else if (blkNr >= 0) {
            return this.ac.getBlockchain().getBlockByNumber(blkNr);
        } else {
            LOG.debug("ApiAion.getBlock - incorrect argument");
            return null;
        }
    }
}
