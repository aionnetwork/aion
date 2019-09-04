package org.aion.precompiled;

import org.aion.precompiled.util.ByteUtil;
import org.aion.types.TransactionStatus;

public class PrecompiledTransactionResult {

    private TransactionStatus code;
    private byte[] output;
    private long energyRemaining;

    /**
     * Constructs a new {@code TransactionResult} with no side-effects, with zero energy remaining,
     * with an empty byte array as its output and {@link TransactionStatus#successful()} as its
     * result code.
     */
    public PrecompiledTransactionResult() {
        this.code = TransactionStatus.successful();
        this.output = new byte[0];
        this.energyRemaining = 0;
    }

    /**
     * Constructs a new {@code TransactionResult} with no side-effects and with the specified result
     * code and remaining energy.
     *
     * @param status The transaction result code.
     * @param energyRemaining The energy remaining after executing the transaction.
     */
    public PrecompiledTransactionResult(TransactionStatus status, long energyRemaining) {
        this.code = status;
        this.energyRemaining = energyRemaining;
        this.output = new byte[0];
    }

    /**
     * Constructs a new {@code TransactionResult} with no side-effects and with the specified result
     * code, remaining energy and output.
     *
     * @param status The transaction result code.
     * @param energyRemaining The energy remaining after executing the transaction.
     * @param output The output of executing the transaction.
     */
    public PrecompiledTransactionResult(
            TransactionStatus status, long energyRemaining, byte[] output) {
        this.code = status;
        this.output = (output == null) ? new byte[0] : output;
        this.energyRemaining = energyRemaining;
    }

    // TODO: document exception / maybe catch it and throw something more informative

    public void setResultCode(TransactionStatus code) {
        if (code == null) {
            throw new NullPointerException("Cannot set null result code.");
        }
        this.code = code;
    }

    public void setEnergyRemaining(long energyRemaining) {
        this.energyRemaining = energyRemaining;
    }

    public TransactionStatus getStatus() {
        return this.code;
    }

    public byte[] getReturnData() {
        return this.output;
    }

    public long getEnergyRemaining() {
        return this.energyRemaining;
    }

    @Override
    public String toString() {
        return "TransactionResult { code = "
                + this.code
                + ", energy remaining = "
                + this.energyRemaining
                + ", output = "
                + ByteUtil.toHexString(this.output)
                + " }";
    }
}
