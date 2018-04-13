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

package org.aion.zero.impl;

import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.db.IRepositoryConfig;
import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.db.impl.leveldb.LevelDBConstants;
import org.aion.mcf.core.AccountState;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.db.impl.DBVendor;
import org.aion.mcf.valid.BlockHeaderValidator;
import org.aion.vm.PrecompiledContracts;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.core.energy.AbstractEnergyStrategyLimit;
import org.aion.zero.impl.core.energy.EnergyStrategies;
import org.aion.zero.impl.core.energy.TargetStrategy;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.aion.zero.impl.valid.AionExtraDataRule;
import org.aion.zero.impl.valid.AionHeaderVersionRule;
import org.aion.zero.impl.valid.EnergyConsumedRule;
import org.aion.zero.types.A0BlockHeader;
import org.aion.mcf.vm.types.DataWord;

import java.math.BigInteger;
import java.util.*;

/**
 * Used mainly for debugging and testing purposes, provides codepaths for easy
 * setup, into standard configurations that a user might expected, and handles
 * any non-intuitive setup that the blockchain may require.
 */
public class StandaloneBlockchain extends AionBlockchainImpl {

    public AionGenesis genesis;

    private static IRepositoryConfig repoConfig = new IRepositoryConfig() {
        @Override
        public String[] getVendorList() {
            return new String[] { DBVendor.MOCKDB.toValue() };
        }

        @Override
        public String getActiveVendor() {
            return DBVendor.MOCKDB.toValue();
        }

        @Override
        public String getDbPath() {
            return "";
        }

        @Override
        public int getPrune() {
            return -1;
        }

        @Override
        public IContractDetails contractDetailsImpl() {
            return ContractDetailsAion.createForTesting(0, 1000000).getDetails();
        }

        @Override
        public boolean isAutoCommitEnabled() {
            return false;
        }

        @Override
        public boolean isDbCacheEnabled() {
            return false;
        }

        @Override
        public boolean isDbCompressionEnabled() {
            return false;
        }

        @Override
        public boolean isHeapCacheEnabled() {
            return true;
        }

        @Override
        public String getMaxHeapCacheSize() {
            return "0";
        }

        @Override
        public boolean isHeapCacheStatsEnabled() {
            return false;
        }

        @Override
        public int getMaxFdAllocSize() {
            return LevelDBConstants.MAX_OPEN_FILES;
        }

        // default levelDB setting, may want to change this later
        @Override
        public int getBlockSize() {
            return LevelDBConstants.BLOCK_SIZE;
        }

        @Override
        public int getWriteBufferSize() {
            return LevelDBConstants.WRITE_BUFFER_SIZE;
        }

        @Override
        public int getCacheSize() {
            return LevelDBConstants.CACHE_SIZE;
        }
    };

    protected StandaloneBlockchain(final A0BCConfig config, final ChainConfiguration chainConfig) {
        super(config, AionRepositoryImpl.createForTesting(repoConfig), chainConfig);
    }

    protected StandaloneBlockchain(final A0BCConfig config, final ChainConfiguration chainConfig,
            IRepositoryConfig repoConfig) {
        super(config, AionRepositoryImpl.createForTesting(repoConfig), chainConfig);
    }

    public void setGenesis(AionGenesis genesis) {
        this.genesis = genesis;
    }

    public AionGenesis getGenesis() {
        return this.genesis;
    }

    public void loadJSON(String json) {

    }

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

        // note that this parameter is usually not injected into the blockchain
        // it remains here so we can replace the default validator
        private ChainConfiguration configuration;
        private List<ECKey> defaultKeys = new ArrayList<>();
        private Map<ByteArrayWrapper, AccountState> initialState = new HashMap<>();
        private boolean blockPruningEnabled = false;

        private IRepositoryConfig repoConfig;

        public static final int INITIAL_ACC_LEN = 10;
        public static final BigInteger DEFAULT_BALANCE = new BigInteger("1000000000000000000000000");

        /**
         * The type of validator selected for the blockchain, a "full" validator
         * validates blocks as if they were broadcasted from the network.
         * Therefore the header validator will require a valid equiHash
         * solution.
         *
         * {@code validatorType -> (full|simple)}
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
                initialState.put(new ByteArrayWrapper(pk.getAddress()),
                        new AccountState(BigInteger.ZERO, DEFAULT_BALANCE));
            }
            return this;
        }

        public Builder withDefaultAccounts(List<ECKey> defaultAccounts) {
            this.defaultKeys.addAll(defaultAccounts);
            this.defaultKeys.forEach(k -> initialState.put(new ByteArrayWrapper(k.getAddress()),
                    new AccountState(BigInteger.ZERO, DEFAULT_BALANCE)));
            return this;
        }

        public Builder withRepoConfig(IRepositoryConfig config) {
            this.repoConfig = config;
            return this;
        }

        public Builder withA0Config(A0BCConfig config) {
            this.a0Config = config;
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

        public Builder withBlockPruningEnabled() {
            blockPruningEnabled = true;
            return this;
        }

        private IRepositoryConfig generateRepositoryConfig() {
            if (!blockPruningEnabled) {
                return repoConfig;
            }

            return new IRepositoryConfig() {
                @Override
                public String[] getVendorList() {
                    return new String[] { DBVendor.MOCKDB.toValue() };
                }

                @Override
                public String getActiveVendor() {
                    return DBVendor.MOCKDB.toValue();
                }

                @Override
                public String getDbPath() {
                    return "";
                }

                @Override
                public int getPrune() {
                    return 0;
                }

                @Override
                public IContractDetails contractDetailsImpl() {
                    return ContractDetailsAion.createForTesting(0, 1000000).getDetails();
                }

                @Override
                public boolean isAutoCommitEnabled() {
                    return false;
                }

                @Override
                public boolean isDbCacheEnabled() {
                    return false;
                }

                @Override
                public boolean isDbCompressionEnabled() {
                    return false;
                }

                @Override
                public boolean isHeapCacheEnabled() {
                    return true;
                }

                @Override
                public String getMaxHeapCacheSize() {
                    return "0";
                }

                @Override
                public boolean isHeapCacheStatsEnabled() {
                    return false;
                }

                @Override
                public int getMaxFdAllocSize() {
                    return LevelDBConstants.MAX_OPEN_FILES;
                }

                // default levelDB setting, may want to change this later
                @Override
                public int getBlockSize() {
                    return LevelDBConstants.BLOCK_SIZE;
                }

                @Override
                public int getWriteBufferSize() {
                    return LevelDBConstants.WRITE_BUFFER_SIZE;
                }

                @Override
                public int getCacheSize() {
                    return LevelDBConstants.CACHE_SIZE;
                }
            };
        }

        public Bundle build() {
            this.a0Config = this.a0Config == null ? new A0BCConfig() {
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
                            configuration.getConstants().getEnergyDivisorLimitLong(),
                            10_000_000L);
                }

            } : this.a0Config;

            if (this.configuration == null) {
                if (this.validatorType == null) {
                    this.configuration = new ChainConfiguration();
                } else if (this.validatorType.equals("full")) {
                    this.configuration = new ChainConfiguration();
                } else if (this.validatorType.equals("simple")) {
                    this.configuration = new ChainConfiguration() {
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
                        public BlockHeaderValidator<A0BlockHeader> createBlockHeaderValidator() {
                            return new BlockHeaderValidator<A0BlockHeader>(
                                    Arrays.asList(
                                            new AionExtraDataRule(this.constants.getMaximumExtraDataSize()),
                                            new EnergyConsumedRule(),
                                            new AionHeaderVersionRule()));
                        }
                    };
                } else {
                    throw new IllegalArgumentException("validatorType != (full|simple)");
                }
            }

            if (this.repoConfig == null)
                this.repoConfig = generateRepositoryConfig();

            StandaloneBlockchain bc = new StandaloneBlockchain(this.a0Config, this.configuration, this.repoConfig);

            AionGenesis.Builder genesisBuilder = new AionGenesis.Builder();
            for (Map.Entry<ByteArrayWrapper, AccountState> acc : this.initialState.entrySet()) {
                genesisBuilder.addPreminedAccount(Address.wrap(acc.getKey()), acc.getValue());
            }

            AionGenesis genesis = genesisBuilder.build();
            bc.genesis = genesis;

            IRepositoryCache track = bc.getRepository().startTracking();
            track.createAccount(PrecompiledContracts.totalCurrencyAddress);

            for (Map.Entry<Integer, BigInteger> key : genesis.getNetworkBalances().entrySet()) {
                track.addStorageRow(PrecompiledContracts.totalCurrencyAddress, new DataWord(key.getKey()),
                        new DataWord(key.getValue()));
            }

            for (Address key : genesis.getPremine().keySet()) {
                track.createAccount(key);
                track.addBalance(key, genesis.getPremine().get(key).getBalance());
            }
            track.flush();

            // TODO: violates abstraction, consider adding to interface after
            // stable
            ((AionRepositoryImpl) bc.getRepository()).commitBlock(genesis.getHeader());
            ((AionBlockStore) bc.getRepository().getBlockStore()).saveBlock(genesis, genesis.getCumulativeDifficulty(),
                    true);
            bc.setBestBlock(genesis);
            bc.setTotalDifficulty(genesis.getCumulativeDifficulty());

            return new Bundle(this.defaultKeys, bc);
        }
    }
}
