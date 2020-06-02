package org.aion.zero.impl.blockchain;

import static java.lang.Runtime.getRuntime;
import static java.math.BigInteger.TEN;
import static java.math.BigInteger.ZERO;
import static java.util.Collections.emptyList;
import static org.aion.crypto.HashUtil.h256;
import static org.aion.util.biginteger.BIUtil.isMoreThan;
import static org.aion.util.conversions.Hex.toHexString;

import java.util.EnumMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.log.LogUtil;
import org.aion.zero.impl.blockchain.AionHub.BestBlockImportCallback;
import org.aion.zero.impl.blockchain.AionHub.SelfNodeStatusCallback;
import org.aion.zero.impl.core.IDifficultyCalculator;
import static org.aion.zero.impl.core.ImportResult.EXIST;
import static org.aion.zero.impl.core.ImportResult.IMPORTED_BEST;
import static org.aion.zero.impl.core.ImportResult.IMPORTED_NOT_BEST;
import static org.aion.zero.impl.core.ImportResult.INVALID_BLOCK;
import static org.aion.zero.impl.core.ImportResult.NO_PARENT;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.aion.zero.impl.sync.DatabaseType;
import org.aion.zero.impl.types.AionGenesis;
import org.aion.zero.impl.types.GenesisStakingBlock;

import static org.aion.zero.impl.types.BlockUtil.calcReceiptsTrie;
import static org.aion.zero.impl.types.BlockUtil.calcTxTrieRoot;
import static org.aion.zero.impl.types.StakingBlockHeader.GENESIS_SEED;
import static org.aion.zero.impl.valid.BlockDetailsValidator.isValidBlock;
import static org.aion.zero.impl.valid.BlockDetailsValidator.isValidStateRoot;
import static org.aion.zero.impl.valid.BlockDetailsValidator.isValidTxTrieRoot;

import org.aion.zero.impl.valid.AionExtraDataRule;
import org.aion.zero.impl.valid.BlockHeaderRule;
import org.aion.zero.impl.valid.BlockHeaderValidator;
import org.aion.zero.impl.valid.EnergyConsumedRule;
import org.aion.zero.impl.valid.HeaderSealTypeRule;
import org.aion.zero.impl.vm.common.PostExecutionLogic;
import org.aion.zero.impl.vm.common.PostExecutionWork;
import org.aion.zero.impl.vm.common.VmFatalException;
import org.aion.base.AccountState;
import org.aion.base.AionTransaction;
import org.aion.base.ConstantUtil;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.db.impl.SystemExitCodes;
import org.aion.equihash.EquihashMiner;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.mcf.db.Repository;
import org.aion.mcf.db.RepositoryCache;
import org.aion.zero.impl.core.FastImportResult;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.db.TransactionStore;
import org.aion.zero.impl.forks.ForkUtility;
import org.aion.zero.impl.trie.TrieNodeResult;
import org.aion.zero.impl.types.BlockContext;
import org.aion.zero.impl.types.BlockIdentifier;
import org.aion.zero.impl.types.StakingBlock;
import org.aion.zero.impl.valid.BeaconHashValidator;
import org.aion.zero.impl.types.StakingBlockHeader;
import org.aion.zero.impl.valid.GrandParentBlockHeaderValidator;
import org.aion.zero.impl.valid.GreatGrandParentBlockHeaderValidator;
import org.aion.zero.impl.valid.ParentBlockHeaderValidator;
import org.aion.base.TransactionTypeRule;
import org.aion.base.Bloom;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.util.types.AddressUtils;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.utils.HeapDumper;
import org.aion.zero.impl.vm.common.BlockCachingContext;
import org.aion.zero.impl.vm.common.BulkExecutor;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.energy.AbstractEnergyStrategyLimit;
import org.aion.zero.impl.core.energy.EnergyStrategies;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.impl.types.RetValidPreBlock;
import org.aion.zero.impl.valid.StakingDeltaCalculator;
import org.aion.zero.impl.valid.TXValidator;
import org.aion.zero.impl.valid.TransactionTypeValidator;
import org.aion.zero.impl.types.A0BlockHeader;
import org.aion.base.AionTxExecSummary;
import org.aion.base.AionTxReceipt;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: clean and clarify best block
// bestKnownBlock - block with the highest block number
// pubBestBlock - block with the highest total difficulty
// bestBlock - current best block inside the blockchain implementation

/**
 * Core blockchain consensus algorithms, the rule within this class decide whether the correct chain
 * from branches and dictates the placement of items into {@link AionRepositoryImpl} as well as
 * managing the state trie. This module also collects stats about block propagation, from its point
 * of view.
 *
 * <p>The module is also responsible for generate new blocks, mostly called by {@link EquihashMiner}
 * to generate new blocks to mine. As for receiving blocks, this class interacts with {@link
 * SyncMgr} to manage the importing of blocks from network.
 */
public class AionBlockchainImpl implements IAionBlockchain {

    private static final Logger LOG = LoggerFactory.getLogger(LogEnum.CONS.name());
    private static final Logger GEN_LOG = LoggerFactory.getLogger(LogEnum.GEN.name());
    private static final Logger SURVEY_LOG = LoggerFactory.getLogger(LogEnum.SURVEY.name());
    private static final Logger SYNC_LOG = LoggerFactory.getLogger(LogEnum.SYNC.name());
    private static final Logger TX_LOG = LoggerFactory.getLogger(LogEnum.TX.name());
    private static final int DIFFICULTY_BYTES = 16;
    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    private final BlockHeaderValidator headerValidator;
    private final GrandParentBlockHeaderValidator preUnityGrandParentBlockHeaderValidator;
    private final GreatGrandParentBlockHeaderValidator unityGreatGrandParentBlockHeaderValidator, nonceSeedValidator, nonceSeedDifficultyValidator;;
    private final ParentBlockHeaderValidator preUnityParentBlockHeaderValidator;
    private final ParentBlockHeaderValidator unityParentBlockHeaderValidator;
    private StakingContractHelper stakingContractHelper = null;
    public final ForkUtility forkUtility;
    public final BeaconHashValidator beaconHashValidator;
    private final boolean isMainnet;

    /**
     * Chain configuration class, because chain configuration may change dependant on the block
     * being executed. This is simple for now but in the future we may have to create a "chain
     * configuration provider" to provide us different configurations.
     */
    protected ChainConfiguration chainConfiguration;

    private A0BCConfig config;
    private AionRepositoryImpl repository;
    private RepositoryCache<AccountState> track;
    private TransactionStore transactionStore;
    private Block bestBlock;
    private StakingBlock bestStakingBlock;
    private AionBlock bestMiningBlock;
    /**
     * This version of the bestBlock is only used for external reference (ex. through {@link
     * #getBestBlock()}), this is done because {@link #bestBlock} can slip into temporarily
     * inconsistent states while forking, and we don't want to expose that information to external
     * actors.
     *
     * <p>However we would still like to publish a bestBlock without locking, therefore we introduce
     * a volatile block that is only published when all forking/appending behaviour is completed.
     */
    private volatile Block pubBestBlock;

    /** use AtomicReference to make sure the difficulty update at the same time */
    private AtomicReference<BigInteger> totalDifficulty = new AtomicReference<>(ZERO);

    private AtomicReference<BlockIdentifier> bestKnownBlock = new AtomicReference<>();
    private boolean fork = false;
    private AionAddress minerCoinbase;
    private byte[] minerExtraData;
    private Stack<State> stateStack = new Stack<>();
    private IEventMgr evtMgr;
    private AbstractEnergyStrategyLimit energyLimitStrategy;
    private AtomicLong bestBlockNumber = new AtomicLong(0L);

    // fields used to manage AVM caching
    // TODO: if refactoring the add(Block) method, these should be used as parameters
    protected BlockCachingContext executionTypeForAVM = BlockCachingContext.MAINCHAIN;
    protected long cachedBlockNumberForAVM = 0L;
    private static final long NO_FORK_LEVEL = -1L;
    private long forkLevel = NO_FORK_LEVEL;

    private final boolean storeInternalTransactions;
    //TODO : [unity] find the proper number for chaching the template.
    final Map<ByteArrayWrapper, StakingBlock> stakingBlockTemplate = Collections.synchronizedMap(new LRUMap<>(64));
    final Map<ByteArrayWrapper, AionBlock> miningBlockTemplate = Collections.synchronizedMap(new LRUMap<>(64));

    private SelfNodeStatusCallback callback;
    private BestBlockImportCallback bestBlockCallback;
    ReentrantLock lock = new ReentrantLock();
    private AtomicBoolean shutDownFlag = new AtomicBoolean();

    /**
     * The constructor for the blockchain initialization {@see AionHub}.
     */
    public AionBlockchainImpl(CfgAion cfgAion, IEventMgr eventMgr, boolean forTest) {
        this(generateBCConfig(cfgAion), AionRepositoryImpl.inst(),
            forTest ? new ChainConfiguration() {
                    /*
                     * Remove the equiHash solution for the simplified
                     * validator this gives us the ability to connect new
                     * blocks without validating the solution and POW.
                     *
                     * This is good for transaction testing, but another set
                     * of tests need to ensure that the equihash and POW
                     * generated are valid.
                     */
                    @Override
                    public BlockHeaderValidator createBlockHeaderValidator() {

                        List<BlockHeaderRule> powRules =
                                Arrays.asList(
                                        new HeaderSealTypeRule(),
                                        new AionExtraDataRule(
                                                this.getConstants().getMaximumExtraDataSize()),
                                        new EnergyConsumedRule());

                        List<BlockHeaderRule> posRules =
                                Arrays.asList(
                                        new HeaderSealTypeRule(),
                                        new AionExtraDataRule(
                                                this.getConstants().getMaximumExtraDataSize()),
                                        new EnergyConsumedRule());

                        Map<BlockSealType, List<BlockHeaderRule>> unityRules =
                                new EnumMap<>(BlockSealType.class);
                        unityRules.put(BlockSealType.SEAL_POW_BLOCK, powRules);
                        unityRules.put(BlockSealType.SEAL_POS_BLOCK, posRules);

                        return new BlockHeaderValidator(unityRules);
                    }

                    @Override
                    public BlockHeaderValidator createBlockHeaderValidatorForImport() {
                        return createBlockHeaderValidator();
                    }
                } : new ChainConfiguration(),
            eventMgr);
    }

    /**
     * The constructor for the public constructor {@see AionBlockchainImpl(CfgAion, IEventMgr, boolean)}
     * and the integrating test class {@see StandaloneBlockchain}
     */
    protected AionBlockchainImpl(
            final A0BCConfig config,
            final AionRepositoryImpl repository,
            final ChainConfiguration chainConfig,
            final IEventMgr eventMgr) {

        // TODO AKI-318: this specialized class is very cumbersome to maintain; could be replaced with CfgAion
        this.config = config;
        this.repository = repository;
        this.storeInternalTransactions = config.isInternalTransactionStorageEnabled();

        isMainnet = CfgAion.inst().getGenesis().getHashWrapper().equals(ByteArrayWrapper.fromHex("30793b4ea012c6d3a58c85c5b049962669369807a98e36807c1b02116417f823"));

        /**
         * Because we dont have any hardforks, later on chain configuration must be determined by
         * blockHash and number.
         */
        this.chainConfiguration = chainConfig;
        headerValidator = chainConfiguration.createBlockHeaderValidatorForImport();
        preUnityParentBlockHeaderValidator = chainConfig.createPreUnityParentBlockHeaderValidator();
        unityParentBlockHeaderValidator = chainConfig.createUnityParentBlockHeaderValidator();
        preUnityGrandParentBlockHeaderValidator = chainConfiguration.createPreUnityGrandParentHeaderValidator();
        unityGreatGrandParentBlockHeaderValidator = chainConfiguration.createUnityGreatGrandParentHeaderValidator();
        nonceSeedDifficultyValidator = chainConfiguration.createNonceSeedDifficultyValidator();
        nonceSeedValidator = chainConfiguration.createNonceSeedValidator();

        this.transactionStore = this.repository.getTransactionStore();

        this.minerCoinbase = this.config.getMinerCoinbase();
        if (minerCoinbase == null) {
            LOG.warn("No miner Coinbase!");
        }

        /** Save a copy of the miner extra data */
        byte[] extraBytes = this.config.getExtraData();
        this.minerExtraData =
                new byte[this.chainConfiguration.getConstants().getMaximumExtraDataSize()];
        if (extraBytes.length < this.chainConfiguration.getConstants().getMaximumExtraDataSize()) {
            System.arraycopy(extraBytes, 0, this.minerExtraData, 0, extraBytes.length);
        } else {
            System.arraycopy(
                    extraBytes,
                    0,
                    this.minerExtraData,
                    0,
                    this.chainConfiguration.getConstants().getMaximumExtraDataSize());
        }
        this.energyLimitStrategy = config.getEnergyLimitStrategy();

        this.forkUtility = new ForkUtility(CfgAion.inst().getFork().getProperties(), LOG);

        // initialize beacon hash validator
        this.beaconHashValidator = new BeaconHashValidator(this, this.forkUtility);

        evtMgr = eventMgr;
    }

    /**
     * Helper method for generating the adapter between this class and {@link CfgAion}
     *
     * @param cfgAion
     * @return {@code configuration} instance that directly references the singleton instance of
     *     cfgAion
     */
    private static A0BCConfig generateBCConfig(CfgAion cfgAion) {

        Long blkNum = monetaryUpdateBlkNum(cfgAion.getFork().getProperties());

        BigInteger initialSupply = ZERO;
        for (AccountState as : cfgAion.getGenesis().getPremine().values()) {
            initialSupply = initialSupply.add(as.getBalance());
        }

        ChainConfiguration config = new ChainConfiguration(blkNum, initialSupply);
        return new A0BCConfig() {
            @Override
            public AionAddress getCoinbase() {
                return cfgAion.getGenesis().getCoinbase();
            }

            @Override
            public byte[] getExtraData() {
                return cfgAion.getConsensus().getExtraData().getBytes();
            }

            @Override
            public boolean getExitOnBlockConflict() {
                return true;
                // return cfgAion.getSync().getExitOnBlockConflict();
            }

            @Override
            public AionAddress getMinerCoinbase() {
                return AddressUtils.wrapAddress(cfgAion.getConsensus().getMinerAddress());
            }

            // TODO: hook up to configuration file
            @Override
            public int getFlushInterval() {
                return 1;
            }

            @Override
            public AbstractEnergyStrategyLimit getEnergyLimitStrategy() {
                return EnergyStrategies.getEnergyStrategy(
                        cfgAion.getConsensus().getEnergyStrategy().getStrategy(),
                        cfgAion.getConsensus().getEnergyStrategy(),
                        config);
            }

            @Override
            public boolean isInternalTransactionStorageEnabled() {
                return CfgAion.inst().getDb().isInternalTxStorageEnabled();
            }
        };
    }

    private static Long monetaryUpdateBlkNum(Properties properties) {
        if (properties == null) {
            return null;
        }

        String monetaryForkNum = properties.getProperty("fork0.4.0");
        return monetaryForkNum == null ? null : Long.valueOf(monetaryForkNum);
    }

    /**
     * Returns a {@link PostExecutionWork} object whose {@code doWork()} method will run the
     * provided logic defined in this method. This work is to be applied after each transaction has
     * been run.
     *
     * <p>This "work" is specific to the {@link AionBlockchainImpl#generatePreBlock(Block)}
     * method.
     */
    @VisibleForTesting
    static PostExecutionWork getPostExecutionWorkForGeneratePreBlock(
            Repository repository) {
        PostExecutionLogic logic =
                (topRepository, childRepository, transactionSummary, transaction) -> {
                    if (!transactionSummary.isRejected()) {
                        childRepository.flush();

                        AionTxReceipt receipt = transactionSummary.getReceipt();
                        receipt.setPostTxState(topRepository.getRoot());
                        receipt.setTransaction(transaction);
                    }
                };

        return new PostExecutionWork(repository, logic);
    }

    /**
     * Returns a {@link PostExecutionWork} object whose {@code doWork()} method will run the
     * provided logic defined in this method. This work is to be applied after each transaction has
     * been run.
     *
     * <p>This "work" is specific to the {@link AionBlockchainImpl#applyBlock(Block)} method.
     */
    private static PostExecutionWork getPostExecutionWorkForApplyBlock(Repository repository) {
        PostExecutionLogic logic =
                (topRepository, childRepository, transactionSummary, transaction) -> {
                    childRepository.flush();
                    AionTxReceipt receipt = transactionSummary.getReceipt();
                    receipt.setPostTxState(topRepository.getRoot());
                };

        return new PostExecutionWork(repository, logic);
    }

    /**
     * Referenced only by external
     *
     * <p>Note: If you are making changes to this method and want to use it to track internal state,
     * use {@link #bestBlock} instead
     *
     * @return {@code bestAionBlock}
     * @see #pubBestBlock
     */
    @Override
    public byte[] getBestBlockHash() {
        return getBestBlock().getHash();
    }

    /**
     * Referenced only by external
     *
     * @return {@code positive long} representing the current size
     * @see #pubBestBlock
     */
    @Override
    public long getSize() {
        return getBestBlock().getNumber() + 1;
    }

    @Override
    public Block getBlockByNumber(long blockNr) {
        return repository.getBlockStore().getChainBlockByNumber(blockNr);
    }

    @Override
    public List<Block> getBlocksByNumber(long blockNr) {
        return repository.getBlockStore().getBlocksByNumber(blockNr);
    }

    @Override
    public List<Block> getBlocksByRange(long first, long last) {
        return repository.getBlockStore().getBlocksByRange(first, last);
    }

    @Override
    /* NOTE: only returns receipts from the main chain */
    public AionTxInfo getTransactionInfo(byte[] hash) {

        // Try to get info if the hash is from an invokable transaction
        Map<ByteArrayWrapper, AionTxInfo> infos = getTransactionInfoByAlias(hash);

        // If we didn't find the alias for an invokable
        if (infos == null || infos.isEmpty()) {
            infos = transactionStore.getTxInfo(hash);
        }

        if (infos == null || infos.isEmpty()) {
            return null;
        }

        AionTxInfo txInfo = null;
        // pick up the receipt from the block on the main chain
        for (ByteArrayWrapper blockHash : infos.keySet()) {
            if (!isMainChain(blockHash.toBytes())) {
                continue;
            } else {
                txInfo = infos.get(blockHash);
                break;
            }
        }
        if (txInfo == null) {
            LOG.warn("Can't find block from main chain for transaction " + toHexString(hash));
            return null;
        }

        AionTransaction tx =
                this.getBlockByHash(txInfo.getBlockHash())
                        .getTransactionsList()
                        .get(txInfo.getIndex());
        txInfo.setTransaction(tx);
        return txInfo;
    }

    // returns transaction info (tx receipt) without the transaction embedded in it.
    // saves on db reads for api when processing large transactions
    public AionTxInfo getTransactionInfoLite(byte[] txHash, byte[] blockHash) {
        return transactionStore.getTxInfo(txHash, blockHash);
    }

    private Map<ByteArrayWrapper, AionTxInfo> getTransactionInfoByAlias(byte[] innerHash) {
        Set<ByteArrayWrapper> metaTxHashes = transactionStore.getAliases(innerHash);

        if (metaTxHashes == null) return null; // No aliases found

        Map<ByteArrayWrapper, AionTxInfo> infoList = new HashMap<>();
        for (ByteArrayWrapper metaTxHash : metaTxHashes) {
            Map<ByteArrayWrapper, AionTxInfo> metaTxInfos = transactionStore.getTxInfo(metaTxHash.toBytes());
            if (metaTxInfos != null) {
                infoList.putAll(metaTxInfos);
            }
        }
        return infoList; // Had metaTx hash, but was not found in mainchain
    }

    @Override
    public Block getBlockByHash(byte[] hash) {
        return repository.getBlockStore().getBlockByHash(hash);
    }

    @Override
    public List<byte[]> getListOfHashesEndWith(byte[] hash, int qty) {
        return repository.getBlockStore().getListHashesEndWith(hash, qty < 1 ? 1 : qty);
    }

    @Override
    public List<byte[]> getListOfHashesStartFromBlock(long blockNumber, int qty) {
        // avoiding errors due to negative qty
        qty = qty < 1 ? 1 : qty;

        long bestNumber = bestBlock.getNumber();

        if (blockNumber > bestNumber) {
            return emptyList();
        }

        if (blockNumber + qty - 1 > bestNumber) {
            qty = (int) (bestNumber - blockNumber + 1);
        }

        long endNumber = blockNumber + qty - 1;

        Block block = getBlockByNumber(endNumber);

        List<byte[]> hashes = repository.getBlockStore().getListHashesEndWith(block.getHash(), qty);

        // asc order of hashes is required in the response
        Collections.reverse(hashes);

        return hashes;
    }

    public AionRepositoryImpl getRepository() {
        return repository;
    }

    public void setRepository(AionRepositoryImpl repository) {
        this.repository = repository;
    }

    private State pushState(byte[] bestBlockHash) {
        State push = stateStack.push(new State());
        Block block = repository.getBlockStore().getBlockByHashWithInfo(bestBlockHash);;
        if (block == null) {
            throw new IllegalStateException("BlockStore error, cannot find the block byHash: " + ByteUtil.toHexString(bestBlockHash));
        }

        this.bestBlock = block;
        LOG.debug("pushState bestBlock:{}", bestBlock);

        if (bestBlock.getHeader().getSealType() == BlockSealType.SEAL_POW_BLOCK) {
            bestMiningBlock = (AionBlock) bestBlock;            
            if (forkUtility.isUnityForkActive(bestBlock.getNumber())) {
                bestStakingBlock = (StakingBlock) getBlockByHash(bestBlock.getParentHash());
            } else {
                bestStakingBlock = null;
            }
        } else if (bestBlock.getHeader().getSealType() == BlockSealType.SEAL_POS_BLOCK) {
            bestStakingBlock = (StakingBlock) bestBlock;
            bestMiningBlock = (AionBlock) getBlockByHash(bestBlock.getParentHash());
        } else {
            throw new IllegalStateException("Invalid best block data!");
        }

        totalDifficulty.set(bestBlock.getTotalDifficulty());

        this.repository =
                (AionRepositoryImpl) this.repository.getSnapshotTo(this.bestBlock.getStateRoot());
        return push;
    }

    private void popState() {
        State state = stateStack.pop();
        this.repository = state.savedRepo;
        this.bestBlock = state.savedBest;

        bestMiningBlock = state.savedBestMining;
        bestStakingBlock = state.savedBestStaking;

        totalDifficulty.set(state.td);
    }

    private void dropState() {
        stateStack.pop();
    }

    /**
     * Not thread safe, currently only run in {@link #tryToConnect(Block)}, assumes that the
     * environment is already locked
     *
     * @param blockWrapper
     * @return
     */
    private AionBlockSummary tryConnectAndFork(final BlockWrapper blockWrapper) {
        Block block = blockWrapper.block;

        this.fork = true;
        State savedState = null;
        AionBlockSummary summary = null;
        try {
            savedState = pushState(block.getParentHash());
            summary = add(blockWrapper).getLeft();
            kernelStateUpdate(block, summary);
        } catch (Exception e) {
            LOG.error("Unexpected error: ", e);
        } finally {
            this.fork = false;
        }

        if (summary != null && savedState != null && isMoreThan(totalDifficulty.get(), savedState.td)) {
            if (LOG.isInfoEnabled()) {
                LOG.info(
                        "branching: from = {}/{}, to = {}/{}",
                        savedState.savedBest.getNumber(),
                        toHexString(savedState.savedBest.getHash()),
                        block.getNumber(),
                        toHexString(block.getHash()));
            }

            // main branch become this branch cause we proved that total difficulty is greater
            forkLevel = repository.getBlockStore().reBranch(block);

            // The main repository rebranch
            this.repository = savedState.savedRepo;
            this.repository.syncToRoot(block.getStateRoot());

            // flushing
            flush();

            dropState();

            clearBlockTemplate();
        } else {
            // Stay on previous branch
            popState();
        }

        return summary;
    }

    private void kernelStateUpdate(Block block, AionBlockSummary summary) {
        if (summary != null) {
            updateTotalDifficulty(block);
            summary.setTotalDifficulty(block.getTotalDifficulty());

            storeBlock(block, summary.getReceipts(), summary.getSummaries());

            flush();

            if (forkUtility.isNonceForkBlock(block.getNumber())) {
                BigInteger newDiff = calculateFirstPoSDifficultyAtBlock(block);
                forkUtility.setNonceForkResetDiff(newDiff);
            }
        }
    }

    /**
     * Heuristic for skipping the call to tryToConnect with very large or very small block number.
     */
    public boolean skipTryToConnect(long blockNumber) {
        long current = bestBlockNumber.get();
        return blockNumber > current + 32 || blockNumber < current - 32;
    }

    @Override
    public byte[] getTrieNode(byte[] key, DatabaseType dbType) {
        return repository.getTrieNode(key, dbType);
    }

    @Override
    public Map<ByteArrayWrapper, byte[]> getReferencedTrieNodes(
            byte[] value, int limit, DatabaseType dbType) {
        return repository.getReferencedTrieNodes(value, limit, dbType);
    }

    @Override
    public StakingBlock getBestStakingBlock() {
        return bestStakingBlock;
    }

    @Override
    public AionBlock getBestMiningBlock() {
        return bestMiningBlock;
    }

    //TODO : [unity] redesign the blockstore datastucture can read the staking/mining block directly.
    private void loadBestMiningBlock() {
        if (bestBlock.getHeader().getSealType() == BlockSealType.SEAL_POW_BLOCK) {
            bestMiningBlock = (AionBlock) bestBlock;
        } else if (bestBlock.getHeader().getSealType() == BlockSealType.SEAL_POS_BLOCK) {
            bestMiningBlock = (AionBlock) getBlockByHash(bestBlock.getParentHash());
        } else {
            throw new IllegalStateException("Invalid block type");
        }
    }

    private void loadBestStakingBlock() {
        long bestBlockNumber = bestBlock.getNumber();

        if (bestStakingBlock == null && forkUtility.isUnityForkActive(bestBlockNumber)) {
            if (bestBlock.getHeader().getSealType() == BlockSealType.SEAL_POS_BLOCK) {
                bestStakingBlock = (StakingBlock) bestBlock;
            } else {
                bestStakingBlock = (StakingBlock) getBlockByHash(bestBlock.getParentHash());
            }
        }
    }

    /**
     * Imports a trie node to the indicated blockchain database.
     *
     * @param key the hash key of the trie node to be imported
     * @param value the value of the trie node to be imported
     * @param dbType the database where the key-value pair should be stored
     * @throws IllegalArgumentException if the given key is null
     * @return a {@link TrieNodeResult} indicating the success or failure of the import operation
     */
    public TrieNodeResult importTrieNode(byte[] key, byte[] value, DatabaseType dbType) {
        return repository.importTrieNode(key, value, dbType);
    }

    /**
     * If using TOP pruning we need to check the pruning restriction for the block. Otherwise, there
     * is not prune restriction.
     */
    public boolean hasPruneRestriction() {
        // no restriction when not in TOP pruning mode
        return repository.usesTopPruning();
    }

    /**
     * Heuristic for skipping the call to tryToConnect with block number that was already pruned.
     */
    public boolean isPruneRestricted(long blockNumber) {
        // no restriction when not in TOP pruning mode
        if (!hasPruneRestriction()) {
            return false;
        }
        return blockNumber < bestBlockNumber.get() - repository.getPruneBlockCount() + 1;
    }

    /**
     * Import block without validity checks and creating the state. Cannot be used for storing the
     * pivot which will not have a parent present in the database.
     *
     * @param block the block to be imported
     * @return a result describing the status of the attempted import
     */
    public FastImportResult tryFastImport(final Block block) {
        lock.lock();
        try {
            if (block == null) {
                LOG.debug("Fast sync import attempted with null block or header.");
                return FastImportResult.INVALID_BLOCK;
            }

            if (block.getTimestamp()
                > (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
                + this.chainConfiguration.getConstants().getClockDriftBufferTime())) {
                LOG.debug(
                    "Block {} invalid due to timestamp {}.",
                    block.getShortHash(),
                    block.getTimestamp());
                return FastImportResult.INVALID_BLOCK;
            }

            // check that the block is not already known
            Block known = getBlockByHash(block.getHash());
            if (known != null && known.getNumber() == block.getNumber()) {
                return FastImportResult.KNOWN;
            } else {
                // a child must be present to import the parent
                Block child = getBlockByNumber(block.getNumber() + 1);
                if (child == null || !Arrays.equals(child.getParentHash(), block.getHash())) {
                    return FastImportResult.NO_CHILD;
                } else {
                    // the total difficulty will be updated after the chain is complete
                    repository.getBlockStore().saveBlock(block, ZERO, true);
                    LOG.debug(
                        "Fast sync block saved: number: {}, hash: {}, child: {}",
                        block.getNumber(),
                        block.getShortHash(),
                        child.getShortHash());
                    return FastImportResult.IMPORTED;
                }
            }
        } finally{
            lock.unlock();
        }
    }

    /**
     * Walks though the ancestor blocks starting with the given hash to determine if there is an
     * ancestor missing from storage. Returns the ancestor's hash if one is found missing or {@code
     * null} when the history is complete, i.e. no missing ancestors exist.
     *
     * @param block the first block to be checked if present in the repository
     * @return the ancestor's hash and height if one is found missing or {@code null} when the
     *     history is complete
     * @throws NullPointerException when given a null block as input
     */
    public Pair<ByteArrayWrapper, Long> findMissingAncestor(Block block) {
        Objects.requireNonNull(block);

        // initialize with given parameter
        byte[] currentHash = block.getHash();
        long currentNumber = block.getNumber();

        Block known = getBlockByHash(currentHash);

        while (known != null && known.getNumber() > 0) {
            currentHash = known.getParentHash();
            currentNumber--;
            known = getBlockByHash(currentHash);
        }

        if (known == null) {
            return Pair.of(ByteArrayWrapper.wrap(currentHash), currentNumber);
        } else {
            return null;
        }
    }

    public static long shutdownHook = Long.MAX_VALUE;

    public static boolean enableFullSyncCheck = false;
    public static boolean reachedFullSync = false;

    @VisibleForTesting
    public ImportResult tryToConnect(final Block block) {
        return tryToConnect(new BlockWrapper(block));
    }

    public ImportResult tryToConnect(final BlockWrapper blockWrapper) {
        lock.lock();
        try {
            return tryToConnectWithTimedExecution(blockWrapper).getLeft();
        } finally{
            checkKernelShutdownForCLI();
            lock.unlock();
            checkKernelExit();
        }
    }

    private boolean checkKernelShutdownForCLI() {
        if (bestBlock.getNumber() == shutdownHook) {
            LOG.info("Shutting down and dumping heap as indicated by CLI request since block number {} was reached.", shutdownHook);

            try {
                HeapDumper.dumpHeap(new File(System.currentTimeMillis() + "-heap-report.hprof").getAbsolutePath(), true);
            } catch (Exception e) {
                LOG.error("Unable to dump heap due to exception:", e);
            }

            shutDownFlag.set(true);
            return true;
        } else if (enableFullSyncCheck && reachedFullSync) {
            LOG.info("Shutting down as indicated by CLI request sync to the top {} was reached.", bestBlock.getNumber());
            shutDownFlag.set(true);
            return true;
        }
        return false;
    }

    /**
     * Imports a batch of blocks.
     *
     * @param blockRange the block range to be imported
     * @param peerDisplayId the display identifier for the peer who provided the batch
     * @return a {@link Triple} containing:
     * <ol>
     *     <li>the best block height after the imports,</li>
     *     <li>the set of imported hashes,</li>
     *     <li>the import result for the last imported block</li>
     * </ol>
     */
    public Triple<Long, Set<ByteArrayWrapper>, ImportResult> tryToConnect(final List<Block> blockRange, String peerDisplayId) {

        lock.lock();
        try {
            ImportResult importResult = null;
            Set<ByteArrayWrapper> imported = new HashSet<>();
            for (Block block : blockRange) {
                Pair<ImportResult, Long> result = tryToConnectWithTimedExecution(new BlockWrapper(block));
                importResult = result.getLeft();
                long importTime = result.getRight();

                // printing additional information when debug is enabled
                SYNC_LOG.debug(
                    "<import-status: node = {}, hash = {}, number = {}, txs = {}, block time = {}, result = {}, time elapsed = {} ms, block td = {}, chain td = {}>",
                    peerDisplayId,
                    block.getShortHash(),
                    block.getNumber(),
                    block.getTransactionsList().size(),
                    block.getTimestamp(),
                    importResult,
                    importTime,
                    block.getTotalDifficulty(),
                    getTotalDifficulty());

                if (checkKernelShutdownForCLI()) {
                    break;
                } else if (!importResult.isStored()) {
                    // stop at invalid blocks
                    return Triple.of(bestBlock.getNumber(), imported, importResult);
                } else {
                    imported.add(block.getHashWrapper());
                }
            }
            return Triple.of(bestBlock.getNumber(), imported, importResult);
        } finally{
            lock.unlock();
            checkKernelExit();
        }
    }

    private long surveyTotalImportTime = 0;
    private long surveyLongImportTimeCount = 0;
    private long surveySuperLongImportTimeCount = 0;
    private long surveyLastLogImportTime = System.currentTimeMillis();
    private long surveyTotalImportedBlocks = 0;
    private long surveyLongestImportTime = 0;
    private final static long ONE_SECOND_TO_NANO = TimeUnit.SECONDS.toNanos(1);
    private final static long TEN_SECOND_TO_NANO = TimeUnit.SECONDS.toNanos(10);
    private final static long SIXTY_SECOND_TO_MILLI = TimeUnit.SECONDS.toMillis(60);

    private Pair<ImportResult, Long> tryToConnectWithTimedExecution(final BlockWrapper blockWrapper) {
        long importTime = System.nanoTime();
        ImportResult importResult =
                tryToConnectAndFetchSummary(blockWrapper).getLeft();
        importTime = (System.nanoTime() - importTime);

        blockImportSurvey(importResult.isValid(), importTime);
        return Pair.of(importResult, importTime);
    }

    private void blockImportSurvey(boolean valid, long importTime) {
        if (SURVEY_LOG.isInfoEnabled()) {
            if (valid) {
                surveyLongestImportTime = Math.max(surveyLongestImportTime, importTime);
                surveyTotalImportTime += importTime;
                surveyTotalImportedBlocks++;
                if (importTime >= TEN_SECOND_TO_NANO) {
                    surveySuperLongImportTimeCount++;
                } else if (importTime >= ONE_SECOND_TO_NANO) {
                    surveyLongImportTimeCount++;
                }
            }

            if (System.currentTimeMillis() >= surveyLastLogImportTime + SIXTY_SECOND_TO_MILLI) {
                printBlockImportLog();
                surveyLastLogImportTime = System.currentTimeMillis();
            }
        }
    }

    private void printBlockImportLog() {
        SURVEY_LOG.info("Total import#[{}], importTime[{}]ms, 1s+Import#[{}], 10s+Import#[{}] longestImport[{}]ms",
            surveyTotalImportedBlocks,
            TimeUnit.NANOSECONDS.toMillis(surveyTotalImportTime),
            surveyLongImportTimeCount,
            surveySuperLongImportTimeCount,
            TimeUnit.NANOSECONDS.toMillis(surveyLongestImportTime));
    }

    Pair<ImportResult, AionBlockSummary> tryToConnectAndFetchSummary(BlockWrapper blockWrapper) {

        Block block = blockWrapper.block;
        // Check block exists before processing more rules
        if (!blockWrapper.skipExistCheck // skipped when redoing imports
                && repository.getBlockStore().getMaxNumber() >= block.getNumber()
                && isBlockStored(block.getHash(), block.getNumber())) {

            LOG.debug(
                    "Block already exists hash: {}, number: {}",
                    block.getShortHash(),
                    block.getNumber());

            if (!repository.isValidRoot(block.getStateRoot())) {
                // correct the world state for this block
                recoverWorldState(repository, block);
            }

            if (!repository.isIndexed(block.getHash(), block.getNumber())) {
                // correct the index for this block
                recoverIndexEntry(repository, block);
            }

            // retry of well known block
            return Pair.of(EXIST, null);
        }

        LOG.debug(
                "Try connect block hash: {}, number: {}", block.getShortHash(), block.getNumber());

        final ImportResult ret;

        // The simple case got the block
        // to connect to the main chain
        final AionBlockSummary summary;
        if (bestBlock.isParentOf(block)) {
            repository.syncToRoot(bestBlock.getStateRoot());

            // because the bestBlock is a parent this is the first block of its height
            // unless there was a recent fork it's likely we will add a mainchain block
            if (forkLevel == NO_FORK_LEVEL) {
                executionTypeForAVM = BlockCachingContext.MAINCHAIN;
                cachedBlockNumberForAVM = bestBlock.getNumber();
            } else {
                executionTypeForAVM = BlockCachingContext.SWITCHING_MAINCHAIN;
                cachedBlockNumberForAVM = forkLevel;
            }

            summary = add(blockWrapper).getLeft();
            kernelStateUpdate(block, summary);

            ret = summary == null ? INVALID_BLOCK : IMPORTED_BEST;

            if (executionTypeForAVM == BlockCachingContext.SWITCHING_MAINCHAIN
                    && ret == IMPORTED_BEST) {
                // overwrite recent fork info after this
                forkLevel = NO_FORK_LEVEL;
            }
        } else {
            if (isBlockStored(block.getParentHash(), block.getNumber()-1)) {
                BigInteger oldTotalDiff = getInternalTD();

                // determine if the block parent is main chain or side chain
                long parentHeight = block.getNumber() - 1; // inferred parent number
                if (repository.getBlockStore().isMainChain(block.getParentHash(), parentHeight)) {
                    // main chain parent, therefore can use its number for getting the cache
                    executionTypeForAVM = BlockCachingContext.SIDECHAIN;
                    cachedBlockNumberForAVM = parentHeight;
                } else {
                    // side chain parent, therefore do not know the closes main chain block
                    executionTypeForAVM = BlockCachingContext.DEEP_SIDECHAIN;
                    cachedBlockNumberForAVM = 0;
                }

                summary = tryConnectAndFork(blockWrapper);
                ret =
                        summary == null
                                ? INVALID_BLOCK
                                : (isMoreThan(getInternalTD(), oldTotalDiff)
                                        ? IMPORTED_BEST
                                        : IMPORTED_NOT_BEST);
            } else {
                summary = null;
                ret = NO_PARENT;
            }
        }

        // update best block reference
        if (ret == IMPORTED_BEST) {
            pubBestBlock = bestBlock;

            if (callback != null) {
                callback.updateBlockStatus(block.getNumber(), block.getHash().clone(), block.getTotalDifficulty());
            }

            if (bestBlockCallback != null) {
                long t1 = System.currentTimeMillis();

                bestBlockCallback.applyBlockUpdate(block, summary.getReceipts());

                AionLoggerFactory.getLogger(LogEnum.TX.toString())
                    .debug("Pending state update took {} ms", System.currentTimeMillis() - t1);
            }
        }

        // fire block events
        if (ret.isSuccessful()) {
            if (this.evtMgr != null) {
                List<IEvent> evts = new ArrayList<>();
                IEvent evtOnBlock = new EventBlock(EventBlock.CALLBACK.ONBLOCK0);
                evtOnBlock.setFuncArgs(Collections.singletonList(summary));
                evts.add(evtOnBlock);

                if (ret == IMPORTED_BEST) {
                    IEvent evtOnBest = new EventBlock(EventBlock.CALLBACK.ONBEST0);
                    evtOnBest.setFuncArgs(Arrays.asList(block, summary.getReceipts()));
                    evts.add(evtOnBest);
                }

                this.evtMgr.newEvents(evts);
            }
        }

        if (ret == IMPORTED_BEST) {
            if (TX_LOG.isDebugEnabled()) {
                for (AionTxReceipt receipt : summary.getReceipts()) {
                    if (receipt != null) {
                        byte[] transactionHash = receipt.getTransaction().getTransactionHash();
                        TX_LOG.debug(
                            "Transaction: "
                                + Hex.toHexString(transactionHash)
                                + " was sealed into block #"
                                + block.getNumber());
                    }
                }
            }
        }

        return Pair.of(ret, summary);
    }

    /**
     * Try to import the block without flush the repository
     *
     * @param block the block trying to import
     * @return import result and summary
     */
    public Pair<AionBlockSummary, RepositoryCache> tryImportWithoutFlush(final Block block) {
        repository.syncToRoot(bestBlock.getStateRoot());
        return add(new BlockWrapper(block, false, true, false, true));
    }

    /**
     * Creates a new mining block, if you require more context refer to the blockContext creation
     * method, which allows us to add metadata not usually associated with the block itself.
     *
     * @param parent block
     * @param transactions to be added into the block
     * @param waitUntilBlockTime if we should wait until the specified blockTime before create a new block
     * @return new block
     */
    public AionBlock createNewMiningBlock(
            Block parent, List<AionTransaction> transactions, boolean waitUntilBlockTime) {
        lock.lock();
        try {
            BlockContext newBlockContext = createNewMiningBlockContext(parent, transactions, waitUntilBlockTime);
            return null == newBlockContext ? null : newBlockContext.block;
        } finally{
            lock.unlock();
        }
    }

    /**
     * Creates a new mining block, adding in context/metadata about the block
     *
     * @param parent the parent block
     * @param txs to be added into the block
     * @param waitUntilBlockTime if we should wait until the specified blockTime before create a new
     *     block
     * @see #createNewMiningBlockContext(Block, List, boolean)
     * @return a context with new mining block
     */
    @Override
    public BlockContext createNewMiningBlockContext(
        Block parent, List<AionTransaction> txs, boolean waitUntilBlockTime) {
        lock.lock();
        try {
            final BlockContext blockContext = createNewMiningBlockInternal(
                parent, txs, waitUntilBlockTime, TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
            if(blockContext != null) {
                miningBlockTemplate.put(ByteArrayWrapper.wrap(blockContext.block.getHeader().getMineHash()), blockContext.block);
            }
            return blockContext;
        } finally{
            lock.unlock();
        }
    }

    BlockContext createNewMiningBlockInternal(
        Block parent,
        List<AionTransaction> txs,
        boolean waitUntilBlockTime,
        long currTimeSeconds) {

        BlockHeader parentHdr = parent.getHeader();

        long time = currTimeSeconds;
        if (parentHdr.getTimestamp() >= time) {
            time = parentHdr.getTimestamp() + 1;
            while (waitUntilBlockTime && TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) <= time) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
        long energyLimit = this.energyLimitStrategy.getEnergyLimit(parentHdr);

        AionBlock block;

        try {
            A0BlockHeader.Builder headerBuilder =
                A0BlockHeader.Builder.newInstance()
                    .withParentHash(parent.getHash())
                    .withCoinbase(minerCoinbase)
                    .withNumber(parentHdr.getNumber() + 1)
                    .withTimestamp(time)
                    .withExtraData(minerExtraData)
                    .withTxTrieRoot(calcTxTrieRoot(txs))
                    .withEnergyLimit(energyLimit)
                    .withDefaultStateRoot()
                    .withDefaultReceiptTrieRoot()
                    .withDefaultLogsBloom()
                    .withDefaultDifficulty()
                    .withDefaultNonce()
                    .withDefaultSolution();

            block = new AionBlock(headerBuilder.build(), txs);
        } catch (Exception e) {
            LOG.error("Construct new mining block header exception:", e);
            return null;
        }
        
        BlockHeader parentMiningBlock;
        BlockHeader parentMiningBlocksParent = null;
        byte[] newDiff;
        IDifficultyCalculator diffCalculator;
        
        // We want the fork block itself to be a PoW block subject to the old pre-Unity rules, 
        // so we use a strict greater than here
        if (forkUtility.isUnityForkActive(block.getNumber())) {
            if (parentHdr.getSealType() == BlockSealType.SEAL_POW_BLOCK) {
                LOG.warn("Tried to create 2 PoW blocks in a row");
                return null;
            } else {
                Block[] blockFamily = repository.getBlockStore().getTwoGenerationBlocksByHashWithInfo(parentHdr.getParentHash());
                Objects.requireNonNull(blockFamily[0]);
                parentMiningBlock = blockFamily[0].getHeader();
                parentMiningBlocksParent = blockFamily[1].getHeader();
                diffCalculator = chainConfiguration.getUnityDifficultyCalculator();
            }
        } else {
            parentMiningBlock = parentHdr;
            if (!parentMiningBlock.isGenesis()) {
                parentMiningBlocksParent = getParent(parentMiningBlock).getHeader();
            }
            diffCalculator = chainConfiguration.getPreUnityDifficultyCalculator();
        }
        newDiff = ByteUtil.bigIntegerToBytes(diffCalculator.calculateDifficulty(parentMiningBlock, parentMiningBlocksParent), DIFFICULTY_BYTES);
        block.updateHeaderDifficulty(newDiff);

        BigInteger totalTransactionFee = blockPreSeal(parentHdr, block);
        if (totalTransactionFee == null) {
            return null;
        }

        // derive base block reward
        BigInteger baseBlockReward =
                this.chainConfiguration
                        .getRewardsCalculator(forkUtility.isUnityForkActive(block.getHeader().getNumber()))
                        .calculateReward(block.getHeader().getNumber());
        return new BlockContext(block, baseBlockReward, totalTransactionFee);
    }
    
    private BigInteger calculateFirstPoSDifficultyAtBlock(Block block) {
        if (!forkUtility.isUnityForkBlock(block.getNumber()) && !forkUtility.isNonceForkBlock(block.getNumber())) {
            throw new IllegalArgumentException("This cannot be the parent of the first PoS block");
        } else {
            byte[] stateRoot = block.getStateRoot();
            AccountState accountState = (AccountState) repository.getSnapshotTo(stateRoot).getAccountState(getStakingContractHelper().getStakingContractAddress());
            return accountState.getBalance().multiply(TEN);
        }
    }

    private StakingBlock createNewStakingBlock(
            Block parent, List<AionTransaction> txs, byte[] newSeed, byte[] signingPublicKey, byte[] coinbase) {
        BlockHeader parentHdr = parent.getHeader();
        byte[] sealedSeed = newSeed;

        if (!forkUtility.isUnityForkActive(parentHdr.getNumber() + 1)) {
            LOG.debug("Unity fork has not been enabled! Can't create the staking blocks");
            return null;
        }

        byte[] parentSeed;
        BigInteger newDiff;

        if (parentHdr.getSealType() == BlockSealType.SEAL_POS_BLOCK) {
            LOG.warn("Tried to create 2 PoS blocks in a row");
            return null;
        } else if (parentHdr.getSealType() == BlockSealType.SEAL_POW_BLOCK) {

            if (forkUtility.isUnityForkBlock(parentHdr.getNumber())) {
                // this is the first PoS block, use all zeroes as seed, and totalStake / 10 as difficulty
                parentSeed = GENESIS_SEED;
                newDiff = calculateFirstPoSDifficultyAtBlock(parent);
            } else if (forkUtility.isNonceForkBlock(parentHdr.getNumber())) {
                BlockHeader parentStakingBlock = getParent(parentHdr).getHeader();
                parentSeed = ((StakingBlockHeader) parentStakingBlock).getSeed();
                newDiff = calculateFirstPoSDifficultyAtBlock(parent);
                forkUtility.setNonceForkResetDiff(newDiff);
            } else {
                Block[] blockFamily = repository.getBlockStore().getTwoGenerationBlocksByHashWithInfo(parentHdr.getParentHash());
                Objects.requireNonNull(blockFamily[0]);
                BlockHeader parentStakingBlock = blockFamily[0].getHeader();
                BlockHeader parentStakingBlocksParent = blockFamily[1].getHeader();
                parentSeed = ((StakingBlockHeader) parentStakingBlock).getSeed();
                newDiff = chainConfiguration.getUnityDifficultyCalculator().calculateDifficulty(parentStakingBlock, parentStakingBlocksParent);
            }
        } else {
            throw new IllegalStateException("Invalid block type");
        }


        long newTimestamp;

        AionAddress coinbaseAddress = new AionAddress(coinbase);
        if (signingPublicKey != null) { // Create block template for the external stakers.
            if (!ECKeyEd25519.verify(parentSeed, newSeed, signingPublicKey)) {
                LOG.debug(
                        "Seed verification failed! oldSeed:{} newSeed{} pKey{}",
                        ByteUtil.toHexString(parentSeed),
                        ByteUtil.toHexString(newSeed),
                        ByteUtil.toHexString(signingPublicKey));
                return null;
            }

            if (forkUtility.isNonceForkActive(parentHdr.getNumber() + 1)) {
                // new seed generation
                BlockHeader parentStakingBlock = getParent(parentHdr).getHeader();

                // retrieve components
                parentSeed = ((StakingBlockHeader) parentStakingBlock).getSeed();
                byte[] signerAddress = new AionAddress(AddressSpecs.computeA0Address(signingPublicKey)).toByteArray();;
                byte[] powMineHash = ((AionBlock) parent).getHeader().getMineHash();
                byte[] powNonce = ((AionBlock) parent).getNonce();
                int lastIndex = parentSeed.length + signerAddress.length + powMineHash.length + powNonce.length;
                byte[] concatenated = new byte[lastIndex + 1];
                System.arraycopy(parentSeed, 0, concatenated, 0, parentSeed.length);
                System.arraycopy(signerAddress, 0, concatenated, parentSeed.length, signerAddress.length);
                System.arraycopy(powMineHash, 0, concatenated, parentSeed.length + signerAddress.length, powMineHash.length);
                System.arraycopy(powNonce, 0, concatenated, parentSeed.length + signerAddress.length + powMineHash.length, powNonce.length);

                concatenated[lastIndex] = 0;
                byte[] hash1 = h256(concatenated);
                concatenated[lastIndex] = 1;
                byte[] hash2 = h256(concatenated);

                sealedSeed = new byte[hash1.length + hash2.length];
                System.arraycopy(hash1, 0, sealedSeed, 0, hash1.length);
                System.arraycopy(hash2, 0, sealedSeed, hash1.length, hash2.length);
            }

            AionAddress signingAddress = new AionAddress(AddressSpecs.computeA0Address(signingPublicKey));
            BigInteger stakes = null;

            try {
                stakes = getStakingContractHelper().getEffectiveStake(signingAddress, coinbaseAddress, parent);
            } catch (Exception e) {
                LOG.error("Shutdown due to a fatal error encountered while getting the effective stake.", e);
                System.exit(SystemExitCodes.FATAL_VM_ERROR);
            }

            if (stakes.signum() < 1) {
                LOG.debug(
                        "The caller {} with coinbase {} has no stake ",
                        signingAddress.toString(),
                        coinbase.toString());
                return null;
            }

            long newDelta = StakingDeltaCalculator.calculateDelta(sealedSeed, newDiff, stakes);

            newTimestamp =
                    Long.max(
                            parent.getHeader().getTimestamp() + newDelta,
                            parent.getHeader().getTimestamp() + 1);
        } else {
            newTimestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
            if (parentHdr.getTimestamp() >= newTimestamp) {
                newTimestamp = parentHdr.getTimestamp() + 1;
            }
        }

        StakingBlock block;
        try {
            StakingBlockHeader.Builder headerBuilder =
                    StakingBlockHeader.Builder.newInstance()
                            .withParentHash(parent.getHash())
                            .withCoinbase(coinbaseAddress)
                            .withNumber(parentHdr.getNumber() + 1)
                            .withTimestamp(newTimestamp)
                            .withExtraData(minerExtraData)
                            .withTxTrieRoot(calcTxTrieRoot(txs))
                            .withEnergyLimit(energyLimitStrategy.getEnergyLimit(parentHdr))
                            .withDifficulty(ByteUtil.bigIntegerToBytes(newDiff, DIFFICULTY_BYTES))
                            .withSeed(sealedSeed)
                            .withDefaultStateRoot()
                            .withDefaultReceiptTrieRoot()
                            .withDefaultLogsBloom()
                            .withDefaultSignature()
                            .withDefaultSigningPublicKey();
            if (signingPublicKey != null) {
                headerBuilder.withSigningPublicKey(signingPublicKey);
            }

            block = new StakingBlock(headerBuilder.build(), txs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        BigInteger transactionFee = blockPreSeal(parentHdr, block);
        if (transactionFee == null) {
            return null;
        }

        if (signingPublicKey != null) {
            stakingBlockTemplate.putIfAbsent(
                ByteArrayWrapper.wrap(block.getHeader().getMineHash()), block);
        }

        LOG.debug("GetBlockTemp: {}", block.toString());
        return block;
    }

    private BigInteger blockPreSeal(BlockHeader parentHdr, Block block) {
        lock.lock();
        try {
            // Begin execution phase
            pushState(parentHdr.getHash());
            track = repository.startTracking();
            RetValidPreBlock preBlock = generatePreBlock(block);
            track.flush();

            // Calculate the gas used for the included transactions
            long totalEnergyUsed = 0;
            BigInteger totalTransactionFee = BigInteger.ZERO;
            for (AionTxExecSummary summary : preBlock.summaries) {
                totalEnergyUsed = totalEnergyUsed + summary.getNrgUsed().longValueExact();
                totalTransactionFee = totalTransactionFee.add(summary.getFee());
            }

            byte[] stateRoot = getRepository().getRoot();
            popState();

            // End execution phase
            Bloom logBloom = new Bloom();
            for (AionTxReceipt receipt : preBlock.receipts) {
                logBloom.or(receipt.getBloomFilter());
            }

            block.updateTransactionAndState(
                    preBlock.txs,
                    calcTxTrieRoot(preBlock.txs),
                    stateRoot,
                    logBloom.getBloomFilterBytes(),
                    calcReceiptsTrie(preBlock.receipts),
                    totalEnergyUsed);

            return totalTransactionFee;
        } catch (IllegalStateException e) {
            LOG.error("blockPreSeal failed.", e);
            popState();
            return null;
        } finally{
            lock.unlock();
        }
    }

    /** @Param flushRepo true for the kernel runtime import and false for the DBUtil */
    private Pair<AionBlockSummary, RepositoryCache> add(BlockWrapper blockWrapper) {
        // reset cached VMs before processing the block
        repository.clearCachedVMs();

        Block block = blockWrapper.block;
        if (!blockWrapper.validatedHeader && !isValid(block)) {
            LOG.error("Attempting to add {} block.", (block == null ? "NULL" : "INVALID"));
            return Pair.of(null, null);
        }

        track = repository.startTracking();
        byte[] origRoot = repository.getRoot();

        // (if not reconstructing old blocks) keep chain continuity
        if (!blockWrapper.reBuild && !Arrays.equals(bestBlock.getHash(), block.getParentHash())) {
            LOG.error("Attempting to add NON-SEQUENTIAL block.");
            return Pair.of(null, null);
        }

        if (blockWrapper.reBuild) {
            // when recovering blocks do not touch the cache
            executionTypeForAVM = BlockCachingContext.DEEP_SIDECHAIN;
            cachedBlockNumberForAVM = 0;
        }

        AionBlockSummary summary = processBlock(block);
        List<AionTxExecSummary> transactionSummaries = summary.getSummaries();
        List<AionTxReceipt> receipts = summary.getReceipts();

        if (!isValidBlock(block, transactionSummaries, receipts, isException(block.getNumber()), LOG)) {
            track.rollback();
            return Pair.of(null, null);
        }

        if (blockWrapper.skipRepoFlush) {
            return Pair.of(summary, track);
        }

        track.flush();
        repository.commitCachedVMs(block.getHashWrapper());

        if (blockWrapper.reBuild) {
            List<AionTxExecSummary> execSummaries = summary.getSummaries();

            for (int i = 0; i < receipts.size(); i++) {
                AionTxInfo infoWithInternalTxs = AionTxInfo.newInstanceWithInternalTransactions(receipts.get(i), block.getHashWrapper(), i, execSummaries.get(i).getInternalTransactions());

                if (storeInternalTransactions) {
                    transactionStore.putTxInfoToBatch(infoWithInternalTxs);
                } else {
                    AionTxInfo info = AionTxInfo.newInstance(receipts.get(i), block.getHashWrapper(), i);
                    transactionStore.putTxInfoToBatch(info);
                }

                if (execSummaries.get(i).getInternalTransactions().size() > 0) {
                    transactionStore.putAliasesToBatch(infoWithInternalTxs);
                }
            }
            transactionStore.flushBatch();

            repository.commitBlock(block.getHashWrapper(), block.getNumber(), block.getStateRoot());

            LOG.debug(
                    "Block rebuilt: number: {}, hash: {}, TD: {}",
                    block.getNumber(),
                    block.getShortHash(),
                    getTotalDifficulty());
        } else {
            if (!isValidStateRoot(block, repository.getRoot(), LOG)) {
                // block is bad so 'rollback' the state root to the original state
                repository.setRoot(origRoot);
                return Pair.of(null, null);
            }
        }

        return Pair.of(summary, null);
    }

    /**
     * Checks if the block is exempt from the transaction rejection check and requires a different logic for the energy used for rejected transactions.
     *
     * @param blockNumber the block being validated and checked for exception status
     * @return {@code true} if the blocks is exempt from some validations, {@code false} otherwise
     */
    private boolean isException(long blockNumber) {
        // list of exempt blocks on the mainnet main chain
        return isMainnet && (blockNumber == 4735401L || blockNumber == 4735403L || blockNumber == 4735405L);
    }

    @Override
    public void flush() {
        repository.flush();
        try {
            repository.getBlockStore().flush();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        transactionStore.commit();
    }

    @SuppressWarnings("unused")
    private boolean needFlushByMemory(double maxMemoryPercents) {
        return getRuntime().freeMemory() < (getRuntime().totalMemory() * (1 - maxMemoryPercents));
    }

    private Block getParent(BlockHeader header) {
        return repository.getBlockStore().getBlockByHashWithInfo(header.getParentHash());
    }

    public boolean isValid(BlockHeader header) {
        /*
         * The block header should already be validated at this point by P2P or mining,
         * but we are including the validation in case future import paths forget to add it.
         */
        if (!this.headerValidator.validate(header, LOG)) {
            return false;
        }

        Block[] threeGenParents = repository.getBlockStore().getThreeGenerationBlocksByHashWithInfo(header.getParentHash());
        Block parentBlock = threeGenParents[0];
        if (parentBlock == null) {
            return false;
        }
        Block grandparentBlock = threeGenParents[1];
        Block greatGrandparentBlock = threeGenParents[2];

        if (header.getSealType() == BlockSealType.SEAL_POW_BLOCK) {
            if (forkUtility.isUnityForkActive(header.getNumber())) {
                if (grandparentBlock == null || greatGrandparentBlock == null) {
                    return false;
                }

                return unityParentBlockHeaderValidator.validate(header, parentBlock.getHeader(), LOG, null) &&
                        unityGreatGrandParentBlockHeaderValidator.validate(grandparentBlock.getHeader(), greatGrandparentBlock.getHeader(), header, LOG);
            } else {
                return preUnityParentBlockHeaderValidator.validate(header, parentBlock.getHeader(), LOG, null) &&
                        preUnityGrandParentBlockHeaderValidator.validate(parentBlock.getHeader(), grandparentBlock == null ? null : grandparentBlock.getHeader(), header, LOG);
            }
        } else  if (header.getSealType() == BlockSealType.SEAL_POS_BLOCK) {
            if (!forkUtility.isUnityForkActive(header.getNumber())) {
                LOG.warn("Trying to import a Staking block when the Unity fork is not active.");
                return false;
            }

            if (grandparentBlock == null) {
                LOG.warn("Staking block {} cannot find its grandparent", header.getNumber());
                return false;
            }

            if (forkUtility.isUnityForkBlock(parentBlock.getNumber())) {
                BigInteger expectedDiff = calculateFirstPoSDifficultyAtBlock(parentBlock);
                if (!expectedDiff.equals(header.getDifficultyBI())) {
                    return false;
                }
                grandparentBlock = new GenesisStakingBlock(expectedDiff);
            } else if (forkUtility.isNonceForkBlock(parentBlock.getNumber())) {
                BigInteger expectedDiff = calculateFirstPoSDifficultyAtBlock(parentBlock);
                if (!expectedDiff.equals(header.getDifficultyBI())) {
                    return false;
                }
            }

            BigInteger stake = null;

            try {
                stake = getStakingContractHelper().getEffectiveStake(new AionAddress(AddressSpecs.computeA0Address(((StakingBlockHeader) header).getSigningPublicKey())), ((StakingBlockHeader) header).getCoinbase(), parentBlock);
            } catch (Exception e) {
                LOG.error("Shutdown due to a fatal error encountered while getting the effective stake.", e);
                System.exit(SystemExitCodes.FATAL_VM_ERROR);
            }

            return unityParentBlockHeaderValidator.validate(header, parentBlock.getHeader(), LOG, stake)
                    && (forkUtility.isNonceForkActive(header.getNumber())
                            ? (nonceSeedValidator.validate(grandparentBlock.getHeader(), parentBlock.getHeader(), header, LOG)
                                    && (forkUtility.isNonceForkBlock(header.getNumber() - 1)
                                            ? header.getDifficultyBI().equals(forkUtility.getNonceForkResetDiff())
                                            : nonceSeedDifficultyValidator.validate(grandparentBlock.getHeader(), greatGrandparentBlock.getHeader(), header, LOG)))
                            : unityGreatGrandParentBlockHeaderValidator.validate(grandparentBlock.getHeader(), greatGrandparentBlock.getHeader(), header, LOG));
        } else {
            LOG.debug("Invalid header seal type!");
            return false;

        }
    }

    /**
     * This mechanism enforces a homeostasis in terms of the time between blocks; a smaller period
     * between the last two blocks results in an increase in the difficulty level and thus
     * additional computation required, lengthening the likely next period. Conversely, if the
     * period is too large, the difficulty, and expected time to the next block, is reduced.
     */
    private boolean isValid(Block block) {

        if (block == null) {
            return false;
        }

        if (!block.isGenesis()) {
            if (!isValid(block.getHeader())) {
                LOG.warn("Block {} has an invalid block header", block.getNumber());
                return false;
            }

            List<AionTransaction> txs = block.getTransactionsList();
            if (!isValidTxTrieRoot(block.getTxTrieRoot(), txs, block.getNumber(), LOG)) {
                return false;
            }

            if (!txs.isEmpty()) {
                Repository parentRepo = repository;

                if (!Arrays.equals(bestBlock.getHash(), block.getParentHash())) {
                    parentRepo =
                            repository.getSnapshotTo(
                                    getBlockByHash(block.getParentHash()).getStateRoot());
                }

                Map<AionAddress, BigInteger> nonceCache = new HashMap<>();

                boolean unityForkEnabled = forkUtility.isUnityForkActive(block.getNumber());
                if (txs.parallelStream()
                        .anyMatch(
                                tx ->
                                    TXValidator.validateTx(tx, unityForkEnabled).isFail()
                                                || !TransactionTypeValidator.isValid(tx)
                                                || !beaconHashValidator.validateTxForBlock(tx, block))) {
                    LOG.error("Some transactions in the block are invalid");

                    for (AionTransaction tx : txs) {
                        TX_LOG.debug(
                                "Tx valid ["
                                        + TXValidator.validateTx(tx, unityForkEnabled).isSuccess()
                                        + "]. Type valid ["
                                        + TransactionTypeValidator.isValid(tx)
                                        + "]\n"
                                        + tx.toString());
                    }

                    return false;
                }

                for (AionTransaction tx : txs) {
                    AionAddress txSender = tx.getSenderAddress();

                    BigInteger expectedNonce = nonceCache.get(txSender);

                    if (expectedNonce == null) {
                        expectedNonce = parentRepo.getNonce(txSender);
                    }

                    BigInteger txNonce = tx.getNonceBI();
                    if (!expectedNonce.equals(txNonce)) {
                        LOG.warn(
                                "Invalid transaction: Tx nonce {} != expected nonce {} (parent nonce: {}): {}",
                                txNonce.toString(),
                                expectedNonce.toString(),
                                parentRepo.getNonce(txSender),
                                tx);
                        return false;
                    }

                    // update cache
                    nonceCache.put(txSender, expectedNonce.add(BigInteger.ONE));
                }
            }
        }

        return true;
    }

    private AionBlockSummary processBlock(Block block) {

        if (!block.isGenesis()) {
            return applyBlock(block);
        } else {
            return new AionBlockSummary(
                    block, new HashMap<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    /**
     * For generating the necessary transactions for a block
     *
     * @param block
     * @return
     */
    private RetValidPreBlock generatePreBlock(Block block) {

        long saveTime = System.nanoTime();

        List<AionTxReceipt> receipts = new ArrayList<>();
        List<AionTxExecSummary> summaries = new ArrayList<>();
        List<AionTransaction> transactions = new ArrayList<>();

        if (!block.getTransactionsList().isEmpty()) {

            boolean fork040Enable = forkUtility.is040ForkActive(block.getNumber());
            if (fork040Enable) {
                TransactionTypeRule.allowAVMContractTransaction();
            }

            try {
                // Booleans moved out here so their meaning is explicit.
                boolean isLocalCall = false;
                boolean incrementSenderNonce = true;
                boolean checkBlockEnergyLimit = true;

                List<AionTxExecSummary> executionSummaries =
                        BulkExecutor.executeAllTransactionsInBlock(
                                block.getDifficulty(),
                                block.getNumber(),
                                block.getTimestamp(),
                                block.getNrgLimit(),
                                block.getCoinbase(),
                                block.getTransactionsList(),
                                track,
                                isLocalCall,
                                incrementSenderNonce,
                                fork040Enable,
                                checkBlockEnergyLimit,
                                LOGGER_VM,
                                getPostExecutionWorkForGeneratePreBlock(repository),
                                BlockCachingContext.PENDING,
                                bestBlock.getNumber(),
                                forkUtility.isUnityForkActive(block.getNumber()));

                for (AionTxExecSummary summary : executionSummaries) {
                    if (!summary.isRejected()) {
                        transactions.add(summary.getTransaction());
                        receipts.add(summary.getReceipt());
                        summaries.add(summary);
                    }
                }
            } catch (VmFatalException e) {
                LOG.error("Shutdown due to a VM fatal error.", e);
                System.exit(SystemExitCodes.FATAL_VM_ERROR);
            }
        }

        Map<AionAddress, BigInteger> rewards = addReward(block);
        return new RetValidPreBlock(transactions, rewards, receipts, summaries);
    }

    private AionBlockSummary applyBlock(Block block) {
        long saveTime = System.nanoTime();

        List<AionTxReceipt> receipts = new ArrayList<>();
        List<AionTxExecSummary> summaries = new ArrayList<>();

        if (!block.getTransactionsList().isEmpty()) {

            // might apply the block before the 040 fork point.
            boolean fork040Enable = forkUtility.is040ForkActive(block.getNumber());
            if (fork040Enable) {
                TransactionTypeRule.allowAVMContractTransaction();
            }

            try {
                // Booleans moved out here so their meaning is explicit.
                boolean isLocalCall = false;
                boolean incrementSenderNonce = true;
                boolean checkBlockEnergyLimit = false;

                List<AionTxExecSummary> executionSummaries =
                        BulkExecutor.executeAllTransactionsInBlock(
                                block.getDifficulty(),
                                block.getNumber(),
                                block.getTimestamp(),
                                block.getNrgLimit(),
                                block.getCoinbase(),
                                block.getTransactionsList(),
                                track,
                                isLocalCall,
                                incrementSenderNonce,
                                fork040Enable,
                                checkBlockEnergyLimit,
                                LOGGER_VM,
                                getPostExecutionWorkForApplyBlock(repository),
                                executionTypeForAVM,
                                cachedBlockNumberForAVM,
                                forkUtility.isUnityForkActive(block.getNumber()));

                // Check for rejected transaction already included in the chain.
                if (isException(block.getNumber())) {
                    for (AionTxExecSummary summary : executionSummaries) {
                        if (summary.isRejected()) {
                            AionTxReceipt receipt = summary.getReceipt();
                            receipt.setNrgUsed(receipt.getTransaction().getEnergyLimit());
                        }
                    }
                }

                for (AionTxExecSummary summary : executionSummaries) {
                    receipts.add(summary.getReceipt());
                    summaries.add(summary);
                }
            } catch (VmFatalException e) {
                LOG.error("Shutdown due to a VM fatal error.", e);
                System.exit(SystemExitCodes.FATAL_VM_ERROR);
            }
        }
        Map<AionAddress, BigInteger> rewards = addReward(block);
        return new AionBlockSummary(block, rewards, receipts, summaries);
    }

    /**
     * Add reward to block- and every uncle coinbase assuming the entire block is valid.
     *
     * @param block object containing the header and uncles
     */
    private Map<AionAddress, BigInteger> addReward(Block block) {

        Map<AionAddress, BigInteger> rewards = new HashMap<>();
        BigInteger minerReward =
                this.chainConfiguration
                        .getRewardsCalculator(forkUtility.isUnityForkActive(block.getHeader().getNumber()))
                        .calculateReward(block.getHeader().getNumber());
        rewards.put(block.getCoinbase(), minerReward);

        if (LOG.isTraceEnabled()) {
            LOG.trace(
                    "rewarding: {}np to {} for mining block {}",
                    minerReward,
                    block.getCoinbase(),
                    block.getNumber());
        }

        /*
         * Remaining fees (the ones paid to miners for running transactions) are
         * already paid for at a earlier point in execution.
         */
        track.addBalance(block.getCoinbase(), minerReward);
        return rewards;
    }

    public ChainConfiguration getChainConfiguration() {
        return chainConfiguration;
    }

    private void storeBlock(Block block, List<AionTxReceipt> receipts, List<AionTxExecSummary> summaries) {

        BigInteger td = totalDifficulty.get();

        repository.getBlockStore().saveBlock(block, td, !fork);

        for (int i = 0; i < receipts.size(); i++) {
            AionTxInfo infoWithInternalTxs = AionTxInfo.newInstanceWithInternalTransactions(receipts.get(i), block.getHashWrapper(), i, summaries.get(i).getInternalTransactions());

            if (storeInternalTransactions) {
                transactionStore.putTxInfoToBatch(infoWithInternalTxs);
            } else {
                AionTxInfo info = AionTxInfo.newInstance(receipts.get(i), block.getHashWrapper(), i);
                transactionStore.putTxInfoToBatch(info);
            }

            if (summaries.get(i).getInternalTransactions().size() > 0) {
                transactionStore.putAliasesToBatch(infoWithInternalTxs);
            }
        }
        transactionStore.flushBatch();

        repository.commitBlock(block.getHashWrapper(), block.getNumber(), block.getStateRoot());

        LOG.debug(
                "Block saved: number: {}, hash: {}, {}",
                block.getNumber(),
                block.getShortHash(),
                td.toString());
        LOG.debug("block added to the blockChain: index: [{}]", block.getNumber());

        setBestBlock(block);
    }

    @Override
    public int storePendingBlockRange(List<Block> blocks) {
        try {
            return repository.getPendingBlockStore().addBlockRange(blocks);
        } catch (Exception e) {
            LOG.error(
                    "Unable to store range of blocks in " + repository.toString() + " due to: ", e);
            return 0;
        }
    }

    @Override
    public Map<ByteArrayWrapper, List<Block>> loadPendingBlocksAtLevel(long level) {
        try {
            return repository.getPendingBlockStore().loadBlockRange(level);
        } catch (Exception e) {
            LOG.error(
                    "Unable to retrieve stored blocks from " + repository.toString() + " due to: ",
                    e);
            return Collections.emptyMap();
        }
    }

    @Override
    public void dropImported(
            long level,
            List<ByteArrayWrapper> ranges,
            Map<ByteArrayWrapper, List<Block>> blocks) {
        try {
            repository.getPendingBlockStore().dropPendingQueues(level, ranges, blocks);
        } catch (Exception e) {
            LOG.error(
                    "Unable to delete used blocks from " + repository.toString() + " due to: ", e);
            return;
        }
    }

    public TransactionStore getTransactionStore() {
        return transactionStore;
    }

    @Override
    public Block getBestBlock() {
        return pubBestBlock == null ? bestBlock : pubBestBlock;
    }

    @Override
    public void setBestBlock(Block block) {
        lock.lock();
        try {
            bestBlock = block;
            if (bestBlock instanceof AionBlock) {
                bestMiningBlock = (AionBlock) bestBlock;
            } else if (bestBlock instanceof StakingBlock) {
                bestStakingBlock = (StakingBlock) bestBlock;
            } else {
                throw new IllegalStateException("Invalid Block instance");
            }

            LOG.debug("BestBlock {}", bestBlock);
            LOG.debug("BestMiningBlock {}", bestMiningBlock);
            LOG.debug("BestStakingBlock {}", bestStakingBlock);

            updateBestKnownBlock(bestBlock.getHeader().getHash(), bestBlock.getHeader().getNumber());
            bestBlockNumber.set(bestBlock.getNumber());
        } finally{
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            // The main repository instance is stashed when the snapshot is created. If the current repository is a snapshot that means the main one is in the stack.
            // We pop the stack until we get to the main repository instance that contains access too all the databases that must be closed.
            while (repository.isSnapshot()) {
                popState();
            }

            // We do not flush before closing the database because under normal circumstances the repository was already flushed.
            // If close was called due to an error (like a VM issue) then flushing may store corrupt data, so it shouldn't be done.
            GEN_LOG.info("shutting down DB...");
            repository.close();
            GEN_LOG.info("shutdown DB... Done!");

            printBlockImportLog();
        } finally{
            lock.unlock();
        }
    }

    @Override
    public BigInteger getTotalDifficulty() {
        return getBestBlock().getTotalDifficulty();
    }

    @Override
    public void setTotalDifficulty(BigInteger totalDifficulty) {
        if (totalDifficulty == null) {
            throw new NullPointerException();
        }
        this.totalDifficulty.set(totalDifficulty);
    }

    @VisibleForTesting
    protected BigInteger getCacheTD() {
        return totalDifficulty.get();
    }

    private BigInteger getInternalTD() {
        return totalDifficulty.get();
    }

    private void updateTotalDifficulty(Block block) {
        BigInteger newTotalDifficulty = totalDifficulty.get().add(block.getDifficultyBI());
        totalDifficulty.set(newTotalDifficulty);
        block.setTotalDifficulty(newTotalDifficulty);
        LOG.debug("UnityDifficulty updated: {}", newTotalDifficulty.toString());
    }

    @Override
    public AionAddress getMinerCoinbase() {
        return minerCoinbase;
    }

    @Override
    public boolean isBlockStored(byte[] hash, long number) {
        return getRepository().isBlockStored(hash, number);
    }

    /**
     * Returns up to limit headers found with following search parameters
     *
     * @param blockNumber Identifier of start block, by number
     * @param limit Maximum number of headers in return
     * @return {@link A0BlockHeader}'s list or empty list if none found
     */
    @Override
    public List<BlockHeader> getListOfHeadersStartFrom(long blockNumber, int limit) {
        if (limit <= 0) {
            return emptyList();
        }

        // identifying block we'll move from
        Block startBlock = getBlockByNumber(blockNumber);

        // if nothing found on main chain, return empty array
        if (startBlock == null) {
            return emptyList();
        }

        List<BlockHeader> headers;
        long bestNumber = bestBlock.getNumber();
        headers = getContinuousHeaders(bestNumber, blockNumber, limit);

        return headers;
    }

    /**
     * Finds up to limit blocks starting from blockNumber on main chain
     *
     * @param bestNumber Number of best block
     * @param blockNumber Number of block to start search (included in return)
     * @param limit Maximum number of headers in response
     * @return headers found by query or empty list if none
     */
    private List<BlockHeader> getContinuousHeaders(long bestNumber, long blockNumber, int limit) {
        int qty = getQty(blockNumber, bestNumber, limit);

        byte[] startHash = getStartHash(blockNumber, qty);

        if (startHash == null) {
            return emptyList();
        }

        List<BlockHeader> headers = repository.getBlockStore().getListHeadersEndWith(startHash, qty);

        // blocks come with decreasing numbers
        Collections.reverse(headers);

        return headers;
    }

    private int getQty(long blockNumber, long bestNumber, int limit) {
        if (blockNumber + limit - 1 > bestNumber) {
            return (int) (bestNumber - blockNumber + 1);
        } else {
            return limit;
        }
    }

    private byte[] getStartHash(long blockNumber, int qty) {

        long startNumber;

        startNumber = blockNumber + qty - 1;

        Block block = getBlockByNumber(startNumber);

        if (block == null) {
            return null;
        }

        return block.getHash();
    }

    private void updateBestKnownBlock(byte[] hash, long number) {
        if (bestKnownBlock.get() == null || number > bestKnownBlock.get().getNumber()) {
            bestKnownBlock.set(new BlockIdentifier(hash, number));
        }
    }

    /**
     * Recovery functionality for rebuilding the world state.
     *
     * @return {@code true} if the recovery was successful, {@code false} otherwise
     */
    public boolean recoverWorldState(Repository repository, Block block) {
        lock.lock();
        try {
            if (block == null) {
                LOG.error("World state recovery attempted with null block.");
                return false;
            }
            if (repository.isSnapshot()) {
                LOG.error("World state recovery attempted with snapshot repository.");
                return false;
            }

            long blockNumber = block.getNumber();
            LOG.info(
                "Pruned or corrupt world state at block hash: {}, number: {}."
                    + " Looking for ancestor block with valid world state ...",
                block.getShortHash(),
                blockNumber);

            AionRepositoryImpl repo = (AionRepositoryImpl) repository;

            // keeping track of the original root
            byte[] originalRoot = repo.getRoot();

            Deque<Block> dirtyBlocks = new ArrayDeque<>();
            // already known to be missing the state
            dirtyBlocks.push(block);

            Block other = block;

            // find all the blocks missing a world state
            do {
                other = getBlockByHash(other.getParentHash());

                // cannot recover if no valid states exist (must build from genesis)
                if (other == null) {
                    return false;
                } else {
                    dirtyBlocks.push(other);
                }
            } while (!repo.isValidRoot(other.getStateRoot()) && other.getNumber() > 0);

            if (other.getNumber() == 0 && !repo.isValidRoot(other.getStateRoot())) {
                LOG.info("Rebuild state FAILED because a valid state could not be found.");
                return false;
            }

            // sync to the last correct state
            repo.syncToRoot(other.getStateRoot());

            // remove the last added block because it has a correct world state
            dirtyBlocks.pop();

            LOG.info(
                "Valid state found at block hash: {}, number: {}.",
                other.getShortHash(),
                other.getNumber());

            // rebuild world state for dirty blocks
            while (!dirtyBlocks.isEmpty()) {
                other = dirtyBlocks.pop();
                LOG.info(
                    "Rebuilding block hash: {}, number: {}, txs: {}.",
                    other.getShortHash(),
                    other.getNumber(),
                    other.getTransactionsList().size());

                // Load bestblock for executing the CLI command.
                if (bestBlock == null) {
                    bestBlock = repo.getBestBlock();

                    if (bestBlock instanceof AionBlock) {
                        bestMiningBlock = (AionBlock) bestBlock;
                    } else if (bestBlock instanceof StakingBlock) {
                        bestStakingBlock = (StakingBlock) bestBlock;
                    } else {
                        throw new IllegalStateException("Invalid best block!");
                    }
                }

                this.add(new BlockWrapper(other, false, true, true, false));
            }

            // update the repository
            repo.flush();

            // setting the root back to its correct value
            repo.syncToRoot(originalRoot);

            // return a flag indicating if the recovery worked
            return repo.isValidRoot(block.getStateRoot());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Recovery functionality for recreating the block info in the index database.
     *
     * @return {@code true} if the recovery was successful, {@code false} otherwise
     */
    public boolean recoverIndexEntry(AionRepositoryImpl repository, Block block) {
        lock.lock();
        try {
            if (block == null) {
                LOG.error("Index recovery attempted with null block.");
                return false;
            }
            if (repository.isSnapshot()) {
                LOG.error("Index recovery attempted with snapshot repository.");
                return false;
            }

            LOG.info(
                    "Missing index at block hash: {}, number: {}. Looking for ancestor block with valid index ...",
                    block.getShortHash(),
                    block.getNumber());

            boolean isSuccessful = repository.recoverIndexEntry(block, bestBlock, LOG);
            if (isSuccessful) {
                clearBlockTemplate();
            }
            return isSuccessful;
        } finally{
            lock.unlock();
        }
    }

    /**
     * @implNote clear the block Template when the chain re-branched.
     */
    private void clearBlockTemplate() {
        // AKI-652 the block template should be cleaned after chain re-branched
        LOG.debug("Clean the block template, staking#{}, mining#{}.", stakingBlockTemplate.size(), miningBlockTemplate.size());

        stakingBlockTemplate.clear();
        miningBlockTemplate.clear();
    }

    @Override
    public BigInteger getTotalDifficultyForHash(byte[] blockHash) {
        Objects.requireNonNull(blockHash, "The block hash cannot be null.");
        return this.repository.getTotalDifficultyForHash(blockHash);
    }

    private class State {
        AionRepositoryImpl savedRepo = repository;
        Block savedBest = bestBlock;
        AionBlock savedBestMining = bestMiningBlock;
        StakingBlock savedBestStaking = bestStakingBlock;
        BigInteger td = totalDifficulty.get();
    }

    /**
     * @implNote this method only can be called by the aionhub for data recovery purpose.
     * @param blk the best block after database recovered or revered.
     */
    void resetPubBestBlock(Block blk) {
        pubBestBlock = blk;
    }

    @Override
    public boolean isMainChain(byte[] hash, long level) {
        return repository.getBlockStore().isMainChain(hash, level);
    }

    @Override
    public boolean isMainChain(byte[] hash) {
        return repository.getBlockStore().isMainChain(hash);
    }

    @Override
    public boolean isUnityForkEnabledAtNextBlock() {
        return forkUtility.isUnityForkActive(bestBlockNumber.get() + 1);
    }

    @Override
    public BigInteger calculateBlockRewards(long block_number) {
        return chainConfiguration
                .getRewardsCalculator(forkUtility.isUnityForkActive(block_number))
                .calculateReward(block_number);
    }

    /**
     * A method for creating a new staking block template for the external staker.
     *
     * @param pendingTransactions to be added into the block.
     * @param signingPublicKey the staker's signing public key.
     * @param newSeed the data decide the weight of the sealing difficulty.
     * @see #createNewStakingBlock(Block, List, byte[], byte[], byte[])
     * @return staking block template
     */
    @Override
    public StakingBlock createStakingBlockTemplate(Block parent, List<AionTransaction> pendingTransactions, byte[] signingPublicKey, byte[] newSeed, byte[] coinbase) {
        lock.lock();
        try {
            if (pendingTransactions == null) {
                LOG.error("createStakingBlockTemplate failed, The pendingTransactions list can not be null");
                return null;
            }

            if (signingPublicKey == null) {
                LOG.error("createStakingBlockTemplate failed, The signing public key is null");
                return null;
            }

            if (newSeed == null) {
                LOG.error("createStakingBlockTemplate failed, The seed is null");
                return null;
            }

            if (coinbase == null) {
                LOG.error("createStakingBlockTemplate failed, The coinbase is null");
                return null;
            }


            try {
                // Use a snapshot to the given parent.
                pushState(parent.getHash());

                return createNewStakingBlock(parent, pendingTransactions, newSeed, signingPublicKey, coinbase);
            } catch (IllegalStateException e) {
                LOG.error("createStakingBlockTemplate failed.", e);
                return null;
            } finally {
                // Ensures that the repository is left in a valid state even if an exception occurs.
                popState();
            }
        } finally{
            lock.unlock();
        }
    }

    public StakingContractHelper getStakingContractHelper() {
        if (stakingContractHelper == null) {
            stakingContractHelper = new StakingContractHelper(CfgAion.inst().getGenesis().getStakingContractAddress(), this);
        }
        return stakingContractHelper;
    }

    @Override
    public byte[] getSeed() {
        if (null == bestStakingBlock) {
            return GENESIS_SEED;
        } else {
            return bestStakingBlock.getSeed();
        }
    }

    @Override
    public StakingBlock getCachingStakingBlockTemplate(byte[] hash) {
        if (hash == null) {
            throw new NullPointerException("The giving hash is null");
        }

        return stakingBlockTemplate.get(ByteArrayWrapper.wrap(hash));
    }

    @Override
    public AionBlock getCachingMiningBlockTemplate(byte[] hash) {
        if (hash == null) {
            throw new NullPointerException("The given hash is null");//
        }
        return miningBlockTemplate.get(ByteArrayWrapper.wrap(hash));
    }

    @Override
    public Block getBlockWithInfoByHash(byte[] hash) {
        return repository.getBlockStore().getBlockByHashWithInfo(hash);
    }

    @Override
    public Block getBestBlockWithInfo() {
        return repository.getBlockStore().getBestBlockWithInfo();
    }

    void setNodeStatusCallback(SelfNodeStatusCallback callback) {
        this.callback = callback;
    }

    void setBestBlockImportCallback(BestBlockImportCallback callback) {
        bestBlockCallback = callback;
    }

    /**
     * Loads the block chain attempting recovery if necessary and returns the starting block.
     *
     * @param genesis the expected genesis block
     * @param genLOG logger for output messages
     */
    public void load(AionGenesis genesis, Logger genLOG) {
        // function repurposed for integrity checks since previously not implemented
        try {
            repository.getBlockStore().load();
        } catch (RuntimeException re) {
            genLOG.error("Fatal: can't load blockstore; exiting.", re);
            System.exit(org.aion.zero.impl.SystemExitCodes.INITIALIZATION_ERROR);
        }

        // Note: if block DB corruption, the bestBlock may not match with the indexDB.
        Block bestBlock = repository.getBestBlock();

        boolean recovered = true;
        boolean bestBlockShifted = true;
        int countRecoveryAttempts = 0;

        // fix the trie if necessary
        while (bestBlockShifted
                && // the best block was updated after recovery attempt
                (countRecoveryAttempts < 5)
                && // allow 5 recovery attempts
                bestBlock != null
                && // recover only for non-null blocks
                !repository.isValidRoot(bestBlock.getStateRoot())) {

            genLOG.info("Recovery initiated due to corrupt world state at block " + bestBlock.getNumber() + ".");

            long bestBlockNumber = bestBlock.getNumber();
            byte[] bestBlockRoot = bestBlock.getStateRoot();

            // ensure that the genesis state exists before attempting recovery
            if (!repository.isValidRoot(genesis.getStateRoot())) {
                genLOG.info("Corrupt world state for genesis block hash: " + genesis.getShortHash() + ", number: " + genesis.getNumber() + ".");

                repository.buildGenesis(genesis);

                if (repository.isValidRoot(genesis.getStateRoot())) {
                    genLOG.info("Rebuilding genesis block SUCCEEDED.");
                } else {
                    genLOG.info("Rebuilding genesis block FAILED.");
                }
            }

            recovered = recoverWorldState(repository, bestBlock);

            if (recovered && !repository.isIndexed(bestBlock.getHash(), bestBlock.getNumber())) {
                // correct the index for this block
                recovered = recoverIndexEntry(repository, bestBlock);
            }

            long blockNumber = bestBlock.getNumber();
            if (!repository.isValidRoot(bestBlock.getStateRoot())) {
                // reverting back one block
                genLOG.info("Rebuild state FAILED. Reverting to previous block.");

                --blockNumber;
                boolean isSuccessful = getRepository().revertTo(blockNumber, genLOG);

                recovered = isSuccessful && repository.isValidRoot(getBlockByNumber(blockNumber).getStateRoot());
            }

            if (recovered) {
                // reverting block & index DB
                repository.getBlockStore().rollback(blockNumber);

                // new best block after recovery
                bestBlock = repository.getBestBlock();
                if (bestBlock != null) {

                    bestBlock.setTotalDifficulty(getTotalDifficultyForHash(bestBlock.getHash()));
                    // TODO : [unity] The publicbestblock is a weird settings, should consider to remove it.
                    resetPubBestBlock(bestBlock);
                } else {
                    genLOG.error("Recovery failed! please re-import your database by ./aion.sh -n <network> --redo-import, it will take a while.");
                    throw new IllegalStateException("Recovery failed due to database corruption.");
                }

                // checking is the best block has changed since attempting recovery
                bestBlockShifted = !(bestBlockNumber == bestBlock.getNumber()) // block number changed
                                || !(Arrays.equals(bestBlockRoot, bestBlock.getStateRoot())); // root hash changed

                if (bestBlockShifted) {
                    genLOG.info("Rebuilding world state SUCCEEDED by REVERTING to a previous block.");
                } else {
                    genLOG.info("Rebuilding world state SUCCEEDED.");
                }
            } else {
                genLOG.error("Rebuilding world state FAILED. "
                                + "Stop the kernel (Ctrl+C) and use the command line revert option to move back to a valid block. "
                                + "Check the Aion wiki for recommendations on choosing the block number.");
            }

            countRecoveryAttempts++;
        }

        // rebuild from genesis if (1) no best block (2) recovery failed
        if (bestBlock == null || !recovered) {
            if (bestBlock == null) {
                genLOG.info("DB is empty - adding Genesis");
            } else {
                genLOG.info("DB could not be recovered - adding Genesis");
            }

            repository.buildGenesis(genesis);

            setBestBlock(genesis);
            setTotalDifficulty(genesis.getDifficultyBI());

            if (genesis.getTotalDifficulty().equals(BigInteger.ZERO)) {
                // setting the object runtime value
                genesis.setTotalDifficulty(genesis.getDifficultyBI());
            }

        } else {
            setBestBlock(bestBlock);
            if (bestBlock instanceof StakingBlock) {
                loadBestMiningBlock();
            } else if (bestBlock instanceof AionBlock) {
                loadBestStakingBlock();
            } else {
                throw new IllegalStateException();
            }

            BigInteger totalDifficulty = repository.getBlockStore().getBestBlockWithInfo().getTotalDifficulty();
            setTotalDifficulty(totalDifficulty);
            if (bestBlock.getTotalDifficulty().equals(BigInteger.ZERO)) {
                // setting the object runtime value
                bestBlock.setTotalDifficulty(totalDifficulty);
            }

            genLOG.info("loaded block <num={}, root={}, td={}>", getBestBlock().getNumber(), LogUtil.toHexF8(getBestBlock().getStateRoot()), getTotalDifficulty());
        }

        ByteArrayWrapper genesisHash = genesis.getHashWrapper();
        ByteArrayWrapper databaseGenHash = getBlockByNumber(0) == null ? null : getBlockByNumber(0).getHashWrapper();

        // this indicates that DB and genesis are inconsistent
        if (genesisHash == null
                || databaseGenHash == null
                || !genesisHash.equals(databaseGenHash)) {
            if (genesisHash == null) {
                genLOG.error("failed to load genesis from config");
            }

            if (databaseGenHash == null) {
                genLOG.error("failed to load block 0 from database");
            }

            genLOG.error("genesis json rootHash {} is inconsistent with database rootHash {}\n"
                            + "your configuration and genesis are incompatible, please do the following:\n"
                            + "\t1) Remove your database folder\n"
                            + "\t2) Verify that your genesis is correct by re-downloading the binary or checking online\n"
                            + "\t3) Reboot with correct genesis and empty database\n",
                    genesisHash == null ? "null" : genesisHash,
                    databaseGenHash == null ? "null" : databaseGenHash);
            System.exit(org.aion.zero.impl.SystemExitCodes.INITIALIZATION_ERROR);
        }

        if (!Arrays.equals(getBestBlock().getStateRoot(), ConstantUtil.EMPTY_TRIE_HASH)) {
            repository.syncToRoot(getBestBlock().getStateRoot());
        }

        long bestNumber = getBestBlock().getNumber();
        if (forkUtility.isNonceForkActive(bestNumber + 1)) {
            // Reset the PoS difficulty as part of the fork logic.
            if (bestNumber == forkUtility.getNonceForkBlockHeight()) {
                // If this is the trigger for the fork calculate the new difficulty.
                Block block = getBestBlock();
                BigInteger newDiff = calculateFirstPoSDifficultyAtBlock(block);
                forkUtility.setNonceForkResetDiff(newDiff);
            } else {
                // Otherwise, assume that it was already calculated and validated during import.
                // The difficulty cannot be calculated here due to possible pruning of the world state.
                Block firstStaked = getBlockByNumber(forkUtility.getNonceForkBlockHeight() + 1);
                forkUtility.setNonceForkResetDiff(firstStaked.getDifficultyBI());
            }
        }
    }

    private void checkKernelExit() {
        if (shutDownFlag.get()) {
            System.exit(SystemExitCodes.NORMAL);
        }
    }

    public void pruneOrRecoverState(boolean dropArchive, AionGenesis genesis, Logger log) {
        // dropping old state database
        log.info("Deleting old data ...");
        repository.getStateDatabase().drop();
        if (dropArchive) {
            repository.getStateArchiveDatabase().drop();
        }

        // recover genesis
        log.info("Rebuilding genesis block ...");
        repository.buildGenesis(genesis);

        // recover all blocks
        Block block = repository.getBestBlock();
        log.info("Rebuilding the main chain " + block.getNumber() + " blocks (may take a while) ...");

        long topBlockNumber = block.getNumber();
        long blockNumber = 1000;

        // recover in increments of 1k blocks
        while (blockNumber < topBlockNumber) {
            block = getBlockByNumber(blockNumber);
            recoverWorldState(repository, block);
            log.info("Finished with blocks up to " + blockNumber + ".");
            blockNumber += 1000;
        }

        block = repository.getBestBlock();
        recoverWorldState(repository, block);
        log.info("Reorganizing the state storage COMPLETE.");
    }

    /**
     * Alternative to performing a full sync when the database already contains the <b>blocks</b>
     * and <b>index</b> databases. It will rebuild the entire blockchain structure other than these
     * two databases verifying consensus properties. It only redoes imports of the main chain
     * blocks, i.e. does not perform the checks for side chains.
     *
     * <p>The minimum start height is 0, i.e. the genesis block. Specifying a height can be useful
     * in performing the operation is sessions.
     *
     * @param startHeight the height from which to start importing the blocks
     * @implNote The assumption is that the stored blocks are correct, but the code may interpret
     *     them differently.
     */
    public void redoMainChainImport(long startHeight, AionGenesis genesis, Logger LOG) {
        lock.lock();

        try {
            // determine the parameters of the rebuild
            Block block = repository.getBestBlock();
            Block startBlock;
            long currentBlock;
            if (block != null && startHeight <= block.getNumber()) {
                LOG.info("Importing the main chain from block #"
                        + startHeight
                        + " to block #"
                        + block.getNumber()
                        + ". This may take a while.\n"
                        + "The time estimates are optimistic based on current progress.\n"
                        + "It is expected that later blocks take a longer time to import due to the increasing size of the database.");

                if (startHeight == 0L) {
                    // dropping databases that can be inferred when starting from genesis
                    List<String> keep = List.of("block", "index");
                    repository.dropDatabasesExcept(keep);

                    // recover genesis
                    repository.redoIndexWithoutSideChains(genesis); // clear the index entry
                    repository.buildGenesis(genesis);
                    LOG.info("Finished rebuilding genesis block.");
                    startBlock = genesis;
                    currentBlock = 1L;
                    setTotalDifficulty(genesis.getDifficultyBI());
                } else {
                    startBlock = getBlockByNumber(startHeight - 1);
                    currentBlock = startHeight;
                    // initial TD = diff of parent of first block to import
                    Block blockWithDifficulties = getBlockWithInfoByHash(startBlock.getHash());
                    setTotalDifficulty(blockWithDifficulties.getTotalDifficulty());
                }

                boolean fail = false;

                if (startBlock == null) {
                    LOG.info("The main chain block at level {} is missing from the database. Cannot continue importing stored blocks.", currentBlock);
                    fail = true;
                } else {
                    setBestBlock(startBlock);

                    long topBlockNumber = block.getNumber();
                    long stepSize = 10_000L;

                    Pair<ImportResult, AionBlockSummary> result;

                    long start = System.currentTimeMillis();

                    // import in increments of 10k blocks
                    while (currentBlock <= topBlockNumber) {
                        block = getBlockByNumber(currentBlock);
                        if (block == null) {
                            LOG.error("The main chain block at level {} is missing from the database. Cannot continue importing stored blocks.", currentBlock);
                            fail = true;
                            break;
                        }

                        try {
                            // clear the index entry and prune side-chain blocks
                            repository.redoIndexWithoutSideChains(block);
                            long t1 = System.currentTimeMillis();
                            result = tryToConnectAndFetchSummary(new BlockWrapper(block));
                            long t2 = System.currentTimeMillis();
                            LOG.info("<import-status: hash = " + block.getShortHash() + ", number = " + block.getNumber()
                                    + ", txs = " + block.getTransactionsList().size() + ", result = " + result.getLeft()
                                    + ", time elapsed = " + (t2 - t1) + " ms, td = " + getTotalDifficulty() + ">");
                        } catch (Throwable t) {
                            // we want to see the exception and the block where it occurred
                            t.printStackTrace();
                            if (t.getMessage() != null && t.getMessage().contains("Invalid Trie state, missing node ")) {
                                LOG.info("The exception above is likely due to a pruned database and NOT a consensus problem.\n"
                                        + "Rebuild the full state by editing the config.xml file or running ./aion.sh --state FULL.\n");
                            }
                            result =
                                    new Pair<>() {
                                        @Override
                                        public AionBlockSummary setValue(AionBlockSummary value) {
                                            return null;
                                        }

                                        @Override
                                        public ImportResult getLeft() {
                                            return ImportResult.INVALID_BLOCK;
                                        }

                                        @Override
                                        public AionBlockSummary getRight() {
                                            return null;
                                        }
                                    };

                            fail = true;
                        }

                        if (!result.getLeft().isSuccessful()) {
                            LOG.error("Consensus break at block:\n" + block);
                            LOG.info("Import attempt returned result "
                                    + result.getLeft()
                                    + " with summary\n"
                                    + result.getRight());

                            if (repository.isValidRoot(repository.getBestBlock().getStateRoot())) {
                                LOG.info("The repository state trie was:\n");
                                LOG.info(repository.getTrieDump());
                            }

                            fail = true;
                            break;
                        }

                        if (currentBlock % stepSize == 0) {
                            double time = System.currentTimeMillis() - start;

                            double timePerBlock = time / (currentBlock - startHeight + 1);
                            long remainingBlocks = topBlockNumber - currentBlock;
                            double estimate = (timePerBlock * remainingBlocks) / 60_000 + 1; // in minutes
                            LOG.info("Finished with blocks up to "
                                    + currentBlock
                                    + " in "
                                    + String.format("%.0f", time)
                                    + " ms (under "
                                    + String.format("%.0f", time / 60_000 + 1)
                                    + " min).\n\tThe average time per block is < "
                                    + String.format("%.0f", timePerBlock + 1)
                                    + " ms.\n\tCompletion for remaining "
                                    + remainingBlocks
                                    + " blocks estimated to take "
                                    + String.format("%.0f", estimate)
                                    + " min.");
                        }

                        currentBlock++;
                    }
                    LOG.info("Import from " + startHeight + " to " + topBlockNumber + " completed in " + (System.currentTimeMillis() - start) + " ms time.");
                }

                if (fail) {
                    LOG.info("Importing stored blocks FAILED.");
                } else {
                    LOG.info("Importing stored blocks SUCCESSFUL.");
                }
            } else {
                if (block == null) {
                    LOG.info("The best known block in null. The given database is likely empty. Nothing to do.");
                } else {
                    LOG.info("The given height "
                            + startHeight
                            + " is above the best known block "
                            + block.getNumber()
                            + ". Nothing to do.");
                }
            }
            LOG.info("Importing stored blocks COMPLETE.");
        } finally {
            lock.unlock();
        }
    }
}
