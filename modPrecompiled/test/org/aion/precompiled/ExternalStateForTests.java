package org.aion.precompiled;

import java.math.BigInteger;
import java.util.Properties;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.ContractDetails;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.db.PruneConfig;
import org.aion.mcf.db.RepositoryCache;
import org.aion.mcf.db.RepositoryConfig;
import org.aion.precompiled.type.IExternalStateForPrecompiled;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.db.AionRepositoryCache;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;

/**
 * A basic testing implementation of the interface.
 */
public final class ExternalStateForTests implements IExternalStateForPrecompiled {
    private final RepositoryCache<AccountState, IBlockStoreBase> repository;

    private ExternalStateForTests(RepositoryCache<AccountState, IBlockStoreBase> repository) {
        this.repository = repository;
    }

    public static ExternalStateForTests usingDefaultRepository() {
        RepositoryConfig repoConfig = new RepositoryConfig() {
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
        AionRepositoryCache repository = new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        return new ExternalStateForTests(repository);
    }

    public static ExternalStateForTests usingRepository(RepositoryCache<AccountState, IBlockStoreBase> repository) {
        return new ExternalStateForTests(repository);
    }

    @Override
    public void commit() {
        this.repository.flush();
    }

    @Override
    public IExternalStateForPrecompiled newChildExternalState() {
        return new ExternalStateForTests(this.repository.startTracking());
    }

    @Override
    public void addStorageValue(AionAddress address, ByteArrayWrapper key, ByteArrayWrapper value) {
        this.repository.addStorageRow(address, key, value);
    }

    @Override
    public void removeStorage(AionAddress address, ByteArrayWrapper key) {
        this.repository.removeStorageRow(address, key);
    }

    @Override
    public ByteArrayWrapper getStorageValue(AionAddress address, ByteArrayWrapper key) {
        return this.repository.getStorageValue(address, key);
    }

    @Override
    public BigInteger getBalance(AionAddress address) {
        return this.repository.getBalance(address);
    }

    @Override
    public void addBalance(AionAddress address, BigInteger amount) {
        this.repository.addBalance(address, amount);
    }

    @Override
    public BigInteger getNonce(AionAddress address) {
        return this.repository.getNonce(address);
    }

    @Override
    public void incrementNonce(AionAddress address) {
        this.repository.incrementNonce(address);
    }

    @Override
    public long getBlockNumber() {
        return 0;
    }

    @Override
    public boolean isValidEnergyLimitForCreate(long energyLimit) {
        return true;
    }

    @Override
    public boolean isValidEnergyLimitForNonCreate(long energyLimit) {
        return true;
    }

    @Override
    public boolean accountNonceEquals(AionAddress address, BigInteger nonce) {
        return this.repository.getNonce(address).equals(nonce);
    }

    @Override
    public boolean accountBalanceIsAtLeast(AionAddress address, BigInteger balance) {
        return this.repository.getBalance(address).compareTo(balance) >= 0;
    }

    @Override
    public void deductEnergyCost(AionAddress address, BigInteger energyCost) {
        this.repository.addBalance(address, energyCost.negate());
    }
}
