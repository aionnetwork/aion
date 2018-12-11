package org.aion.vm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * A ExecutionResult is the result of a VM execution. It contains the VM status code, nrg usage,
 * output, etc.
 *
 * @author yulong
 */
public class ExecutionResult extends AbstractExecutionResult {

    /**
     * Constructs a new ExecutionResult.
     *
     * @param code The result code of the execution.
     * @param nrgLeft The energy remaining after the execution.
     * @param output The output of the execution.
     */
    public ExecutionResult(ResultCode code, long nrgLeft, byte[] output) {
        super(code, nrgLeft, output);
    }

    /**
     * Constructs a new ExecutionResult with no output.
     *
     * @param code The result code of the execution.
     * @param nrgLeft The energy remaining after the execution.
     */
    public ExecutionResult(ResultCode code, long nrgLeft) {
        super(code, nrgLeft);
    }

    /**
     * Constructs a new ExecutionResult from encoding, where encoding is a big-endian binary
     * encoding of an ExecutionResult.
     *
     * @param encoding A big-endian binary encoding of an ExecutionResult.
     */
    public static ExecutionResult parse(byte[] encoding) {
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
     * Returns true only if o is an ExecutionResult with the same code, energy left and output as
     * this ExecutionResult.
     *
     * @param o The object whose equality with this ExecutionResult is to be tested.
     * @return true only if o is equal to this ExecutionResult.
     */
    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (o == this) {
            return true;
        }
        if (!(o instanceof ExecutionResult)) {
            return false;
        }

        ExecutionResult other = (ExecutionResult) o;
        if (!this.code.equals(other.code)) {
            return false;
        }
        if (this.nrgLeft != other.nrgLeft) {
            return false;
        }
        return Arrays.equals(this.output, other.output);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.output) + ((int) this.nrgLeft) + this.code.toInt();
    }
}
