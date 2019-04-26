package org.aion.zero.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.interfaces.db.ContractDetails;
import org.aion.interfaces.db.InternalVmType;
import org.aion.interfaces.db.PruneConfig;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.interfaces.db.RepositoryConfig;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.valid.BlockHeaderValidator;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.precompiled.ContractFactory;
import org.aion.types.Address;
import org.aion.types.ByteArrayWrapper;
import org.aion.types.Hash256;
import org.aion.zero.exceptions.HeaderStructureException;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.core.energy.AbstractEnergyStrategyLimit;
import org.aion.zero.impl.core.energy.TargetStrategy;
import org.aion.zero.impl.db.AionContractDetailsImpl;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.aion.zero.impl.sync.DatabaseType;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.valid.AionExtraDataRule;
import org.aion.zero.impl.valid.AionHeaderVersionRule;
import org.aion.zero.impl.valid.EnergyConsumedRule;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.tuple.Pair;

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
                public ContractDetails contractDetailsImpl() {
                    return ContractDetailsAion.createForTesting(0, 1000000).getDetails();
                }

                @Override
                public Properties getDatabaseConfig(String db_name) {
                    Properties props = new Properties();
                    props.setProperty(DatabaseFactory.Props.DB_TYPE, DBVendor.MOCKDB.toValue());
                    props.setProperty(DatabaseFactory.Props.ENABLE_HEAP_CACHE, "false");
                    return props;
                }
            };

    protected StandaloneBlockchain(final A0BCConfig config, final ChainConfiguration chainConfig) {
        super(config, AionRepositoryImpl.createForTesting(repoConfig), chainConfig);
    }

    protected StandaloneBlockchain(
            final A0BCConfig config,
            final ChainConfiguration chainConfig,
            RepositoryConfig repoConfig) {
        super(config, AionRepositoryImpl.createForTesting(repoConfig), chainConfig);
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
        private A0BCConfig a0Config;

        private boolean enableAvm = false;

        // note that this parameter is usually not injected into the blockchain
        // it remains here so we can replace the default validator
        private ChainConfiguration configuration;
        private List<ECKey> defaultKeys = new ArrayList<>();
        private Map<ByteArrayWrapper, AccountState> initialState = new HashMap<>();

        private RepositoryConfig repoConfig;

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
                        new ByteArrayWrapper(pk.getAddress()),
                        new AccountState(BigInteger.ZERO, DEFAULT_BALANCE));
            }
            return this;
        }

        public Builder withDefaultAccounts(List<ECKey> defaultAccounts) {
            this.defaultKeys.addAll(defaultAccounts);
            this.defaultKeys.forEach(
                    k ->
                            initialState.put(
                                    new ByteArrayWrapper(k.getAddress()),
                                    new AccountState(BigInteger.ZERO, DEFAULT_BALANCE)));
            return this;
        }

        public Builder withRepoConfig(RepositoryConfig config) {
            this.repoConfig = config;
            return this;
        }

        public Builder withA0Config(A0BCConfig config) {
            this.a0Config = config;
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

        private AionBlock best = null, parentBest = null;
        private byte[] trieData = null;
        private BigInteger totalDiff = null, totalDiffParent = null;

        /**
         * @param serializedTrieBestBlock data obtained by calling {@link
         *     AionRepositoryImpl#dumpImportableState(byte[], int, DatabaseType)}
         */
        public Builder withState(
                AionBlock parentBestBlock,
                BigInteger totalDiffParent,
                AionBlock bestBlock,
                BigInteger totalDiffBest,
                byte[] serializedTrieBestBlock) {
            this.parentBest = parentBestBlock;
            this.totalDiffParent = totalDiffParent;
            this.best = bestBlock;
            this.totalDiff = totalDiffBest;
            this.trieData = serializedTrieBestBlock;
            return this;
        }

        private Map<Address, byte[]> contractDetails = new HashMap<>();

        /** @param encodedDetails data obtained from {@link AionContractDetailsImpl#getEncoded()} */
        public Builder withDetails(Address contract, byte[] encodedDetails) {
            this.contractDetails.put(contract, encodedDetails);
            return this;
        }

        private Map<Address, byte[]> contractStorage = new HashMap<>();

        /**
         * @param serializedContractTrie data obtained from {@link
         *     AionRepositoryImpl#dumpImportableStorage(byte[], int, Address)}
         */
        public Builder withStorage(Address contract, byte[] serializedContractTrie) {
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
                public ContractDetails contractDetailsImpl() {
                    return ContractDetailsAion.createForTesting(0, 1000000).getDetails();
                }

                @Override
                public Properties getDatabaseConfig(String db_name) {
                    Properties props = new Properties();
                    props.setProperty(DatabaseFactory.Props.DB_TYPE, DBVendor.MOCKDB.toValue());
                    props.setProperty(DatabaseFactory.Props.ENABLE_HEAP_CACHE, "false");
                    return props;
                }
            };
        }

        public Bundle build() {
            this.a0Config =
                    this.a0Config == null
                            ? new A0BCConfig() {
                                @Override
                                public Address getCoinbase() {
                                    return Address.ZERO_ADDRESS();
                                }

                                @Override
                                public byte[] getExtraData() {
                                    return new byte[32];
                                }

                                @Override
                                public boolean getExitOnBlockConflict() {
                                    return false;
                                }

                                @Override
                                public Address getMinerCoinbase() {
                                    return Address.ZERO_ADDRESS();
                                }

                                @Override
                                public int getFlushInterval() {
                                    return 1;
                                }

                                @Override
                                public AbstractEnergyStrategyLimit getEnergyLimitStrategy() {
                                    return new TargetStrategy(
                                            configuration.getConstants().getEnergyLowerBoundLong(),
                                            configuration
                                                    .getConstants()
                                                    .getEnergyDivisorLimitLong(),
                                            10_000_000L);
                                }
                            }
                            : this.a0Config;

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
                                public BlockHeaderValidator<A0BlockHeader>
                                        createBlockHeaderValidator() {
                                    return new BlockHeaderValidator<>(
                                            Arrays.asList(
                                                    new AionExtraDataRule(
                                                            this.constants
                                                                    .getMaximumExtraDataSize()),
                                                    new EnergyConsumedRule(),
                                                    new AionHeaderVersionRule()));
                                }
                            };
                } else {
                    throw new IllegalArgumentException("validatorType != (full|simple)");
                }
            }

            if (this.repoConfig == null) {
                this.repoConfig = generateRepositoryConfig();
            }

            StandaloneBlockchain bc =
                    new StandaloneBlockchain(this.a0Config, this.configuration, this.repoConfig);

            AionGenesis.Builder genesisBuilder = new AionGenesis.Builder();
            for (Map.Entry<ByteArrayWrapper, AccountState> acc : this.initialState.entrySet()) {
                genesisBuilder.addPreminedAccount(Address.wrap(acc.getKey()), acc.getValue());
            }

            AionGenesis genesis;
            try {
                genesis = genesisBuilder.build();
            } catch (HeaderStructureException e) {
                throw new RuntimeException(e);
            }
            bc.genesis = genesis;

            RepositoryCache track = bc.getRepository().startTracking();
            track.createAccount(ContractFactory.getTotalCurrencyContractAddress());
            track.saveVmType(ContractFactory.getTotalCurrencyContractAddress(), InternalVmType.FVM);

            for (Map.Entry<Integer, BigInteger> key : genesis.getNetworkBalances().entrySet()) {
                // assumes only additions can be made in the genesis
                track.addStorageRow(
                        ContractFactory.getTotalCurrencyContractAddress(),
                        new DataWordImpl(key.getKey()).toWrapper(),
                        new ByteArrayWrapper(
                                new DataWordImpl(key.getValue()).getNoLeadZeroesData()));
            }

            for (Address key : genesis.getPremine().keySet()) {
                track.createAccount(key);
                track.addBalance(key, genesis.getPremine().get(key).getBalance());
            }
            track.flush();

            bc.getRepository().commitBlock(genesis.getHeader());
            bc.getRepository().getBlockStore().saveBlock(genesis, genesis.getDifficultyBI(), true);
            bc.setBestBlock(genesis);
            bc.setTotalDifficulty(genesis.getDifficultyBI());
            if (genesis.getCumulativeDifficulty().equals(BigInteger.ZERO)) {
                // setting the object runtime value
                genesis.setCumulativeDifficulty(genesis.getDifficultyBI());
            }

            // set specific block and state
            if (best != null
                    && totalDiff != null
                    && trieData != null
                    && totalDiff != null
                    && parentBest != null
                    && totalDiffParent != null) {
                bc.getRepository().getBlockStore().saveBlock(parentBest, totalDiffParent, true);
                bc.getRepository().getBlockStore().saveBlock(best, totalDiff, true);
                bc.setBestBlock(best);
                bc.setTotalDifficulty(totalDiff);
                bc.getRepository().loadImportableState(trieData, DatabaseType.STATE);
                bc.getRepository().getWorldState().setRoot(best.getStateRoot());
                bc.getRepository().getWorldState().sync(false);
            }

            // set contract details
            if (!contractDetails.isEmpty()) {
                for (Address contract : contractDetails.keySet()) {
                    bc.getRepository()
                            .importTrieNode(
                                    contract.toBytes(),
                                    contractDetails.get(contract),
                                    DatabaseType.DETAILS);
                }
            }

            // set contract storage
            if (!contractStorage.isEmpty()) {
                for (Address contract : contractStorage.keySet()) {
                    bc.getRepository()
                            .loadImportableState(
                                    contractStorage.get(contract), DatabaseType.STORAGE);
                }
            }
            return new Bundle(this.defaultKeys, bc);
        }
    }

    /** for testing */
    public BigInteger getCachedTotalDifficulty() {
        return getCacheTD();
    }

    public void assertEqualTotalDifficulty() {
        BigInteger tdForHash, tdCached, tdPublic;
        byte[] bestBlockHash;

        synchronized (this) {
            bestBlockHash = getBestBlock().getHash();
            tdForHash = getBlockStore().getTotalDifficultyForHash(getBestBlock().getHash());
            tdCached = getCacheTD();
            tdPublic = getTotalDifficulty();
        }

        assert (tdPublic.equals(tdForHash));
        assert (tdPublic.equals(tdCached));
        assert (tdForHash.equals(getTotalDifficultyByHash(new Hash256(bestBlockHash))));
    }

    @Override
    public synchronized ImportResult tryToConnect(final AionBlock block) {
        ImportResult result = tryToConnectInternal(block, System.currentTimeMillis() / 1000);

        if (result == ImportResult.IMPORTED_BEST) {
            BigInteger tdForHash = getBlockStore().getTotalDifficultyForHash(block.getHash());
            assert (getTotalDifficulty().equals(tdForHash));
            assert (getCacheTD().equals(tdForHash));
        }
        return result;
    }

    // TEMPORARY: here to support the ConsensusTest
    public synchronized Pair<ImportResult, AionBlockSummary> tryToConnectAndFetchSummary(
            AionBlock block) {
        return tryToConnectAndFetchSummary(block, System.currentTimeMillis() / 1000, true);
    }

    /** Uses the createNewBlockInternal functionality to avoid time-stamping issues. */
    public AionBlock createBlock(
            AionBlock parent,
            List<AionTransaction> txs,
            boolean waitUntilBlockTime,
            long currTimeSeconds) {

        return createNewBlockInternal(parent, txs, waitUntilBlockTime, currTimeSeconds).block;
    }

    /**
     * @apiNote users should beware that this will cause a disconnect in the blockchain, one should
     *     not expect to use any block that is below {@code blockNumber}
     * @apiNote as a consequence of the creation behaviour do not attempt to conduct VM bytecode
     *     that queries the history of the chain.
     *     <p>This should <i>always</i> be used first, do not attempt to use this function after a
     *     blockchain has already been altered in some state.
     * @implNote creates a new block that does not reference any parent and adds it into the
     *     blockchain (note that this will cause a disconnect in the blockchain)
     * @param blockNumber to be set in the blockchain
     */
    public synchronized void setBlockNumber(long blockNumber) {
        // cannot replace genesis
        assert blockNumber > 0;
        assert blockNumber > this.getBestBlock().getNumber();

        // enforce that we have just created the blockchain, and have not
        // altered it in any drastic fashion
        assert this.getBestBlock() == this.genesis;

        try {
            // we also need a grandparent block to calculate difficulty
            AionBlock grandParentBlock = null;
            if (this.getBlockStore().getBlocksByNumber((int) blockNumber - 1).size() == 0) {
                // create a grandparent block if none exists
                A0BlockHeader header =
                        new A0BlockHeader.Builder()
                                .withStateRoot(this.getBestBlock().getStateRoot())
                                .withTxTrieRoot(HashUtil.EMPTY_TRIE_HASH)
                                .withReceiptTrieRoot(HashUtil.EMPTY_TRIE_HASH)
                                .withNumber((int) blockNumber - 1)
                                .withEnergyLimit(this.genesis.getNrgLimit())
                                .withDifficulty(this.genesis.getDifficulty())
                                .withTimestamp(0)
                                .build();

                AionBlock block = new AionBlock(header, Collections.emptyList());
                this.setBestBlock(block);
                this.getBlockStore().saveBlock(block, this.genesis.getCumulativeDifficulty(), true);
                grandParentBlock = block;
            } else {
                // grab the grandparent block from the database if it exists
                grandParentBlock =
                        this.getBlockStore()
                                .getBlocksByNumber((int) blockNumber - 1)
                                .get(0)
                                .getKey();
            }

            A0BlockHeader bestHeader = this.getBestBlock().getHeader();
            A0BlockHeader header =
                    new A0BlockHeader.Builder()
                            .withParentHash(grandParentBlock.getHash())
                            .withStateRoot(this.getBestBlock().getStateRoot())
                            .withTxTrieRoot(HashUtil.EMPTY_TRIE_HASH)
                            .withReceiptTrieRoot(HashUtil.EMPTY_TRIE_HASH)
                            .withDifficulty(this.getBestBlock().getDifficulty())
                            .withEnergyLimit(this.getBestBlock().getNrgLimit())
                            .withTimestamp(1)
                            .withNumber(blockNumber)
                            .build();
            AionBlock block = new AionBlock(header, Collections.emptyList());
            this.setBestBlock(block);
            this.getBlockStore().saveBlock(block, this.genesis.getCumulativeDifficulty(), true);
        } catch (Exception e) {
            // any exception here should kill the tests
            // rethrow as runtime
            throw new RuntimeException(e);
        }
    }

    public void set040ForkNumber(long n) {
        fork040BlockNumber = n;
    }
}
