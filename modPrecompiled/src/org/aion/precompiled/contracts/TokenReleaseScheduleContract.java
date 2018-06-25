package org.aion.precompiled.contracts;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.type.StatefulPrecompiledContract;

/**
 * A Token Release Schedule pre-compiled contract. This contract allows tokens to be locked into a
 * non-negotiable release schedule over a period of time.
 *
 * @author nick nadeau
 */
public class TokenReleaseScheduleContract extends StatefulPrecompiledContract {
    private final Address caller;

    /**
     * Constructs a new TokenReleaseSchedule pre-compiled contract.
     *
     * @param track The repository.
     * @param caller The address of the calling account.
     */
    public TokenReleaseScheduleContract(
        IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track, Address caller) {

        super(track);
        this.caller = caller;
    }

    @Override
    public ContractExecutionResult execute(byte[] input, long nrgLimit) {
        return null;
    }

}
