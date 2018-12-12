package org.aion.precompiled.type;

import org.aion.base.db.IRepositoryCache;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.vm.IPrecompiledContract;

/**
 * A pre-compiled contract that is capable of modifying state.
 *
 * <p>StatefulPrecompiledContract objects should be instance-based with an immutable reference to a
 * particular state, this is what distinguishes them from ordinary pre-compiled contracts.
 */
public abstract class StatefulPrecompiledContract implements IPrecompiledContract {
    public static final long TX_NRG_MIN = 20_999;
    public static final long TX_NRG_MAX = 2_000_001;
    protected final IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> track;

    /**
     * Constructs a new StatefulPrecompiledContract.
     *
     * @param track
     */
    public StatefulPrecompiledContract(
            IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> track) {

        if (track == null) {
            throw new IllegalArgumentException("Null track.");
        }
        this.track = track;
    }

    /**
     * Returns true only if nrgLimit is a valid energy limit for the transaction.
     *
     * @param nrgLimit The limit to check.
     * @return true only if nrgLimit is a valid limit.
     */
    protected boolean isValidTxNrg(long nrgLimit) {
        return (nrgLimit > TX_NRG_MIN) && (nrgLimit < TX_NRG_MAX);
    }
}
