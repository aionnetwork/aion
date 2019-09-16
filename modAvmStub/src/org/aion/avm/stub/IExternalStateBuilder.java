package org.aion.avm.stub;

import java.math.BigInteger;
import org.aion.base.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.db.RepositoryCache;
import org.aion.types.AionAddress;

/**
 * Builds an external state object.
 */
public interface IExternalStateBuilder {

    public IExternalStateBuilder withRepository(RepositoryCache<AccountState, IBlockStoreBase> repository);

    public IExternalStateBuilder withDifficulty(BigInteger difficulty);

    public IExternalStateBuilder withBlockNumber(long blockNumber);

    public IExternalStateBuilder withBlockTimestamp(long timestamp);

    public IExternalStateBuilder withBlockEnergyLimit(long blockEnergyLimit);

    public IExternalStateBuilder withMiner(AionAddress miner);

    public IExternalStateBuilder withEnergyRules(IEnergyRules energyRules);

    public IExternalStateBuilder allowNonceIncrement(boolean allow);

    public IExternalStateBuilder isLocalCall(boolean isLocal);

    public IAvmExternalState build();
}
