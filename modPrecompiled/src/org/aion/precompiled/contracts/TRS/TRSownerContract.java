/*
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
 */
package org.aion.precompiled.contracts.TRS;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.type.StatefulPrecompiledContract;

/**
 * The TRSownerContract is 1 of 3 inter-dependent but separate contracts that together make up the
 * public-facing TRS contract. A public-facing TRS contract can be owned by any user. In addition to
 * a regular user being able to own a public-facing TRS contract, there is also a special instance
 * of the public-facing TRS contract that is owned by The Aion Foundation itself, which differs from
 * the private TRS contract.
 *
 * The public-facing TRS contract was split into 3 contracts mostly for user-friendliness, since the
 * TRS contract supports many operations, rather than have a single execute method and one very
 * large document specifying its use, the contract was split into 3 logical components instead.
 *
 * The TRSownerContract is the component of the public-facing TRS contract that the contract owner
 * interacts with primarily. The operations provided here are all operations that only the contract
 * owner can use and are related to the overall integrity of the contract itself.
 *
 * The following operations are supported:
 *      create -- creates a new public TRS contract.
 *      init -- initializes a non-initialized but created public TRS contract.
 *      lock -- locks the TRS contract so that no more deposits may be made.
 *      start -- starts the distribution of the savings in the TRS contract.
 *      mint -- informs the TRS contract about tokens that were minted to it on behalf of a depositor.
 *      nullify -- disables the TRS contract.
 */
public class TRSownerContract extends StatefulPrecompiledContract {
    private final Address caller;

    /**
     * Constructs a new TRSownerContract that will use repo as the database cache to update its
     * state with and is called by caller.
     *
     * @param repo The database cache.
     * @param caller The calling address.
     */
    public TRSownerContract(
        IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> repo, Address caller) {

        super(repo);
        this.caller = caller;
    }

    /**
     *
     * @param input The input arguments for the contract.
     * @param nrgLimit The energy limit.
     * @return the result of calling execute on the specified input.
     */
    @Override
    public ContractExecutionResult execute(byte[] input, long nrgLimit) {
        //TODO
        return null;
    }
}
