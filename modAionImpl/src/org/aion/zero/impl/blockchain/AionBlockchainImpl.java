package org.aion.zero.impl.blockchain;

import static java.lang.Runtime.getRuntime;
import static java.math.BigInteger.TEN;
import static java.math.BigInteger.ZERO;
import static java.util.Collections.emptyList;
import static org.aion.util.biginteger.BIUtil.isMoreThan;
import static org.aion.util.conversions.Hex.toHexString;

import java.util.EnumMap;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.aion.zero.impl.types.GenesisStakingBlock;
import static org.aion.zero.impl.types.StakingBlockHeader.GENESIS_SEED;

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
import org.aion.zero.impl.trie.Trie;
import org.aion.zero.impl.trie.TrieImpl;
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
import org.aion.rlp.RLP;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.util.types.AddressUtils;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.util.types.Hash256;
import org.aion.utils.HeapDumper;
import org.aion.zero.impl.vm.common.BlockCachingContext;
import org.aion.zero.impl.vm.common.BulkExecutor;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.energy.AbstractEnergyStrategyLimit;
import org.aion.zero.impl.core.energy.EnergyStrategies;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.sync.DatabaseType;
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
    private static final Logger TX_LOG = LoggerFactory.getLogger(LogEnum.TX.name());
    private static final int THOUSAND_MS = 1000;
    private static final int DIFFICULTY_BYTES = 16;
    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    static long fork040BlockNumber = -1L;
    private static boolean fork040Enable;
    private final BlockHeaderValidator headerValidator;
    private final GrandParentBlockHeaderValidator preUnityGrandParentBlockHeaderValidator;
    private final GreatGrandParentBlockHeaderValidator unityGreatGrandParentBlockHeaderValidator;
    private final ParentBlockHeaderValidator preUnityParentBlockHeaderValidator;
    private final ParentBlockHeaderValidator unityParentBlockHeaderValidator;
    private StakingContractHelper stakingContractHelper = null;
    public final ForkUtility forkUtility;
    public final BeaconHashValidator beaconHashValidator;

    /**
     * Chain configuration class, because chain configuration may change dependant on the block
     * being executed. This is simple for now but in the future we may have to create a "chain
     * configuration provider" to provide us different configurations.
     */
    protected ChainConfiguration chainConfiguration;

    private A0BCConfig config;
    private long exitOn = Long.MAX_VALUE;
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
    private IEventMgr evtMgr = null;
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
    private final Map<ByteArrayWrapper, StakingBlock> stakingBlockTemplate = Collections
        .synchronizedMap(new LRUMap<>(64));
    private final Map<ByteArrayWrapper, AionBlock> miningBlockTemplate = Collections
        .synchronizedMap(new LRUMap<>(64));

    private SelfNodeStatusCallback callback;

    public AionBlockchainImpl(CfgAion cfgAion, boolean forTest) {
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
            forTest);
    }

    protected AionBlockchainImpl(
            final A0BCConfig config,
            final AionRepositoryImpl repository,
            final ChainConfiguration chainConfig,
            boolean forTest) {

        // TODO AKI-318: this specialized class is very cumbersome to maintain; could be replaced with CfgAion
        this.config = config;
        this.repository = repository;
        this.storeInternalTransactions = config.isInternalTransactionStorageEnabled();

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

        // initialize fork utility
        this.forkUtility = new ForkUtility(); // forks are disabled by default
        Optional<Long> maybeUnityFork = loadUnityForkNumberFromConfig(CfgAion.inst());
        if (maybeUnityFork.isPresent()) {
            if (maybeUnityFork.get() < 2) {   // AKI-419, Constrain the minimum unity fork number
                LOG.warn("The unity fork number cannot be less than 2, set the fork number to 2");
                maybeUnityFork = Optional.of(2L);
            }

            this.forkUtility.enableUnityFork(maybeUnityFork.get());
        }

        // initialize beacon hash validator
        this.beaconHashValidator = new BeaconHashValidator(this, this.forkUtility);
    }

    /**
     * Determine fork 0.5.0 fork number from Aion Config.
     *
     * @param cfgAion configuration
     * @return 0.5.0 fork number, if configured; {@link Optional#empty()} otherwise.
     * @throws NumberFormatException if "fork1.0" present in the config, but not parseable
     */
    private static Optional<Long> loadUnityForkNumberFromConfig(CfgAion cfgAion) {
        String unityforkSetting = cfgAion.getFork().getProperties().getProperty("fork1.0");
        if(unityforkSetting == null) {
            return Optional.empty();
        } else {
            return Optional.of(Long.valueOf(unityforkSetting));
        }
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

        if (blkNum != null) {
            fork040BlockNumber = blkNum;
        }

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

    private static byte[] calcTxTrie(List<AionTransaction> transactions) {

        if (transactions == null || transactions.isEmpty()) {
            return ConstantUtil.EMPTY_TRIE_HASH;
        }

        Trie txsState = new TrieImpl(null);

        for (int i = 0; i < transactions.size(); i++) {
            byte[] txEncoding = transactions.get(i).getEncoded();
            if (txEncoding != null) {
                txsState.update(RLP.encodeInt(i), txEncoding);
            } else {
                return ConstantUtil.EMPTY_TRIE_HASH;
            }
        }
        return txsState.getRootHash();
    }

    private static byte[] calcReceiptsTrie(List<AionTxReceipt> receipts) {
        if (receipts == null || receipts.isEmpty()) {
            return ConstantUtil.EMPTY_TRIE_HASH;
        }

        Trie receiptsTrie = new TrieImpl(null);
        for (int i = 0; i < receipts.size(); i++) {
            receiptsTrie.update(RLP.encodeInt(i), receipts.get(i).getReceiptTrieEncoded());
        }
        return receiptsTrie.getRootHash();
    }

    private static byte[] calcLogBloom(List<AionTxReceipt> receipts) {
        if (receipts == null || receipts.isEmpty()) {
            return new byte[Bloom.SIZE];
        }

        Bloom retBloomFilter = new Bloom();
        for (AionTxReceipt receipt : receipts) {
            retBloomFilter.or(receipt.getBloomFilter());
        }

        return retBloomFilter.getBloomFilterBytes();
    }

    /**
     * Returns a {@link PostExecutionWork} object whose {@code doWork()} method will run the
     * provided logic defined in this method. This work is to be applied after each transaction has
     * been run.
     *
     * <p>This "work" is specific to the {@link AionBlockchainImpl#generatePreBlock(Block)}
     * method.
     */
    private static PostExecutionWork getPostExecutionWorkForGeneratePreBlock(
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

    private static boolean checkFork040(long blkNum) {
        if (fork040BlockNumber != -1) {
            return blkNum >= fork040BlockNumber;
        } else {
            return false;
        }
    }

    /**
     * Should be set after initialization, note that the blockchain will still operate if not set,
     * just will not emit events.
     *
     * @param eventManager
     */
    public void setEventManager(IEventMgr eventManager) {
        this.evtMgr = eventManager;
    }

    public AionBlockStore getBlockStore() {
        return repository.getBlockStore();
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
     * <p>Note: If you are making changes to this method and want to use it to track internal state,
     * opt for {@link #getSizeInternal()} instead.
     *
     * @return {@code positive long} representing the current size
     * @see #pubBestBlock
     */
    @Override
    public long getSize() {
        return getBestBlock().getNumber() + 1;
    }

    /** @see #getSize() */
    private long getSizeInternal() {
        return this.bestBlock.getNumber() + 1;
    }

    @Override
    public Block getBlockByNumber(long blockNr) {
        return getBlockStore().getChainBlockByNumber(blockNr);
    }

    @Override
    public List<Block> getBlocksByRange(long first, long last) {
        return getBlockStore().getBlocksByRange(first, last);
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
        return getBlockStore().getBlockByHash(hash);
    }

    @Override
    public List<byte[]> getListOfHashesEndWith(byte[] hash, int qty) {
        return getBlockStore().getListHashesEndWith(hash, qty < 1 ? 1 : qty);
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

        List<byte[]> hashes = getBlockStore().getListHashesEndWith(block.getHash(), qty);

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
        this.bestBlock = getBlockStore().getBlockByHashWithInfo(bestBlockHash);

        if (bestBlock.getHeader().getSealType() == BlockSealType.SEAL_POW_BLOCK) {
            bestMiningBlock = (AionBlock) bestBlock;            
            if (forkUtility.isUnityForkActive(bestBlock.getNumber())) {
                bestStakingBlock = (StakingBlock) getBlockStore().getBlockByHash(bestBlock.getParentHash());
            } else {
                bestStakingBlock = null;
            }
        } else if (bestBlock.getHeader().getSealType() == BlockSealType.SEAL_POS_BLOCK) {
            bestStakingBlock = (StakingBlock) bestBlock;
            bestMiningBlock = (AionBlock) getBlockStore().getBlockByHash(bestBlock.getParentHash());
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
     * @param block
     * @return
     */
    private AionBlockSummary tryConnectAndFork(final Block block) {
        State savedState = pushState(block.getParentHash());
        this.fork = true;

        AionBlockSummary summary = null;
        try {
            summary = add(block);
        } catch (Exception e) {
            LOG.error("Unexpected error: ", e);
        } finally {
            this.fork = false;
        }

        if (summary != null && isMoreThan(totalDifficulty.get(), savedState.td)) {
            if (LOG.isInfoEnabled()) {
                LOG.info(
                        "branching: from = {}/{}, to = {}/{}",
                        savedState.savedBest.getNumber(),
                        toHexString(savedState.savedBest.getHash()),
                        block.getNumber(),
                        toHexString(block.getHash()));
            }

            // main branch become this branch cause we proved that total difficulty is greater
            forkLevel = getBlockStore().reBranch(block);

            // The main repository rebranch
            this.repository = savedState.savedRepo;
            this.repository.syncToRoot(block.getStateRoot());

            // flushing
            flush();

            dropState();
        } else {
            // Stay on previous branch
            popState();
        }

        return summary;
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

    @Override
    public void setBestStakingBlock(StakingBlock block) {
        if (block == null) {
            throw new NullPointerException("The best staking block is null");
        }
        bestStakingBlock = block;
    }

    @Override
    public void setBestMiningBlock(AionBlock block) {
        if (block == null) {
            throw new NullPointerException("The best mining block us null");
        }
        bestMiningBlock = block;
    }

    //TODO : [unity] redesign the blockstore datastucture can read the staking/mining block directly.
    @Override
    public void loadBestMiningBlock() {
        if (bestBlock.getHeader().getSealType() == BlockSealType.SEAL_POW_BLOCK) {
            bestMiningBlock = (AionBlock) bestBlock;
        } else if (bestBlock.getHeader().getSealType() == BlockSealType.SEAL_POS_BLOCK) {
            bestMiningBlock = (AionBlock) getBlockStore().getBlockByHash(bestBlock.getParentHash());
        } else {
            throw new IllegalStateException("Invalid block type");
        }
    }

    @Override
    public void loadBestStakingBlock() {
        long bestBlockNumber = bestBlock.getNumber();

        if (bestStakingBlock == null && forkUtility.isUnityForkActive(bestBlockNumber)) {
            if (bestBlock.getHeader().getSealType() == BlockSealType.SEAL_POS_BLOCK) {
                bestStakingBlock = (StakingBlock) bestBlock;
            } else {
                bestStakingBlock = (StakingBlock) getBlockStore().getBlockByHash(bestBlock.getParentHash());
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
    public synchronized FastImportResult tryFastImport(final Block block) {
        if (block == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Fast sync import attempted with null block or header.");
            }
            return FastImportResult.INVALID_BLOCK;
        }
        if (block.getTimestamp()
                > (System.currentTimeMillis() / THOUSAND_MS
                        + this.chainConfiguration.getConstants().getClockDriftBufferTime())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "Block {} invalid due to timestamp {}.",
                        block.getShortHash(),
                        block.getTimestamp());
            }
            return FastImportResult.INVALID_BLOCK;
        }

        // check that the block is not already known
        Block known = getBlockStore().getBlockByHash(block.getHash());
        if (known != null && known.getNumber() == block.getNumber()) {
            return FastImportResult.KNOWN;
        }

        // a child must be present to import the parent
        Block child = getBlockStore().getChainBlockByNumber(block.getNumber() + 1);
        if (child == null || !Arrays.equals(child.getParentHash(), block.getHash())) {
            return FastImportResult.NO_CHILD;
        } else {
            // the total difficulty will be updated after the chain is complete
            getBlockStore().saveBlock(block, ZERO, true);

            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "Fast sync block saved: number: {}, hash: {}, child: {}",
                        block.getNumber(),
                        block.getShortHash(),
                        child.getShortHash());
            }
            return FastImportResult.IMPORTED;
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

        Block known = getBlockStore().getBlockByHash(currentHash);

        while (known != null && known.getNumber() > 0) {
            currentHash = known.getParentHash();
            currentNumber--;
            known = getBlockStore().getBlockByHash(currentHash);
        }

        if (known == null) {
            return Pair.of(ByteArrayWrapper.wrap(currentHash), currentNumber);
        } else {
            return null;
        }
    }

    public static long shutdownHook = Long.MAX_VALUE;

    public synchronized ImportResult tryToConnect(final Block block) {
        if (bestBlock.getNumber() == shutdownHook) {
            LOG.info("Shutting down and dumping heap as indicated by CLI request since block number {} was reached.", shutdownHook);

            try {
                HeapDumper.dumpHeap(new File(System.currentTimeMillis() + "-heap-report.hprof").getAbsolutePath(), true);
            } catch (Exception e) {
                LOG.error("Unable to dump heap due to exception:", e);
            }

            // requested shutdown
            System.exit(SystemExitCodes.NORMAL);
        }
        return tryToConnectInternal(block, System.currentTimeMillis() / THOUSAND_MS);
    }

    public synchronized void compactState() {
        repository.compactState();
    }

    /* TODO AKI-440: We should either refactor this to remove the redundant parameter,
        or provide it as an input to isValid() */
    public Pair<ImportResult, AionBlockSummary> tryToConnectAndFetchSummary(
            Block block, long currTimeSeconds, boolean doExistCheck) {
        // Check block exists before processing more rules
        if (doExistCheck // skipped when redoing imports
                && getBlockStore().getMaxNumber() >= block.getNumber()
                && getBlockStore().isBlockStored(block.getHash(), block.getNumber())) {

            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "Block already exists hash: {}, number: {}",
                        block.getShortHash(),
                        block.getNumber());
            }

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

        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "Try connect block hash: {}, number: {}",
                    block.getShortHash(),
                    block.getNumber());
        }

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

            summary = add(block);
            ret = summary == null ? INVALID_BLOCK : IMPORTED_BEST;

            if (executionTypeForAVM == BlockCachingContext.SWITCHING_MAINCHAIN
                    && ret == IMPORTED_BEST) {
                // overwrite recent fork info after this
                forkLevel = NO_FORK_LEVEL;
            }
        } else {
            if (getBlockStore().isBlockStored(block.getParentHash(), block.getNumber()-1)) {
                BigInteger oldTotalDiff = getInternalTD();

                // determine if the block parent is main chain or side chain
                long parentHeight = block.getNumber() - 1; // inferred parent number
                if (getBlockStore().isMainChain(block.getParentHash(), parentHeight)) {
                    // main chain parent, therefore can use its number for getting the cache
                    executionTypeForAVM = BlockCachingContext.SIDECHAIN;
                    cachedBlockNumberForAVM = parentHeight;
                } else {
                    // side chain parent, therefore do not know the closes main chain block
                    executionTypeForAVM = BlockCachingContext.DEEP_SIDECHAIN;
                    cachedBlockNumberForAVM = 0;
                }

                summary = tryConnectAndFork(block);
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
        }

        // fire block events
        if (ret.isSuccessful()) {
            if (this.evtMgr != null) {

                List<IEvent> evts = new ArrayList<>();
                IEvent evtOnBlock = new EventBlock(EventBlock.CALLBACK.ONBLOCK0);
                evtOnBlock.setFuncArgs(Collections.singletonList(summary));
                evts.add(evtOnBlock);

                IEvent evtTrace = new EventBlock(EventBlock.CALLBACK.ONTRACE0);
                String str = String.format("Block chain size: [ %d ]", this.getSizeInternal());
                evtTrace.setFuncArgs(Collections.singletonList(str));
                evts.add(evtTrace);

                if (ret == IMPORTED_BEST) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("IMPORTED_BEST");
                    }
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
        return add(block, false, false);
    }

    /**
     * Processes a new block and potentially appends it to the blockchain, thereby changing the
     * state of the world. Decoupled from wrapper function {@link #tryToConnect(Block)} so we
     * can feed timestamps manually
     */
    ImportResult tryToConnectInternal(final Block block, long currTimeSeconds) {
        return tryToConnectAndFetchSummary(block, currTimeSeconds, true).getLeft();
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
    public synchronized AionBlock createNewMiningBlock(
            Block parent, List<AionTransaction> transactions, boolean waitUntilBlockTime) {
        BlockContext newBlockContext = createNewMiningBlockContext(parent, transactions, waitUntilBlockTime);
        return null == newBlockContext ? null : newBlockContext.block;
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
    public synchronized BlockContext createNewMiningBlockContext(
        Block parent, List<AionTransaction> txs, boolean waitUntilBlockTime) {
        final BlockContext blockContext = createNewMiningBlockInternal(
            parent, txs, waitUntilBlockTime, System.currentTimeMillis() / THOUSAND_MS);
        if(blockContext != null) {
            miningBlockTemplate.put(ByteArrayWrapper.wrap(blockContext.block.getHeader().getMineHash()), blockContext.block);
        }
        return blockContext;
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
            while (waitUntilBlockTime && (System.currentTimeMillis() / THOUSAND_MS) <= time) {
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
                    .withTxTrieRoot(calcTxTrie(txs))
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
        
        BlockHeader parentMiningBlock = null;
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
                parentMiningBlock = getParent(parentHdr).getHeader();
                parentMiningBlocksParent = getParent(parentMiningBlock).getHeader();
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

        // derive base block reward
        BigInteger baseBlockReward =
                this.chainConfiguration
                        .getRewardsCalculator(forkUtility.isUnityForkActive(block.getHeader().getNumber()))
                        .calculateReward(block.getHeader().getNumber());
        return new BlockContext(block, baseBlockReward, totalTransactionFee);
    }
    
    private BigInteger calculateFirstPoSDifficultyAtBlock(Block block) {
        if (!forkUtility.isUnityForkBlock(block.getNumber())) {
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
            } else {
                BlockHeader parentStakingBlock = getParent(parentHdr).getHeader();
                BlockHeader parentStakingBlocksParent = getParent(parentStakingBlock).getHeader();
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

            long newDelta = StakingDeltaCalculator.calculateDelta(newSeed, newDiff, stakes);

            newTimestamp =
                    Long.max(
                            parent.getHeader().getTimestamp() + newDelta,
                            parent.getHeader().getTimestamp() + 1);
        } else {
            newTimestamp = System.currentTimeMillis() / THOUSAND_MS;
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
                            .withTxTrieRoot(calcTxTrie(txs))
                            .withEnergyLimit(energyLimitStrategy.getEnergyLimit(parentHdr))
                            .withDifficulty(ByteUtil.bigIntegerToBytes(newDiff, DIFFICULTY_BYTES))
                            .withSeed(newSeed)
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
        blockPreSeal(parentHdr, block);

        if (signingPublicKey != null) {
            stakingBlockTemplate.put(
                ByteArrayWrapper.wrap(block.getHeader().getMineHash()), block);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("GetBlockTemp: {}", block.toString());
        }

        return block;
    }

    private synchronized BigInteger blockPreSeal(BlockHeader parentHdr, Block block) {
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
                calcTxTrie(preBlock.txs),
                stateRoot,
                logBloom.getBloomFilterBytes(),
                calcReceiptsTrie(preBlock.receipts),
                totalEnergyUsed);

        return totalTransactionFee;
    }

    private AionBlockSummary add(Block block) {
        // typical use without rebuild
        AionBlockSummary summary = add(block, false);

        if (summary != null) {
            updateTotalDifficulty(block);
            summary.setTotalDifficulty(block.getTotalDifficulty());

            storeBlock(block, summary.getReceipts(), summary.getSummaries());

            flush();
        }

        return summary;
    }

    private AionBlockSummary add(Block block, boolean rebuild) {
        return add(block, rebuild, true).getLeft();
    }

    /** @Param flushRepo true for the kernel runtime import and false for the DBUtil */
    public Pair<AionBlockSummary, RepositoryCache> add(
            Block block, boolean rebuild, boolean flushRepo) {
        // reset cached VMs before processing the block
        repository.clearCachedVMs();

        if (!isValid(block)) {
            LOG.error("Attempting to add {} block.", (block == null ? "NULL" : "INVALID"));
            return Pair.of(null, null);
        }

        track = repository.startTracking();
        byte[] origRoot = repository.getRoot();

        // (if not reconstructing old blocks) keep chain continuity
        if (!rebuild && !Arrays.equals(bestBlock.getHash(), block.getParentHash())) {
            LOG.error("Attempting to add NON-SEQUENTIAL block.");
            return Pair.of(null, null);
        }

        if (rebuild) {
            // when recovering blocks do not touch the cache
            executionTypeForAVM = BlockCachingContext.DEEP_SIDECHAIN;
            cachedBlockNumberForAVM = 0;
        }

        AionBlockSummary summary = processBlock(block);
        List<AionTxReceipt> receipts = summary.getReceipts();

        // Sanity checks
        long energyUsed = 0L;
        if (!receipts.isEmpty()) {
            for (AionTxReceipt receipt : receipts) {
                energyUsed += receipt.getEnergyUsed();
            }
        }
        if (block.getHeader().getEnergyConsumed() != energyUsed) {
            LOG.warn("Block's energy consumed doesn't match: calculated={} header={}", energyUsed, block.getHeader().getEnergyConsumed());
            track.rollback();
            return Pair.of(null, null);
        }

        byte[] receiptHash = block.getReceiptsRoot();
        byte[] receiptListHash = calcReceiptsTrie(receipts);

        if (!Arrays.equals(receiptHash, receiptListHash)) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(
                        "Block's given Receipt Hash doesn't match: {} != {}",
                        receiptHash,
                        receiptListHash);
                LOG.warn("Calculated receipts: " + receipts);
            }
            track.rollback();
            return Pair.of(null, null);
        }

        byte[] logBloomHash = block.getLogBloom();
        byte[] logBloomListHash = calcLogBloom(receipts);

        if (!Arrays.equals(logBloomHash, logBloomListHash)) {
            if (LOG.isWarnEnabled())
                LOG.warn(
                        "Block's given logBloom Hash doesn't match: {} != {}",
                        ByteUtil.toHexString(logBloomHash),
                        ByteUtil.toHexString(logBloomListHash));
            track.rollback();
            return Pair.of(null, null);
        }

        if (!flushRepo) {
            return Pair.of(summary, track);
        }

        track.flush();
        if (summary != null) {
            repository.commitCachedVMs(block.getHashWrapper());
        }

        if (!rebuild) {
            byte[] blockStateRootHash = block.getStateRoot();
            byte[] worldStateRootHash = repository.getRoot();

            if (!Arrays.equals(blockStateRootHash, worldStateRootHash)) {

                LOG.warn(
                        "BLOCK: State conflict or received invalid block. block: {} worldstate {} mismatch",
                        block.getNumber(),
                        worldStateRootHash);
                LOG.warn("Conflict block dump: {}", toHexString(block.getEncoded()));

                // block is bad so 'rollback' the state root to the original state
                repository.setRoot(origRoot);
                return Pair.of(null, null);
            }
        }

        if (rebuild) {
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

            if (LOG.isDebugEnabled())
                LOG.debug(
                        "Block rebuilt: number: {}, hash: {}, TD: {}",
                        block.getNumber(),
                        block.getShortHash(),
                        getTotalDifficulty());
        }

        return Pair.of(summary, null);
    }

    @Override
    public void flush() {
        repository.flush();
        try {
            getBlockStore().flush();
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
        return getBlockStore().getBlockByHashWithInfo(header.getParentHash());
    }

    public boolean isValid(BlockHeader header) {
        /*
         * The block header should already be validated at this point by P2P or mining,
         * but we are including the validation in case future import paths forget to add it.
         */
        if (!this.headerValidator.validate(header, LOG)) {
            return false;
        }

        Block parent = getParent(header);
        if (parent == null) {
            return false;
        }

        Block grandParent = getParent(parent.getHeader());
        if (header.getSealType() == BlockSealType.SEAL_POW_BLOCK) {
            if (forkUtility.isUnityForkActive(header.getNumber())) {
                if (grandParent == null) {
                    return false;
                }

                Block greatGrandParent = getParent(grandParent.getHeader());
                if (greatGrandParent == null) {
                    return false;
                }

                return unityParentBlockHeaderValidator.validate(header, parent.getHeader(), LOG, null) &&
                        unityGreatGrandParentBlockHeaderValidator.validate(grandParent.getHeader(), greatGrandParent.getHeader(), header, LOG);
            } else {
                return preUnityParentBlockHeaderValidator.validate(header, parent.getHeader(), LOG, null) &&
                        preUnityGrandParentBlockHeaderValidator.validate(parent.getHeader(), grandParent == null ? null : grandParent.getHeader(), header, LOG);
            }
        } else  if (header.getSealType() == BlockSealType.SEAL_POS_BLOCK) {
            if (!forkUtility.isUnityForkActive(header.getNumber())) {
                LOG.warn("Trying to import a Staking block when the Unity fork is not active.");
                return false;
            }

            if (grandParent == null) {
                LOG.warn("Staking block {} cannot find its grandparent", header.getNumber());
                return false;
            }

            Block greatGrandParent = getParent(grandParent.getHeader());
            
            if (forkUtility.isUnityForkBlock(parent.getNumber())) {
                BigInteger expectedDiff = calculateFirstPoSDifficultyAtBlock(parent);
                if (!expectedDiff.equals(header.getDifficultyBI())) {
                    return false;
                }
                grandParent = new GenesisStakingBlock(expectedDiff);
            }

            BigInteger stake = null;

            try {
                stake = getStakingContractHelper().getEffectiveStake(new AionAddress(AddressSpecs.computeA0Address(((StakingBlockHeader) header).getSigningPublicKey())), ((StakingBlockHeader) header).getCoinbase(), parent);
            } catch (Exception e) {
                LOG.error("Shutdown due to a fatal error encountered while getting the effective stake.", e);
                System.exit(SystemExitCodes.FATAL_VM_ERROR);
            }

            return unityParentBlockHeaderValidator.validate(header, parent.getHeader(), LOG, stake) && 
                    unityGreatGrandParentBlockHeaderValidator.validate(grandParent.getHeader(), greatGrandParent.getHeader(), header, LOG);

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

            // Sanity checks
            byte[] trieHash = block.getTxTrieRoot();
            List<AionTransaction> txs = block.getTransactionsList();

            byte[] trieListHash = calcTxTrie(txs);
            if (!Arrays.equals(trieHash, trieListHash)) {
                LOG.warn(
                        "Block's given Trie Hash doesn't match: {} != {}",
                        toHexString(trieHash),
                        toHexString(trieListHash));
                return false;
            }

            if (txs != null && !txs.isEmpty()) {
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
                                    !TXValidator.isValid(tx, unityForkEnabled)
                                                || !TransactionTypeValidator.isValid(tx)
                                                || !beaconHashValidator.validateTxForBlock(tx, block))) {
                    LOG.error("Some transactions in the block are invalid");

                    for (AionTransaction tx : txs) {
                        TX_LOG.debug(
                                "Tx valid ["
                                        + TXValidator.isValid(tx, unityForkEnabled)
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

            fork040Enable = checkFork040(block.getNumber());
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
            fork040Enable = checkFork040(block.getNumber());
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

        getBlockStore().saveBlock(block, td, !fork);

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

        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "Block saved: number: {}, hash: {}, {}",
                    block.getNumber(),
                    block.getShortHash(),
                    td.toString());

            LOG.debug("block added to the blockChain: index: [{}]", block.getNumber());
        }

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

    public boolean hasParentOnTheChain(Block block) {
        return getParent(block.getHeader()) != null;
    }

    public TransactionStore getTransactionStore() {
        return transactionStore;
    }

    @Override
    public Block getBestBlock() {
        return pubBestBlock == null ? bestBlock : pubBestBlock;
    }

    @Override
    public synchronized void setBestBlock(Block block) {
        bestBlock = block;
        if (bestBlock instanceof AionBlock) {
            bestMiningBlock = (AionBlock) bestBlock;
        } else if (bestBlock instanceof StakingBlock) {
            bestStakingBlock = (StakingBlock) bestBlock;
        } else {
            throw new IllegalStateException("Invalid Block instance");
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("BestBlock {}", bestBlock.toString());
            if (bestMiningBlock != null) {
                LOG.debug("BestMiningBlock {}", bestMiningBlock.toString());
            }

            if (bestStakingBlock != null) {
                LOG.debug("BestStakingBlock {}", bestStakingBlock.toString());
            }
        }
        updateBestKnownBlock(block);
        bestBlockNumber.set(bestBlock.getNumber());
    }

    @Override
    public synchronized void close() {
        getBlockStore().close();
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
        if (LOG.isDebugEnabled()) {
            LOG.debug("UnityDifficulty updated: {}", newTotalDifficulty.toString());
        }
    }

    public void setExitOn(long exitOn) {
        this.exitOn = exitOn;
    }

    @Override
    public AionAddress getMinerCoinbase() {
        return minerCoinbase;
    }

    @Override
    public boolean isBlockStored(byte[] hash, long number) {
        return getBlockStore().isBlockStored(hash, number);
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

        List<BlockHeader> headers = getBlockStore().getListHeadersEndWith(startHash, qty);

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

    // NOTE: Functionality removed because not used and untested
    //    /**
    //     * Returns up to limit headers found with following search parameters
    //     *
    //     * @param identifier
    //     *            Identifier of start block, by number of by hash
    //     * @param skip
    //     *            Number of blocks to skip between consecutive headers
    //     * @param limit
    //     *            Maximum number of headers in return
    //     * @param reverse
    //     *            Is search reverse or not
    //     * @return {@link A0BlockHeader}'s list or empty list if none found
    //     */
    //    @Override
    //    public List<A0BlockHeader> getListOfHeadersStartFrom(BlockIdentifierImpl identifier, int
    // skip,
    // int limit,
    //            boolean reverse) {
    //
    //        // null identifier check
    //        if (identifier == null){
    //            return emptyList();
    //        }
    //
    //        // Identifying block we'll move from
    //        IAionBlock startBlock;
    //        if (identifier.getHash() != null) {
    //            startBlock = getBlockByHash(identifier.getHash());
    //        } else {
    //            startBlock = getBlockByNumber(identifier.getNumber());
    //        }
    //
    //        // If nothing found or provided hash is not on main chain, return empty
    //        // array
    //        if (startBlock == null) {
    //            return emptyList();
    //        }
    //        if (identifier.getHash() != null) {
    //            IAionBlock mainChainBlock = getBlockByNumber(startBlock.getNumber());
    //            if (!startBlock.equals(mainChainBlock)) {
    //                return emptyList();
    //            }
    //        }
    //
    //        List<A0BlockHeader> headers;
    //        if (skip == 0) {
    //            long bestNumber = bestBlock.getNumber();
    //            headers = getContinuousHeaders(bestNumber, startBlock.getNumber(), limit,
    // reverse);
    //        } else {
    //            headers = getGapedHeaders(startBlock, skip, limit, reverse);
    //        }
    //
    //        return headers;
    //    }
    //
    //    /**
    //     * Finds up to limit blocks starting from blockNumber on main chain
    //     *
    //     * @param bestNumber
    //     *            Number of best block
    //     * @param blockNumber
    //     *            Number of block to start search (included in return)
    //     * @param limit
    //     *            Maximum number of headers in response
    //     * @param reverse
    //     *            Order of search
    //     * @return headers found by query or empty list if none
    //     */
    //    private List<A0BlockHeader> getContinuousHeaders(long bestNumber, long blockNumber, int
    // limit, boolean reverse) {
    //        int qty = getQty(blockNumber, bestNumber, limit, reverse);
    //
    //        byte[] startHash = getStartHash(blockNumber, qty, reverse);
    //
    //        if (startHash == null) {
    //            return emptyList();
    //        }
    //
    //        List<A0BlockHeader> headers = getBlockStore().getListHeadersEndWith(startHash, qty);
    //
    //        // blocks come with falling numbers
    //        if (!reverse) {
    //            Collections.reverse(headers);
    //        }
    //
    //        return headers;
    //    }
    //
    //    /**
    //     * Gets blocks from main chain with gaps between
    //     *
    //     * @param startBlock
    //     *            Block to start from (included in return)
    //     * @param skip
    //     *            Number of blocks skipped between every header in return
    //     * @param limit
    //     *            Maximum number of headers in return
    //     * @param reverse
    //     *            Order of search
    //     * @return headers found by query or empty list if none
    //     */
    //    private List<A0BlockHeader> getGapedHeaders(IAionBlock startBlock, int skip, int limit,
    // boolean reverse) {
    //        List<A0BlockHeader> headers = new ArrayList<>();
    //        headers.add(startBlock.getHeader());
    //        int offset = skip + 1;
    //        if (reverse) {
    //            offset = -offset;
    //        }
    //        long currentNumber = startBlock.getNumber();
    //        boolean finished = false;
    //
    //        while (!finished && headers.size() < limit) {
    //            currentNumber += offset;
    //            IAionBlock nextBlock = getBlockStore().getChainBlockByNumber(currentNumber);
    //            if (nextBlock == null) {
    //                finished = true;
    //            } else {
    //                headers.add(nextBlock.getHeader());
    //            }
    //        }
    //
    //        return headers;
    //    }
    //
    //
    //    private int getQty(long blockNumber, long bestNumber, int limit, boolean reverse) {
    //        if (reverse) {
    //            return blockNumber - limit + 1 < 0 ? (int) (blockNumber + 1) : limit;
    //        } else {
    //            if (blockNumber + limit - 1 > bestNumber) {
    //                return (int) (bestNumber - blockNumber + 1);
    //            } else {
    //                return limit;
    //            }
    //        }
    //    }
    //
    //    private byte[] getStartHash(long blockNumber, int qty, boolean reverse) {
    //
    //        long startNumber;
    //
    //        if (reverse) {
    //            startNumber = blockNumber;
    //        } else {
    //            startNumber = blockNumber + qty - 1;
    //        }
    //
    //        IAionBlock block = getBlockByNumber(startNumber);
    //
    //        if (block == null) {
    //            return null;
    //        }
    //
    //        return block.getHash();
    //    }

    @Override
    public List<byte[]> getListOfBodiesByHashes(List<byte[]> hashes) {
        List<byte[]> bodies = new ArrayList<>(hashes.size());

        for (byte[] hash : hashes) {
            Block block = getBlockStore().getBlockByHash(hash);
            if (block == null) {
                break;
            }
            bodies.add(block.getEncodedBody());
        }

        return bodies;
    }

    private void updateBestKnownBlock(Block block) {
        updateBestKnownBlock(block.getHeader());
    }

    private void updateBestKnownBlock(BlockHeader header) {
        if (bestKnownBlock.get() == null || header.getNumber() > bestKnownBlock.get().getNumber()) {
            bestKnownBlock.set(new BlockIdentifier(header.getHash(), header.getNumber()));
        }
    }

    public IEventMgr getEventMgr() {
        return this.evtMgr;
    }

    @Override
    public synchronized boolean recoverWorldState(Repository repository, Block block) {
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
            other = repo.getBlockStore().getBlockByHash(other.getParentHash());

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
                bestBlock = getBlockStore().getBestBlock();

                if (bestBlock instanceof AionBlock) {
                    bestMiningBlock = (AionBlock) bestBlock;
                } else if (bestBlock instanceof StakingBlock) {
                    bestStakingBlock = (StakingBlock) bestBlock;
                } else {
                    throw new IllegalStateException("Invalid best block!");
                }
            }

            this.add(other, true);
        }

        // update the repository
        repo.flush();

        // setting the root back to its correct value
        repo.syncToRoot(originalRoot);

        // return a flag indicating if the recovery worked
        return repo.isValidRoot(block.getStateRoot());
    }

    @Override
    public synchronized boolean recoverIndexEntry(Repository repository, Block block) {
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

        AionRepositoryImpl repo = (AionRepositoryImpl) repository;

        Deque<Block> dirtyBlocks = new ArrayDeque<>();
        // already known to be missing the state
        dirtyBlocks.push(block);

        Block other = block;

        // find all the blocks missing a world state
        do {
            other = repo.getBlockStore().getBlockByHash(other.getParentHash());

            // cannot recover if no valid states exist (must build from genesis)
            if (other == null) {
                return false;
            } else {
                dirtyBlocks.push(other);
            }
        } while (!repo.isIndexed(other.getHash(), other.getNumber()) && other.getNumber() > 0);

        if (other.getNumber() == 0 && !repo.isIndexed(other.getHash(), other.getNumber())) {
            LOG.info("Rebuild index FAILED because a valid index could not be found.");
            return false;
        }

        // if the size key is missing we set it to the MAX(best block, this block, current value)
        long maxNumber = getBlockStore().getMaxNumber();
        if (bestBlock != null && bestBlock.getNumber() > maxNumber) {
            maxNumber = bestBlock.getNumber();
        }
        if (block.getNumber() > maxNumber) {
            maxNumber = block.getNumber();
        }
        getBlockStore().correctSize(maxNumber, LOG);

        // remove the last added block because it has a correct world state
        Block parentBlock =
                repo.getBlockStore().getBlockByHashWithInfo(dirtyBlocks.pop().getHash());

        BigInteger totalDiff = parentBlock.getTotalDifficulty();

        LOG.info(
                "Valid index found at block hash: {}, number: {}.",
                other.getShortHash(),
                other.getNumber());

        // rebuild world state for dirty blocks
        while (!dirtyBlocks.isEmpty()) {
            other = dirtyBlocks.pop();
            LOG.info(
                    "Rebuilding index for block hash: {}, number: {}, txs: {}.",
                    other.getShortHash(),
                    other.getNumber(),
                    other.getTransactionsList().size());
            totalDiff =
                    repo.getBlockStore()
                            .correctIndexEntry(other, parentBlock.getTotalDifficulty());
            parentBlock = other;
        }

        // update the repository
        repo.flush();

        // return a flag indicating if the recovery worked
        if (repo.isIndexed(block.getHash(), block.getNumber())) {
            Block mainChain = getBlockStore().getBestBlock();
            BigInteger mainChainTotalDiff =
                    getBlockStore().getTotalDifficultyForHash(mainChain.getHash());

            // check if the main chain needs to be updated
            if (mainChainTotalDiff.compareTo(totalDiff) < 0) {
                if (LOG.isInfoEnabled()) {
                    LOG.info(
                            "branching: from = {}/{}, to = {}/{}",
                            mainChain.getNumber(),
                            toHexString(mainChain.getHash()),
                            block.getNumber(),
                            toHexString(block.getHash()));
                }
                getBlockStore().reBranch(block);
                repo.syncToRoot(block.getStateRoot());
                repo.flush();
            } else {
                if (mainChain.getNumber() > block.getNumber()) {
                    // checking if the current recovered blocks are a subsection of the main chain
                    Block ancestor = getBlockByNumber(block.getNumber() + 1);
                    if (ancestor != null
                            && Arrays.equals(ancestor.getParentHash(), block.getHash())) {
                        getBlockStore().correctMainChain(block, LOG);
                        repo.flush();
                    }
                }
            }
            return true;
        } else {
            LOG.info("Rebuild index FAILED.");
            return false;
        }
    }

    @Override
    public BigInteger getTotalDifficultyByHash(Hash256 hash) {
        if (hash == null) {
            throw new NullPointerException();
        }
        return this.getBlockStore().getTotalDifficultyForHash(hash.toBytes());
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
        return getBlockStore().isMainChain(hash, level);
    }

    @Override
    public boolean isMainChain(byte[] hash) {
        return getBlockStore().isMainChain(hash);
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
    public synchronized StakingBlock createStakingBlockTemplate(
        List<AionTransaction> pendingTransactions, byte[] signingPublicKey, byte[] newSeed, byte[] coinbase) {
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

        return createNewStakingBlock(getBestBlock(), pendingTransactions, newSeed, signingPublicKey, coinbase);
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
        return getBlockStore().getBlockByHashWithInfo(hash);
    }

    @Override
    public Block getBestBlockWithInfo() {
        return getBlockStore().getBestBlockWithInfo();
    }

    void setNodeStatusCallback(SelfNodeStatusCallback callback) {
        this.callback = callback;
    }
}
