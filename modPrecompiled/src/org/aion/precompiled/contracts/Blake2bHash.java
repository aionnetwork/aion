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
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.type.StatefulPrecompiledContract;

import static org.aion.crypto.HashUtil.*;

public class Blake2bHash extends StatefulPrecompiledContract{
    private final static long COST = 100L;
    private static final String INPUT_LENGTH_ERROR_MESSAGE = "input too short";
    private static final String OPERATION_ERROR_MESSAGE = "invalid operation";

    public Blake2bHash(IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track) {
        super(track);
    }

    /**
     * Returns the hash of given input
     *
     * input is defined as:
     *      [1b operator] 0 for blake256, 1 for blake128
     *      [nb input byte array]
     *
     * the returned hash is in ContractExecutionResult.getOutput
     */
    public ContractExecutionResult execute(byte[] input, long nrg){
        long additionalNRG = Math.round(Math.sqrt(input.length));
        // check input nrg
        if (nrg < COST + additionalNRG)
            return new ContractExecutionResult(ResultCode.OUT_OF_NRG, 0);

        // check length
        if (input.length < 2)
            return  new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST, INPUT_LENGTH_ERROR_MESSAGE.getBytes());

        // check operation number
        int operation = input[0];

        switch (operation){
            case 0:
                return blake256Hash(input, nrg + additionalNRG);
            case 1:
                return blake128Hash(input, nrg + additionalNRG);
            default:
                return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST, OPERATION_ERROR_MESSAGE.getBytes());
        }
    }

    private ContractExecutionResult blake256Hash(byte[] input, long nrg){
        byte[] byteArray = new byte[input.length - 1];
        System.arraycopy(input, 1, byteArray, 0, input.length - 1);
        byte[] hash = blake256(byteArray);
        return new ContractExecutionResult(ResultCode.SUCCESS, nrg - COST, hash);

    }

    private ContractExecutionResult blake128Hash(byte[] input, long nrg){
        byte[] byteArray = new byte[input.length - 1];
        System.arraycopy(input, 1, byteArray, 0, input.length - 1);
        byte[] hash = blake128(byteArray);
        return new ContractExecutionResult(ResultCode.SUCCESS, nrg - COST, hash);
    }

    public static byte[] setupInput(int operation, byte[] inputByteArray){
        byte[] ret = new byte[1 + inputByteArray.length];
        ret[0] = (byte) operation;
        System.arraycopy(inputByteArray, 0, ret, 1, inputByteArray.length);
        return ret;
    }

}
