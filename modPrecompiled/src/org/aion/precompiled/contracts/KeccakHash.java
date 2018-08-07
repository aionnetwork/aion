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
package org.aion.precompiled.contracts;

import org.aion.base.db.*;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionResult;

import static org.aion.crypto.HashUtil.keccak256;

public class KeccakHash extends StatefulPrecompiledContract{
    private final static long DEFAULT_COST = 100L;

    public KeccakHash(IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track) {
        super(track);
    }

    /**
     * Returns the hash of given input
     *
     * input is defined as
     *      [nb input byte array] n > 0
     *
     * the returned hash is in ContractExecutionResult.getOutput
     */
    public ExecutionResult execute(byte[] input, long nrg){
        // check input nrg
        long additionalNRG = Math.round(Math.sqrt(input.length));
        if (nrg < DEFAULT_COST + additionalNRG)
            return new ExecutionResult(ResultCode.OUT_OF_NRG, 0);

        // check length
        if (input.length < 1)
            return  new ExecutionResult(ResultCode.INTERNAL_ERROR, nrg - DEFAULT_COST, "input too short".getBytes());

        byte[] hash = keccak256(input);
        return new ExecutionResult(ResultCode.SUCCESS, nrg - DEFAULT_COST - additionalNRG, hash);
    }
}
