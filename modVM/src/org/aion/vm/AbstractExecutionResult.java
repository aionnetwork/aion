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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import org.aion.base.type.IExecutionResult;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;

public abstract class AbstractExecutionResult implements IExecutionResult {

    private ResultCode code;
    private long nrgLeft;
    private byte[] output;
    public AbstractExecutionResult(byte[] result) {
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.order(ByteOrder.BIG_ENDIAN);

        ResultCode code = ResultCode.fromInt(buffer.getInt());
        long nrgLeft = buffer.getLong();
        byte[] output = new byte[buffer.getInt()];
        buffer.get(output);

        this.code = code;
        this.nrgLeft = nrgLeft;
        this.output = output;
    }
    /**
     * Constructs a new ExecutionResult with output.
     *
     * @param code The result code.
     * @param nrgLeft The energy left after execution.
     * @param output The output of the execution.
     */
    public AbstractExecutionResult(ResultCode code, long nrgLeft, byte[] output) {
        this.code = code;
        this.nrgLeft = nrgLeft;
        this.output = output;
    }

    /**
     * Constructs a new ContractExecutionResult with no output.
     *
     * @param code The result code.
     * @param nrgLeft The energy left after execution.
     */
    public AbstractExecutionResult(ResultCode code, long nrgLeft) {
        this(code, nrgLeft, ByteUtil.EMPTY_BYTE_ARRAY);
    }

    /**
     * Encode execution resul tinto byte array.
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
     * Returns this ContractExecutionResult's result code.
     *
     * @return the result code.
     */
    public int getCode() {
        return code.toInt();
    }

    /**
     * Sets the code.
     */
    public void setCode(int code) {
        this.code = ResultCode.fromInt(code);
    }

    public ResultCode getResultCode() {
        return code;
    }

    /**
     * Returns this ContractExecutionResult's energy left.
     *
     * @return the energy left.
     */
    public long getNrgLeft() {
        return nrgLeft;
    }

    /**
     * Sets nrg left.
     */
    public void setNrgLeft(long nrgLeft) {
        this.nrgLeft = nrgLeft;
    }

    /**
     * Sets this ContractExecutionResult's code to code and its energy left to nrgLeft.
     *
     * @param code The new code.
     * @param nrgLeft The new nrgLeft.
     */
    public void setCodeAndNrgLeft(int code, long nrgLeft) {
        setCode(code);
        this.nrgLeft = nrgLeft;
    }

    /**
     * Returns this ContractExecutionResult's output.
     *
     * @return the result output.
     */
    public byte[] getOutput() {
        return output;
    }

    /**
     * Sets the output.
     */
    public void setOutput(byte[] output) {
        this.output = output;
    }

    public String toString() {
        return "[code = " + code + ", nrgLeft = " + nrgLeft + ", output = " + Hex
            .toHexString(output) + "]";
    }

    public enum ResultCode {
        SUCCESS(0),
        FAILURE(1),
        OUT_OF_NRG(2),
        BAD_INSTRUCTION(3),
        BAD_JUMP_DESTINATION(4),
        STACK_OVERFLOW(5),
        STACK_UNDERFLOW(6),
        REVERT(7),
        INVALID_NONCE(8),
        INVALID_NRG_LIMIT(9),
        INSUFFICIENT_BALANCE(10),
        CONTRACT_ALREADY_EXISTS(11),
        INTERNAL_ERROR(-1);

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
}
