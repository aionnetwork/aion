package org.aion.avm.stub;

import java.math.BigInteger;
import org.aion.base.AccountState;
import org.aion.mcf.db.RepositoryCache;
import org.aion.types.AionAddress;

/**
 * Builds an external state object.
 */
public interface IAvmExternalStateBuilder {

    public IAvmExternalStateBuilder withRepository(RepositoryCache<AccountState> repository);

    public IAvmExternalStateBuilder withDifficulty(BigInteger difficulty);

    public IAvmExternalStateBuilder withBlockNumber(long blockNumber);

    public IAvmExternalStateBuilder withBlockTimestamp(long timestamp);

    public IAvmExternalStateBuilder withBlockEnergyLimit(long blockEnergyLimit);

    public IAvmExternalStateBuilder withMiner(AionAddress miner);

    public IAvmExternalStateBuilder withEnergyRules(IEnergyRules energyRules);

    public IAvmExternalStateBuilder allowNonceIncrement(boolean allow);

    public IAvmExternalStateBuilder isLocalCall(boolean isLocal);

    public IAvmExternalState build();
}
