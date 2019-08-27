package org.aion.mcf.types;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static org.aion.util.biginteger.BIUtil.toBI;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.aion.base.AionTransaction;
import org.aion.mcf.vm.types.InternalTransactionUtil;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.Log;

public class AionTxExecSummary {

    /**
     * The receipt associated with {@link AionTransaction} that indicates the results of the
     * execution
     */
    private AionTxReceipt receipt;

    private BigInteger value = BigInteger.ZERO;

    private List<AionAddress> deletedAccounts = emptyList();
    private List<InternalTransaction> internalTransactions = emptyList();

    private byte[] result;
    private List<Log> logs;

    /** Indicates whether the transaction failed */
    private TransactionSummaryStatus failed;

    public AionTxExecSummary(AionTxReceipt receipt) {
        this.receipt = receipt;
        this.value = toBI(this.getReceipt().getTransaction().getValue());
    }

    public AionTransaction getTransaction() {
        return this.getReceipt().getTransaction();
    }

    public byte[] getTransactionHash() {
        return getTransaction().getTransactionHash();
    }

    public BigInteger getValue() {
        return value;
    }

    public List<AionAddress> getDeletedAccounts() {
        return deletedAccounts;
    }

    public List<InternalTransaction> getInternalTransactions() {
        return internalTransactions;
    }

    /**
     * Indicates whether the transaction was rejected.
     *
     * @see TransactionSummaryStatus
     * @return
     */
    public boolean isRejected() {
        return failed == TransactionSummaryStatus.REJECTED;
    }

    /**
     * To keep compatibility with any previously written code, isFailed() now returns true both when
     * the transaction was rejected or the VM failed.
     *
     * @see TransactionSummaryStatus
     * @return
     */
    public boolean isFailed() {
        return failed == TransactionSummaryStatus.FAILED || failed == TransactionSummaryStatus.REJECTED;
    }

    public byte[] getResult() {
        return result;
    }

    public List<Log> getLogs() {
        return logs;
    }

    public AionTxReceipt getReceipt() {
        return this.receipt;
    }

    public BigInteger getRefund() {
        BigInteger energyLimit = BigInteger.valueOf(this.getTransaction().getEnergyLimit());
        BigInteger energyUsed = BigInteger.valueOf(this.getReceipt().getEnergyUsed());
        return energyLimit
                .subtract(energyUsed)
                .multiply(BigInteger.valueOf(this.getTransaction().getEnergyPrice()));
    }

    public BigInteger getFee() {
        return BigInteger.valueOf(this.getReceipt().getEnergyUsed())
                .multiply(BigInteger.valueOf(this.getTransaction().getEnergyPrice()));
    }

    public BigInteger getNrgUsed() {
        return BigInteger.valueOf(this.getReceipt().getEnergyUsed());
    }

    public static Builder builderFor(AionTxReceipt receipt) {
        return new Builder(receipt);
    }

    /**
     * Builder for {@link AionTxExecSummary}, responsible for the correct creation of the
     * transaction summary. Contains all elements useful for referencing both the transactions and
     * the results of the transaction. This also includes results like which rows of the account
     * storage were {@code touched} by virtual machine.
     *
     * <p>Prefer using this builder, as rules will be enforced and the system will fast fail.
     *
     * <p>Any non-critical elements will be set to a default value
     */
    public static class Builder {

        private final AionTxExecSummary summary;

        public Builder(AionTxReceipt receipt) {
            summary = new AionTxExecSummary(receipt);
        }

        public Builder internalTransactions(List<InternalTransaction> internalTransactions) {
            summary.internalTransactions = unmodifiableList(internalTransactions);
            return this;
        }

        public Builder deletedAccounts(List<AionAddress> list) {
            summary.deletedAccounts = new ArrayList<>();
            summary.deletedAccounts.addAll(list);
            return this;
        }

        public Builder markAsRejected() {
            summary.failed = TransactionSummaryStatus.REJECTED;
            return this;
        }

        public Builder markAsFailed() {
            summary.failed = TransactionSummaryStatus.FAILED;
            return this;
        }

        public Builder logs(List<Log> logs) {
            summary.logs = logs;
            return this;
        }

        public Builder result(byte[] result) {
            summary.result = result;
            return this;
        }

        public AionTxExecSummary build() {
            Objects.requireNonNull(summary.getResult());
            Objects.requireNonNull(summary.getReceipt());
            Objects.requireNonNull(summary.getReceipt().getTransaction());

            if (summary.logs == null) {
                summary.logs = Collections.emptyList();
            }

            if (summary.internalTransactions == null) {
                summary.internalTransactions = Collections.emptyList();
            }

            if (summary.failed != null && summary.failed != TransactionSummaryStatus.SUCCESS) {
                summary.internalTransactions =
                        InternalTransactionUtil.createRejectedTransactionList(
                                summary.internalTransactions);
            }
            return summary;
        }
    }

    public enum TransactionSummaryStatus {

        /**
         * Rejected indicates the transaction was not valid for processing this indicates the
         * transaction should not be included in the block. Subsequently, no effort was made to
         * process this transaction.
         */
        REJECTED(0),

        /**
         * Failed indicates the transaction failed to process, this can occur due to OutOfEnergy
         * errors or VM Exception errors. Transactions that Failed are still placed into the block,
         * with corresponding consequences for the sender.
         */
        FAILED(1),

        /**
         * Successful transaction are transactions that were processed successfully these are placed
         * into the block.
         */
        SUCCESS(2);

        private int code;

        TransactionSummaryStatus(int code) {
            this.code = code;
        }

        public int getCode() {
            return this.code;
        }

        public static TransactionSummaryStatus getStatus(int code) {
            switch (code) {
                case 0:
                    return REJECTED;
                case 1:
                    return FAILED;
                case 2:
                    return SUCCESS;
            }
            return null;
        }
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();

        s.append(receipt.toString()).append("\n");
        s.append("value= ").append(value).append("\n");
        s.append("deletedAccounts [");
        if (!deletedAccounts.isEmpty()) {
            s.append("\n");
            for (AionAddress a : deletedAccounts) {
                s.append("  ").append(a).append("\n");
            }
        }
        s.append("]\n");

        return s.toString();
    }
}
