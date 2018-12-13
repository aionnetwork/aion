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

package org.aion.zero.impl.vm;

import org.aion.fastvm.FastVM;
import org.aion.precompiled.ContractFactory;
import org.aion.vm.ExecutorProvider;
import org.aion.vm.IPrecompiledContract;
import org.aion.vm.KernelInterfaceForFastVM;
import org.aion.base.vm.VirtualMachine;
import org.aion.vm.api.interfaces.TransactionContext;

public class AionExecutorProvider implements ExecutorProvider {

    @Override
    public IPrecompiledContract getPrecompiledContract(
            TransactionContext context, KernelInterfaceForFastVM track) {
        return new ContractFactory().getPrecompiledContract(context, track);
    }

    @Override
    public VirtualMachine getVM() {
        return new FastVM();
    }

    private AionExecutorProvider() {}

    private static class Holder {
        static final AionExecutorProvider INST = new AionExecutorProvider();
    }

    // holder
    public static AionExecutorProvider getInstance() {
        return Holder.INST;
    }
}
