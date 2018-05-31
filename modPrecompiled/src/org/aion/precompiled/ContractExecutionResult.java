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
package org.aion.precompiled;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import org.aion.base.util.ByteUtil;

/**
 * The result of executing a pre-compiled contract.
 */
public class ContractExecutionResult {
    private ResultCode code;
    private long nrgLeft;
    private byte[] output;

    public enum ResultCode {
        SUCCESS(0),
        FAILURE(1),
        OUT_OF_NRG(2),
        REVERT(3),
        INVALID_NONCE(4),
        INVALID_NRG_LIMIT(5),
        INSUFFICIENT_BALANCE(6),
        INTERNAL_ERROR(-1);

        private int val;
        private static Map<Integer, ResultCode> intMap = new HashMap<>();

        static {
            for (ResultCode code : ResultCode.values()) {
                intMap.put(code.val, code);
            }
        }

        ResultCode(int code) {
            this.val = code;
        }
        public static ResultCode fromInt(int code) {
            return intMap.get(code);
        }
        public int toInt() {
            return this.val;
        }
    }

    /**
     * Constructs a new ContractExecutionResult with output.
     *
     * @param code The result code.
     * @param nrgLeft The energy left after execution.
     * @param output The output of the contract execution.
     */
    public ContractExecutionResult(ResultCode code, long nrgLeft, byte[] output) {
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
    public ContractExecutionResult(ResultCode code, long nrgLeft) {
        this(code, nrgLeft, ByteUtil.EMPTY_BYTE_ARRAY);
    }

    /**
     * Parses and returns a new ContractExecutionResult from a byte array.
     */
    public static ContractExecutionResult parse(byte[] result) {
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.order(ByteOrder.BIG_ENDIAN);

        ResultCode code = ResultCode.fromInt(buffer.getInt());
        long nrgLeft = buffer.getLong();
        byte[] output = new byte[buffer.getInt()];
        buffer.get(output);

        return new ContractExecutionResult(code, nrgLeft, output);
    }

    //TODO

}
