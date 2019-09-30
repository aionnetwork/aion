package org.aion.avm.version1;

import java.math.BigInteger;
import org.aion.avm.stub.IAvmExternalState;
import org.aion.avm.stub.IEnergyRules;
import org.aion.avm.stub.IAvmExternalStateBuilder;
import org.aion.avm.version1.internal.ExternalStateForAvm;
import org.aion.base.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.db.RepositoryCache;
import org.aion.types.AionAddress;

/**
 * Builds a new {@link IAvmExternalState} instance.
 */
public final class AvmExternalStateBuilder implements IAvmExternalStateBuilder {
    private RepositoryCache<AccountState, IBlockStoreBase> repository = null;
    private BigInteger difficulty = null;
    private Long blockNumber = null;
    private Long timestamp = null;
    private Long blockEnergyLimit = null;
    private AionAddress miner = null;
    private IEnergyRules energyRules = null;
    private Boolean allowNonceIncrement = null;
    private Boolean isLocalCall = null;

    @Override
    public IAvmExternalStateBuilder withRepository(RepositoryCache<AccountState, IBlockStoreBase> repository) {
        this.repository = repository;
        return this;
    }

    @Override
    public IAvmExternalStateBuilder withDifficulty(BigInteger difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    @Override
    public IAvmExternalStateBuilder withBlockNumber(long blockNumber) {
        this.blockNumber = blockNumber;
        return this;
    }

    @Override
    public IAvmExternalStateBuilder withBlockTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    @Override
    public IAvmExternalStateBuilder withBlockEnergyLimit(long blockEnergyLimit) {
        this.blockEnergyLimit = blockEnergyLimit;
        return this;
    }

    @Override
    public IAvmExternalStateBuilder withMiner(AionAddress miner) {
        this.miner = miner;
        return this;
    }

    @Override
    public IAvmExternalStateBuilder withEnergyRules(IEnergyRules energyRules) {
        this.energyRules = energyRules;
        return this;
    }

    @Override
    public IAvmExternalStateBuilder allowNonceIncrement(boolean allow) {
        this.allowNonceIncrement = allow;
        return this;
    }

    @Override
    public IAvmExternalStateBuilder isLocalCall(boolean isLocal) {
        this.isLocalCall = isLocal;
        return this;
    }

    @Override
    public IAvmExternalState build() {
        if (this.repository == null) {
            throw new IllegalStateException("Cannot build external state: no repository specified!");
        }
        if (this.difficulty == null) {
            throw new IllegalStateException("Cannot build external state: no difficulty specified!");
        }
        if (this.blockNumber == null) {
            throw new IllegalStateException("Cannot build external state: no blockNumber specified!");
        }
        if (this.timestamp == null) {
            throw new IllegalStateException("Cannot build external state: no timestamp specified!");
        }
        if (this.blockEnergyLimit == null) {
            throw new IllegalStateException("Cannot build external state: no blockEnergyLimit specified!");
        }
        if (this.miner == null) {
            throw new IllegalStateException("Cannot build external state: no miner specified!");
        }
        if (this.energyRules == null) {
            throw new IllegalStateException("Cannot build external state: no energyRules specified!");
        }
        if (this.allowNonceIncrement == null) {
            throw new IllegalStateException("Cannot build external state: no allowNonceIncrement specified!");
        }
        if (this.isLocalCall == null) {
            throw new IllegalStateException("Cannot build external state: no isLocalCall specified!");
        }

        return new ExternalStateForAvm(this.repository, this.allowNonceIncrement, this.isLocalCall, this.difficulty, this.blockNumber, this.timestamp, this.blockEnergyLimit, this.miner, this.energyRules);
    }
}
