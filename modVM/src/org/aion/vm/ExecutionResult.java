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
import java.util.Arrays;
import javax.annotation.Nonnull;

/**
 * A ExecutionResult is the result of a VM execution. It contains the VM status
 * code, nrg usage, output, etc.
 *
 * @author yulong
 */
public final class ExecutionResult extends AbstractExecutionResult {

    /**
     * Constructs a new ExecutionResult.
     *
     * @param code The result code of the execution.
     * @param nrgLeft The energy remaining after the execution.
     * @param output The output of the execution.
     */
    public ExecutionResult(@Nonnull ResultCode code, long nrgLeft, byte[] output) {
        super(code, nrgLeft, output);
    }

    /**
     * Constructs a new ExecutionResult with no output.
     *
     * @param code The result code of the execution.
     * @param nrgLeft The energy remaining after the execution.
     */
    public ExecutionResult(@Nonnull ResultCode code, long nrgLeft) {
        super(code, nrgLeft);
    }

    /**
     * Constructs a new ExecutionResult from encoding, where encoding is a big-endian binary encoding
     * of an ExecutionResult.
     *
     * @param encoding A big-endian binary encoding of an ExecutionResult.
     */
    public static ExecutionResult parse(@Nonnull byte[] encoding) {
        ByteBuffer buffer = ByteBuffer.wrap(encoding);
        buffer.order(ByteOrder.BIG_ENDIAN);

        ResultCode code = ResultCode.fromInt(buffer.getInt());
        long nrgLeft = buffer.getLong();
        byte[] output;

        int len = buffer.getInt();
        if (len >= 0) {
            output = new byte[len];
            buffer.get(output);
        } else {
            output = null;
        }

        return new ExecutionResult(code, nrgLeft, output);
    }

    /**
     * Returns a big-endian binary encoding of this ExecutionResult.
     *
     * @return a big-endian binary encoding of this ExecutionResult.
     */
    public final byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 8 + 4 + (output == null ? 0 : output.length));
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(code.toInt());
        buffer.putLong(nrgLeft);
        buffer.putInt(output == null ? -1 : output.length);
        if (output != null) {
            buffer.put(output);
        }
        return buffer.array();
    }

    /**
     * Returns true only if o is an ExecutionResult with the same code, energy left and output as
     * this ExecutionResult.
     *
     * @param o The object whose equality with this ExecutionResult is to be tested.
     * @return true only if o is equal to this ExecutionResult.
     */
    @Override
    public boolean equals(Object o) {
        if (o == null) { return false; }
        if (o == this) { return true; }
        if (!(o instanceof ExecutionResult)) { return false; }

        ExecutionResult other = (ExecutionResult) o;
        if (!this.code.equals(other.code)) { return false; }
        if (this.nrgLeft != other.nrgLeft) { return false; }
        return Arrays.equals(this.output, other.output);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.output) + ((int) this.nrgLeft) + this.code.toInt();
    }
}
