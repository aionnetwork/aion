package org.aion.zero.impl.db;

import org.aion.interfaces.db.ContractDetails;
import org.aion.interfaces.db.DetailsProvider;
import org.aion.zero.db.AionContractDetailsImpl;

/**
 * Contract details provider for Aion.
 *
 * @author gavin
 */
public class ContractDetailsAion implements DetailsProvider {

    private final int prune;
    private final int memStorageLimit;

    private ContractDetailsAion() {
        // CfgDb cfgDb = CfgAion.inst().getDb();
        this.prune = 0; // cfgDb.getPrune();
        this.memStorageLimit = 64 * 1024; // cfgDb.getDetailsInMemoryStorageLimit();
    }

    /**
     * Non-Singleton constructor for class, currently this constructor is mostly used by {@link
     * AionRepositoryImpl} related testing. In the future when we moved towards a more formal
     * dependency management framework, this may become more useful.
     *
     * @param prune a value > 0 indicates that prune should be for that many blocks.
     * @param memStorageLimit indicates the maximum storage size (is this used?)
     */
    protected ContractDetailsAion(final int prune, final int memStorageLimit) {
        this.prune = prune;
        this.memStorageLimit = memStorageLimit;
    }

    /**
     * Static factory method for creating the details provider, as the name indicates this is
     * intended for use in testing.
     *
     * @param prune {@link ContractDetailsAion}
     * @param memStorageLimit {@link ContractDetailsAion}
     * @return {@code contractDetails} a standalone/new instance of contract details
     */
    public static ContractDetailsAion createForTesting(final int prune, final int memStorageLimit) {
        return new ContractDetailsAion(prune, memStorageLimit);
    }

    private static class ContractDetailsAionHolder {
        public static final ContractDetailsAion inst = new ContractDetailsAion();
    }

    public static ContractDetailsAion getInstance() {
        return ContractDetailsAionHolder.inst;
    }

    @Override
    public ContractDetails getDetails() {
        return new AionContractDetailsImpl(this.prune, this.memStorageLimit);
    }
}
