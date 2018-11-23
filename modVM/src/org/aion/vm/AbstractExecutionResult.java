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

import org.aion.base.type.IExecutionResult;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/** An abstract class representing the result of either a transaction or contract execution. */
public abstract class AbstractExecutionResult implements IExecutionResult {
    ResultCode code;
    long nrgLeft;
    byte[] output;

    public enum ResultCode {
        /*
         * Indicates a successful transaction.
         */
        SUCCESS(0),

        /*
         * Indicates a runtime failure when executing the smart contract code.
         */
        FAILURE(1), // generic failure
        OUT_OF_NRG(2), // run out of energy
        BAD_INSTRUCTION(3), // bad FAStVM instruction
        BAD_JUMP_DESTINATION(4), // bad JUMP destination
        STACK_OVERFLOW(5), // stack overflow
        STACK_UNDERFLOW(6), // stack underflow
        REVERT(7), // the REVERT opcode triggered
        STATIC_MODE_ERROR(8), // trying to modify the state in a STATICCALL.

        /*
         * Indicates a transaction rejection. This usually happens before any code execution.
         */
        INVALID_NONCE(101), // invalid nonce
        INVALID_NRG_LIMIT(102), // invalid energy limit
        INSUFFICIENT_BALANCE(103), // balance is insufficient to cover the cost

        /*
         * Indicates an internal error, because of either bug or out-of-resource.
         * The node shall shut itself down when this occurs.
         */
        VM_REJECTED(-1), // VM is not willing to execute the code
        VM_INTERNAL_ERROR(-2); // VM internal implementation error

        private static Map<Integer, ResultCode> intMap = new HashMap<>();

        static {
            for (ResultCode code : ResultCode.values()) {
                intMap.put(code.val, code);
            }
        }

        private int val;

        ResultCode(int val) {
            this.val = val;
        }

        public static ResultCode fromInt(int code) {
            return intMap.get(code);
        }

        public int toInt() {
            return val;
        }
    }

    /**
     * Constructs a new AbstractExecutionResult with the specified output.
     *
     * @param code The result code.
     * @param nrgLeft The energy left after execution.
     * @param output The output of the execution.
     * @throws NullPointerException if code is null.
     */
    public AbstractExecutionResult(ResultCode code, long nrgLeft, byte[] output) {
        this.code = code;
        this.nrgLeft = nrgLeft;
        this.output = (output == null) ? ByteUtil.EMPTY_BYTE_ARRAY : output;
    }

    /**
     * Constructs a new ContractExecutionResult with no specified output. The output for this
     * AbstractExecutionResult is a zero-length byte array.
     *
     * @param code The result code.
     * @param nrgLeft The energy left after execution.
     * @throws NullPointerException if code is null.
     * @throws IllegalArgumentException if nrgLeft is negative.
     */
    public AbstractExecutionResult(ResultCode code, long nrgLeft) {
        this(code, nrgLeft, ByteUtil.EMPTY_BYTE_ARRAY);
    }

    /**
     * Returns a big-endian binary encoding of the AbstractExecutionResult.
     *
     * @return a big-endian binary encoding of the AbstractExecutionResult.
     */
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 8 + 4 + (output == null ? 0 : output.length));
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(code.toInt());
        buffer.putLong(nrgLeft);
        buffer.putInt(output == null ? 0 : output.length);
        if (output != null) {
            buffer.put(output);
        }
        return buffer.array();
    }

    /**
     * Returns an integer representation of the AbstractExecutionResult's code.
     *
     * @return an integer representation of the code.
     */
    public int getCode() {
        return code.toInt();
    }

    /**
     * Returns the AbstractExecutionResult's result code.
     *
     * @return the result code.
     */
    public ResultCode getResultCode() {
        return code;
    }

    /**
     * Returns the AbstractExecutionResult's remaining energy.
     *
     * @return the energy left.
     */
    public long getNrgLeft() {
        return nrgLeft;
    }

    /**
     * Returns the AbstractExecutionResult's output.
     *
     * @return the output.
     */
    public byte[] getOutput() {
        return output;
    }

    /**
     * Sets the AbstractExecutionResult's code to the code whose integer representation is code.
     *
     * @param code the integer representation of the code to be set.
     */
    public void setCode(int code) {
        ResultCode resCode = ResultCode.fromInt(code);
        this.code = (resCode == null) ? this.code : resCode;
    }

    /**
     * Sets the energy left for the AbstractExecutionResult to nrgLeft.
     *
     * @param nrgLeft The energy remaining.
     */
    public void setNrgLeft(long nrgLeft) {
        this.nrgLeft = nrgLeft;
    }

    /**
     * Sets the AbstractExecutionResult's result code to the code whose integer representation is
     * code and its remaining energy to nrgLeft.
     *
     * @param code The integer representation of the new code to be set.
     * @param nrgLeft The new remaining energy.
     * @throws IllegalArgumentException if code does not correspond to a ResultCode or nrgLeft is
     *     negative.
     */
    public void setCodeAndNrgLeft(int code, long nrgLeft) {
        setCode(code);
        setNrgLeft(nrgLeft);
    }

    /**
     * Sets the AbstractExecutionResult's output to output.
     *
     * @param output The new output.
     */
    public void setOutput(byte[] output) {
        this.output = output;
    }

    /**
     * Returns a string representation of the AbstractExecutionResult.
     *
     * @return a string representation of the AbstractExecutionResult.
     */
    @Override
    public String toString() {
        String out = (output == null) ? "" : Hex.toHexString(output);
        return "[code = " + code + ", nrgLeft = " + nrgLeft + ", output = " + out + "]";
    }
}
