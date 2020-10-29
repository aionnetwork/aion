package org.aion.zero.impl.blockchain;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.aion.base.AccountState;
import org.aion.base.AionTransaction;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.evtmgr.IEventMgr;
import org.aion.zero.impl.types.Block;
import org.aion.zero.impl.types.BlockHeader.Seal;
import org.aion.zero.impl.config.CfgPrune;
import org.aion.base.InternalVmType;
import org.aion.zero.impl.config.PruneConfig;
import org.aion.base.db.RepositoryCache;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.forks.ForkUtility;
import org.aion.zero.impl.types.AionGenesis;
import org.aion.zero.impl.types.BlockContext;
import org.aion.zero.impl.types.MiningBlock;
import org.aion.zero.impl.valid.BlockHeaderRule;
import org.aion.zero.impl.valid.BlockHeaderValidator;
import org.aion.util.types.DataWord;
import org.aion.zero.impl.db.RepositoryConfig;
import org.aion.precompiled.ContractInfo;
import org.aion.types.AionAddress;
import org.aion.util.types.AddressUtils;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.vm.common.BlockCachingContext;
import org.aion.zero.impl.core.energy.AbstractEnergyStrategyLimit;
import org.aion.zero.impl.core.energy.TargetStrategy;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.sync.DatabaseType;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.valid.AionExtraDataRule;
import org.aion.zero.impl.valid.EnergyConsumedRule;
import org.aion.zero.impl.valid.HeaderSealTypeRule;
import org.aion.zero.impl.valid.TXValidator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

/**
 * Used mainly for debugging and testing purposes, provides codepaths for easy setup, into standard
 * configurations that a user might expected, and handles any non-intuitive setup that the
 * blockchain may require.
 */
public class StandaloneBlockchain extends AionBlockchainImpl {

    public AionGenesis genesis;

    private static RepositoryConfig repoConfig =
            new RepositoryConfig() {
                @Override
                public String getDbPath() {
                    return "";
                }

                @Override
                public PruneConfig getPruneConfig() {
                    return new CfgPrune(false);
                }

                @Override
                public Properties getDatabaseConfig(String db_name) {
                    Properties props = new Properties();
                    props.setProperty(DatabaseFactory.Props.DB_TYPE, DBVendor.MOCKDB.toValue());
                    return props;
                }
            };

    protected StandaloneBlockchain(
            final AbstractEnergyStrategyLimit energyLimitStrategy,
            final ForkUtility forkUtility,
            final ChainConfiguration chainConfig,
            RepositoryConfig repoConfig,
            final IEventMgr eventMgr) {
        super(new byte[32], AddressUtils.ZERO_ADDRESS, energyLimitStrategy, true, false, forkUtility, AionRepositoryImpl.createForTesting(repoConfig), chainConfig, eventMgr);
    }

    public void setGenesis(AionGenesis genesis) {
        this.genesis = genesis;
    }

    public AionGenesis getGenesis() {
        return this.genesis;
    }

    public void loadJSON(String json) {}

    public BlockHeaderValidator getBlockHeaderValidator() {
        return this.chainConfiguration.createBlockHeaderValidator();
    }

    public static class Bundle {
        public final List<ECKey> privateKeys;
        public final StandaloneBlockchain bc;

        public Bundle(List<ECKey> privateKeys, StandaloneBlockchain bc) {
            this.privateKeys = privateKeys;
            this.bc = bc;
        }
    }

    public static class Builder {
        private AbstractEnergyStrategyLimit energyLimitStrategy;
        private ForkUtility forkUtility = new ForkUtility();

        private boolean enableAvm = false;

        // note that this parameter is usually not injected into the blockchain
        // it remains here so we can replace the default validator
        private ChainConfiguration configuration;
        private List<ECKey> defaultKeys = new ArrayList<>();
        private Map<ByteArrayWrapper, AccountState> initialState = new HashMap<>();

        private RepositoryConfig repoConfig;
        private IEventMgr eventMgr;

        public static final int INITIAL_ACC_LEN = 10;
        public static final BigInteger DEFAULT_BALANCE =
                new BigInteger("1000000000000000000000000");

        /**
         * The type of validator selected for the blockchain, a "full" validator validates blocks as
         * if they were broadcasted from the network. Therefore the header validator will require a
         * valid equiHash solution.
         *
         * <p>{@code validatorType -> (full|simple)}
         */
        String validatorType;

        public Builder withValidatorConfiguration(String type) {
            this.validatorType = type;
            return this;
        }

        public Builder withDefaultAccounts() {
            for (int i = 0; i < INITIAL_ACC_LEN; i++) {
                ECKey pk = ECKeyFac.inst().create();
                this.defaultKeys.add(pk);
                initialState.put(
                        ByteArrayWrapper.wrap(pk.getAddress()),
                        new AccountState(BigInteger.ZERO, DEFAULT_BALANCE));
            }
            return this;
        }

        public Builder withDefaultAccounts(List<ECKey> defaultAccounts) {
            this.defaultKeys.addAll(defaultAccounts);
            this.defaultKeys.forEach(
                    k ->
                            initialState.put(
                                    ByteArrayWrapper.wrap(k.getAddress()),
                                    new AccountState(BigInteger.ZERO, DEFAULT_BALANCE)));
            return this;
        }

        public Builder withRepoConfig(RepositoryConfig config) {
            this.repoConfig = config;
            return this;
        }

        public Builder withAvmEnabled() {
            this.enableAvm = true;
            return this;
        }

        public Builder withChainConfig(ChainConfiguration chainConfig) {
            if (this.validatorType != null) {
                throw new IllegalArgumentException("cannot set chainConfig after setting type");
            }

            this.configuration = chainConfig;
            return this;
        }

        public Builder withAccount(ByteArrayWrapper publicKey, AccountState accState) {
            initialState.put(publicKey, accState);
            return this;
        }

        public Builder withForks(Properties forkProperties) {
            this.forkUtility = new ForkUtility(forkProperties, LOG);
            return this;
        }

        private Block best = null, parentBest = null;
        private byte[] trieData = null;
        private BigInteger totalDiff = null, totalDiffParent = null;

        /**
         * @param serializedTrieBestBlock data obtained by calling {@link
         *     AionRepositoryImpl#dumpImportableState(byte[], int, DatabaseType)}
         */
        public Builder withState(
                Block parentBestBlock,
                BigInteger totalDiffParent,
                Block bestBlock,
                BigInteger totalDiffBest,
                byte[] serializedTrieBestBlock) {
            this.parentBest = parentBestBlock;
            this.totalDiffParent = totalDiffParent;
            this.best = bestBlock;
            this.totalDiff = totalDiffBest;
            this.trieData = serializedTrieBestBlock;
            return this;
        }

        private Map<AionAddress, byte[]> contractDetails = new HashMap<>();

        /** @param encodedDetails data obtained from {@link org.aion.zero.impl.db.StoredContractDetails#getEncoded()} */
        public Builder withDetails(AionAddress contract, byte[] encodedDetails) {
            this.contractDetails.put(contract, encodedDetails);
            return this;
        }

        private Map<AionAddress, Triple<ByteArrayWrapper, ByteArrayWrapper, InternalVmType>>
                contractIndex = new HashMap<>();

        /** Required by contracts that are not precompiled to get the correct VM type during use. */
        public Builder withContractIndex(
                AionAddress contract,
                ByteArrayWrapper codeHash,
                ByteArrayWrapper inceptionBlock,
                InternalVmType vmType) {
            this.contractIndex.put(contract, Triple.of(codeHash, inceptionBlock, vmType));
            return this;
        }

        private Map<AionAddress, byte[]> contractStorage = new HashMap<>();

        /**
         * @param serializedContractTrie data obtained from {@link
         *     AionRepositoryImpl#dumpImportableStorage(byte[], int, AionAddress)}
         */
        public Builder withStorage(AionAddress contract, byte[] serializedContractTrie) {
            this.contractStorage.put(contract, serializedContractTrie);
            return this;
        }

        private RepositoryConfig generateRepositoryConfig() {
            return new RepositoryConfig() {
                @Override
                public String getDbPath() {
                    return "";
                }

                @Override
                public PruneConfig getPruneConfig() {
                    return new CfgPrune(false);
                }

                @Override
                public Properties getDatabaseConfig(String db_name) {
                    Properties props = new Properties();
                    props.setProperty(DatabaseFactory.Props.DB_TYPE, DBVendor.MOCKDB.toValue());
                    return props;
                }
            };
        }

        public Bundle build() {
            if (this.configuration == null) {
                if (this.validatorType == null) {
                    this.configuration = new ChainConfiguration();
                } else if (this.validatorType.equals("full")) {
                    this.configuration = new ChainConfiguration();
                } else if (this.validatorType.equals("simple")) {
                    this.configuration =
                            new ChainConfiguration() {
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
                                                            this.getConstants()
                                                                    .getMaximumExtraDataSize()),
                                                    new EnergyConsumedRule());

                                    List<BlockHeaderRule> posRules =
                                            Arrays.asList(
                                                    new HeaderSealTypeRule(),
                                                    new AionExtraDataRule(
                                                            this.getConstants()
                                                                    .getMaximumExtraDataSize()),
                                                    new EnergyConsumedRule());

                                    Map<Seal, List<BlockHeaderRule>> unityRules = new EnumMap<>(Seal.class);
                                    unityRules.put(Seal.PROOF_OF_WORK, powRules);
                                    unityRules.put(Seal.PROOF_OF_STAKE, posRules);

                                    return new BlockHeaderValidator(unityRules);
                                }

                                @Override
                                public BlockHeaderValidator createBlockHeaderValidatorForImport() {
                                    return createBlockHeaderValidator();
                                }
                            };
                } else {
                    throw new IllegalArgumentException("validatorType != (full|simple)");
                }
            }

            this.energyLimitStrategy = new TargetStrategy(configuration.getConstants().getEnergyLowerBoundLong(), configuration.getConstants().getEnergyDivisorLimitLong(), 10_000_000L);

            if (this.repoConfig == null) {
                this.repoConfig = generateRepositoryConfig();
            }

            StandaloneBlockchain bc = new StandaloneBlockchain(this.energyLimitStrategy, this.forkUtility, this.configuration, this.repoConfig, this.eventMgr);

            AionGenesis.Builder genesisBuilder = new AionGenesis.Builder();
            for (Map.Entry<ByteArrayWrapper, AccountState> acc : this.initialState.entrySet()) {
                genesisBuilder.addPreminedAccount(
                        new AionAddress(acc.getKey().toBytes()), acc.getValue());
            }

            AionGenesis genesis;
            try {
                genesis = genesisBuilder.buildForTest();
                CfgAion.inst().setGenesis(genesis);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            bc.genesis = genesis;

            RepositoryCache track = bc.getRepository().startTracking();
            track.createAccount(ContractInfo.TOTAL_CURRENCY.contractAddress);
            track.saveVmType(ContractInfo.TOTAL_CURRENCY.contractAddress, InternalVmType.FVM);

            for (Map.Entry<Integer, BigInteger> key : genesis.getNetworkBalances().entrySet()) {
                // assumes only additions can be made in the genesis
                track.addStorageRow(
                        ContractInfo.TOTAL_CURRENCY.contractAddress,
                        new DataWord(key.getKey()).toWrapper(),
                        ByteArrayWrapper.wrap(
                                new DataWord(key.getValue()).getNoLeadZeroesData()));
            }

            for (AionAddress key : genesis.getPremine().keySet()) {
                track.createAccount(key);
                track.addBalance(key, genesis.getPremine().get(key).getBalance());
            }
            track.flushTo(bc.getRepository(), true);

            bc.getRepository()
                    .commitBlock(
                            genesis.getHashWrapper(), genesis.getNumber(), genesis.getStateRoot());
            bc.getRepository().getBlockStore().saveBlock(genesis, genesis.getDifficultyBI(), true);
            bc.setBestBlock(genesis);
            bc.setTotalDifficulty(genesis.getDifficultyBI());

            if (genesis.getTotalDifficulty().equals(BigInteger.ZERO)) {
                // setting the object runtime value
                genesis.setTotalDifficulty(genesis.getDifficultyBI());
            }

            // set specific block and state
            if (best != null
                    && totalDiff != null
                    && trieData != null
                    && parentBest != null
                    && totalDiffParent != null) {
                bc.getRepository().getBlockStore().saveBlock(parentBest, totalDiffParent, true);
                bc.getRepository().getBlockStore().saveBlock(best, totalDiff, true);
                bc.setBestBlock(best);
                bc.setTotalDifficulty(totalDiff);
                bc.getRepository().loadImportableState(trieData, DatabaseType.STATE);
                bc.getRepository().getWorldState().setRoot(best.getStateRoot());
                bc.getRepository().getWorldState().syncWithoutFlush();
            }

            // set contract details
            if (!contractDetails.isEmpty()) {
                for (AionAddress contract : contractDetails.keySet()) {
                    bc.getRepository()
                            .importTrieNode(
                                    contract.toByteArray(),
                                    contractDetails.get(contract),
                                    DatabaseType.DETAILS);
                }
            }

            // set contract index
            if (!contractIndex.isEmpty()) {
                for (AionAddress contract : contractIndex.keySet()) {
                    Triple<ByteArrayWrapper, ByteArrayWrapper, InternalVmType> ci =
                            contractIndex.get(contract);
                    bc.getRepository()
                            .saveIndexedContractInformation(
                                    contract, ci.getLeft(), ci.getMiddle(), ci.getRight(), true);
                }
            }

            // set contract storage
            if (!contractStorage.isEmpty()) {
                for (AionAddress contract : contractStorage.keySet()) {
                    bc.getRepository()
                            .loadImportableState(
                                    contractStorage.get(contract), DatabaseType.STORAGE);
                }
            }
            return new Bundle(this.defaultKeys, bc);
        }

        public Builder withEventManger(IEventMgr eventManger) {
            eventMgr = eventManger;
            return this;
        }
    }

    /** for testing */
    public BigInteger getCachedTotalDifficulty() {
        return getCacheTD();
    }

    public void assertEqualTotalDifficulty() {
        BigInteger tdForHash, tdCached, tdPublic;
        byte[] bestBlockHash;

        lock.lock();
        try {
            bestBlockHash = getBestBlock().getHash();
            tdForHash = getTotalDifficultyForHash(getBestBlock().getHash());
            tdCached = getCacheTD();
            tdPublic = getTotalDifficulty();
        } finally{
            lock.unlock();
        }

        assert (tdPublic.equals(tdForHash));
        assert (tdPublic.equals(tdCached));
        assert (tdForHash.equals(getTotalDifficultyForHash(bestBlockHash)));
    }

    @Override
    public ImportResult tryToConnect(final Block block) {
        lock.lock();
        try {
            ImportResult result = tryToConnectAndFetchSummary(new BlockWrapper(block)).getLeft();

            if (result == ImportResult.IMPORTED_BEST) {
                BigInteger tdForHash = getTotalDifficultyForHash(block.getHash());
                assert (getTotalDifficulty().equals(tdForHash));
                assert (getCacheTD().equals(tdForHash));
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    // TEMPORARY: here to support the ConsensusTest
    public Pair<ImportResult, AionBlockSummary> tryToConnectAndFetchSummary(Block block) {
        lock.lock();
        try {
            return tryToConnectAndFetchSummary(new BlockWrapper(block));
        } finally {
            lock.unlock();
        }
    }

    /** Uses the createNewMiningBlockInternal functionality to avoid time-stamping issues. */
    public MiningBlock createBlock(
            Block parent,
            List<AionTransaction> txs,
            boolean waitUntilBlockTime,
            long currTimeSeconds) {

        boolean unityForkEnabled = forkUtility.isUnityForkActive(parent.getNumber() + 1);
        boolean signatureSwapForkEnabled = forkUtility.isSignatureSwapForkActive(parent.getNumber() + 1);
        for (AionTransaction tx : txs) {
            if (TXValidator.validateTx(tx, unityForkEnabled, signatureSwapForkEnabled).isFail()) {
                throw new InvalidParameterException("invalid transaction input! " + tx.toString());
            }
        }

        return createNewMiningBlockInternal(parent, txs, waitUntilBlockTime, currTimeSeconds).block;
    }

    /** create a testing mining block and the block template*/
    public MiningBlock createBlockAndBlockTemplate(
        Block parent,
        List<AionTransaction> txs,
        boolean waitUntilBlockTime,
        long currTimeSeconds) {

        boolean unityForkEnabled = forkUtility.isUnityForkActive(parent.getNumber() + 1);
        boolean signatureSwapForkEnabled = forkUtility.isSignatureSwapForkActive(parent.getNumber() + 1);
        for (AionTransaction tx : txs) {
            if (TXValidator.validateTx(tx, unityForkEnabled, signatureSwapForkEnabled).isFail()) {
                throw new InvalidParameterException("invalid transaction input! " + tx.toString());
            }
        }

        BlockContext context = createNewMiningBlockInternal(parent, txs, waitUntilBlockTime, currTimeSeconds);

        if (context != null) {
            MiningBlock block = context.block;

            boolean newblock = miningBlockTemplate.put(ByteArrayWrapper.wrap(block.getHash()), block) == null;
            return newblock ? block : null;
        }

        return null;
    }

    @VisibleForTesting
    public Pair<Long, BlockCachingContext> getAvmCachingContext() {
        return Pair.of(cachedBlockNumberForAVM, executionTypeForAVM);
    }
}
