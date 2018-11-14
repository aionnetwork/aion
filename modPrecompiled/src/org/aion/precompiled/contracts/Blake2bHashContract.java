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
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionResult;
import org.aion.vm.IPrecompiledContract;

public class Blake2bHashContract implements IPrecompiledContract {

    private static final long COST = 30L;
    private static final int WORD_LENGTH = 4;
    private static final String INPUT_LENGTH_ERROR_MESSAGE = "input too short";

    public Blake2bHashContract() {}

    /**
     * Returns the hash of given input
     *
     * <p>the returned hash is in ExecutionResult.getOutput
     *
     * <p>the maximum data hash is 1M bytes
     */
    public ExecutionResult execute(byte[] input, long nrg) {

        // check length
        if (input == null || input.length == 0 || input.length > 1_048_576L) {
            return new ExecutionResult(
                    ResultCode.FAILURE, nrg - COST, INPUT_LENGTH_ERROR_MESSAGE.getBytes());
        }

        long additionalNRG = ((long) Math.ceil(((double) input.length - 1) / WORD_LENGTH)) * 6;

        // check input nrg
        long nrgLeft = nrg - (COST + additionalNRG);

        if (nrgLeft < 0) {
            return new ExecutionResult(ResultCode.OUT_OF_NRG, 0);
        }

        return blake256Hash(input, nrgLeft);
    }

    private ExecutionResult blake256Hash(byte[] input, long nrg) {
        byte[] hash = blake256(input);
        return new ExecutionResult(ResultCode.SUCCESS, nrg, hash);
    }

    @VisibleForTesting
    public static byte[] setupInput(int operation, byte[] inputByteArray) {
        byte[] ret = new byte[1 + inputByteArray.length];
        ret[0] = (byte) operation;
        System.arraycopy(inputByteArray, 0, ret, 1, inputByteArray.length);
        return ret;
    }
}
