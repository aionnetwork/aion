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

import static org.aion.crypto.HashUtil.blake256;

import com.google.common.annotations.VisibleForTesting;
import org.aion.vm.api.ResultCode;
import org.aion.vm.api.TransactionResult;
import org.aion.vm.IPrecompiledContract;

/**
 * @author Jay Tseng
 * @author William Zhai
 * @implNote Base on benchmark the keccak256hash and blake2bhash precompiled contract blake2b is
 *     5 times faster then keccak256. Therefore, blake2b modify the energy charge to 1/3 of the
 *     Ethereum keccak256 precompiled contract charge.
 */
public class Blake2bHashContract implements IPrecompiledContract {

    private static final long COST = 10L;
    private static final int WORD_LENGTH = 4;
    private static final int NRG_CHARGE_PER_WORD = 2;
    private static final String INPUT_LENGTH_ERROR_MESSAGE = "incorrect size of the input data.";

    public Blake2bHashContract() {}

    /**
     * Returns the hash of given input
     *
     * @param input data input; must be less or equal than 2 MB
     * @return the returned blake2b 256bits hash is in ExecutionResult.getOutput
     */
    public TransactionResult execute(byte[] input, long nrg) {

        // check length
        if (input == null || input.length == 0 || input.length > 2_097_152L) {
            return new TransactionResult(
                ResultCode.FAILURE, nrg - COST, INPUT_LENGTH_ERROR_MESSAGE.getBytes());
        }

        long additionalNRG =
                ((long) Math.ceil(((double) input.length - 1) / WORD_LENGTH)) * NRG_CHARGE_PER_WORD;

        // check input nrg
        long nrgLeft = nrg - (COST + additionalNRG);

        if (nrgLeft < 0) {
            return new TransactionResult(ResultCode.OUT_OF_ENERGY, 0);
        }

        return blake256Hash(input, nrgLeft);
    }

    private TransactionResult blake256Hash(byte[] input, long nrg) {
        byte[] hash = blake256(input);
        return new TransactionResult(ResultCode.SUCCESS, nrg, hash);
    }

    @VisibleForTesting
    static byte[] setupInput(int operation, byte[] inputByteArray) {
        byte[] ret = new byte[1 + inputByteArray.length];
        ret[0] = (byte) operation;
        System.arraycopy(inputByteArray, 0, ret, 1, inputByteArray.length);
        return ret;
    }
}
