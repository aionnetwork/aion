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

package org.aion.vm;

import org.aion.vm.api.interfaces.TransactionContext;

public interface ExecutorProvider {

    /**
     * @implNote note that this essentially means that a new precompiled contract is created each
     *     time the contract is executed. Reasoning behind this is that {@code ExecutionContext} is
     *     specific to a particular call.
     * @implNote no real good reason for passing in the {@code ExecutionContext} in the constructor,
     *     rather than the execution call. Just more convenient as it disrupts the least amount of
     *     code paths
     * @param context
     * @param track
     * @return @{code precompiled contract} if available {@code null} otherwise
     */
    IPrecompiledContract getPrecompiledContract(TransactionContext context, KernelInterfaceForFastVM track);

    VirtualMachine getVM();
}
