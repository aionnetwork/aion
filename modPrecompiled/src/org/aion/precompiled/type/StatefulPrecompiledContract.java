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
package org.aion.precompiled.type;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;

/**
 * A pre-compiled contract that is capable of modifying state.
 *
 * StatefulPrecompiledContract objects should be instance-based with an immutable reference to a
 * particular state, this is what distinguishes them from ordinary pre-compiled contracts.
 */
public abstract class StatefulPrecompiledContract implements IPrecompiledContract {
    protected final IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track;

    /**
     * Constructs a new StatefulPrecompiledContract.
     *
     * @param track
     */
    public StatefulPrecompiledContract(
        IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track) {

        this.track = track;
    }

}
