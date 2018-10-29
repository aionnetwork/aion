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

import static org.aion.crypto.HashUtil.blake128;
import static org.aion.crypto.HashUtil.blake256;

import com.google.common.annotations.VisibleForTesting;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionResult;
import org.aion.vm.IPrecompiledContract;

public class Blake2bHashContract implements IPrecompiledContract {

    private static final long COST = 30L;
    private static final int WORD_LENGTH = 4;
    private static final String INPUT_LENGTH_ERROR_MESSAGE = "input too short";
    private static final String OPERATION_ERROR_MESSAGE = "invalid operation";

    public Blake2bHashContract() {
    }

    /**
     * Returns the hash of given input
     *
     * <p>input is defined as: [1b operator] 0 for blake256, 1 for blake128 [nb input byte array]
     *
     * <p>the returned hash is in ExecutionResult.getOutput
     */
    public ExecutionResult execute(byte[] input, long nrg) {

        // check length
        if (input.length < 2) {
            return new ExecutionResult(
                ResultCode.INTERNAL_ERROR, nrg - COST, INPUT_LENGTH_ERROR_MESSAGE.getBytes());
        }

        long additionalNRG = ((long) Math.ceil(((double) input.length - 1) / WORD_LENGTH)) * 6;

        // check input nrg
        long nrgLeft = nrg - (COST + additionalNRG);

        if (nrgLeft < 0) {
            return new ExecutionResult(ResultCode.OUT_OF_NRG, 0);
        }

        // check operation number
        int operation = input[0];

        switch (operation) {
            case 0:
                return blake256Hash(input, nrgLeft);
            case 1:
                return blake128Hash(input, nrgLeft);
            default:
                return new ExecutionResult(
                    ResultCode.INTERNAL_ERROR, nrg - COST, OPERATION_ERROR_MESSAGE.getBytes());
        }
    }

    private ExecutionResult blake256Hash(byte[] input, long nrg) {
        byte[] byteArray = new byte[input.length - 1];
        System.arraycopy(input, 1, byteArray, 0, input.length - 1);
        byte[] hash = blake256(byteArray);
        return new ExecutionResult(ResultCode.SUCCESS, nrg, hash);
    }

    private ExecutionResult blake128Hash(byte[] input, long nrg) {
        byte[] byteArray = new byte[input.length - 1];
        System.arraycopy(input, 1, byteArray, 0, input.length - 1);
        byte[] hash = blake128(byteArray);
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
