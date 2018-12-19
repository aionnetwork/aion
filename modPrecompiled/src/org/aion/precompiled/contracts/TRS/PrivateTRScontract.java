package org.aion.precompiled.contracts.TRS;

import org.aion.base.db.IRepositoryCache;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.aion.vm.api.interfaces.Address;

/**
 * The PrivateTRScontract is a private version of the TRS contract that is used solely by The Aion
 * Foundation, for the foundation to lock its funds away over a number of months, ensuring continued
 * commitment to the project.
 *
 * <p>Unlike the public-facing TRS contract, the logic for this private TRS contract is bound inside
 * a single class. This is for 2 reasons: there are fewer supported operations for the private TRS
 * contract, and user-friendliness is not a concern here.
 *
 * <p>The PrivateTRScontract may be thought of as a parent class for the public-facing TRS contract
 * in that the latter calls this contract to perform any supported operations it needs.
 */
public final class PrivateTRScontract extends StatefulPrecompiledContract {
    private final Address caller;

    /**
     * Constructs a new PrivateTRScontract that will use repo as the database cache to update its
     * state with and is called by caller.
     *
     * @param repo The database cache.
     * @param caller The calling address.
     */
    public PrivateTRScontract(
            IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo, Address caller) {

        super(repo);
        this.caller = caller;
    }

    /**
     * @param input The input arguments for the contract.
     * @param nrgLimit The energy limit.
     * @return the result of calling execute on the specified input.
     */
    @Override
    public PrecompiledTransactionResult execute(byte[] input, long nrgLimit) {
        // TODO
        return null;
    }
}
