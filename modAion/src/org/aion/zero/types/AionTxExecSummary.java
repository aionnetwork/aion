package org.aion.zero.types;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static org.aion.util.biginteger.BIUtil.toBI;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.aion.mcf.core.TxTouchedStorage;
import org.aion.mcf.db.DetailsDataStore;
import org.aion.mcf.tx.TxExecSummary;
import org.aion.mcf.tx.TxReceipt;
import org.aion.mcf.types.IExecutionLog;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.mcf.vm.types.Log;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.types.AionAddress;

public class AionTxExecSummary implements TxExecSummary {

    /**
     * The receipt associated with {@link AionTransaction} that indicates the results of the
     * execution
     */
    private AionTxReceipt receipt;

    private BigInteger value = BigInteger.ZERO;

    private List<AionAddress> deletedAccounts = emptyList();
    private List<AionInternalTx> internalTransactions = emptyList();
    private Map<DataWordImpl, DataWordImpl> storageDiff = emptyMap();
    private TxTouchedStorage touchedStorage = new TxTouchedStorage();

    private byte[] result;
    private List<IExecutionLog> logs;

    /** Indicates whether the transaction failed */
    private TransactionStatus failed;

    /** RLP related, parsed indicates whether the class has already been deserialized. */
    private byte[] rlpEncoded;

    private boolean parsed;

    public AionTxExecSummary(AionTxReceipt receipt) {
        this.parsed = true;
        this.receipt = receipt;
        this.value = toBI(this.getReceipt().getTransaction().getValue());
    }

    public AionTxExecSummary(byte[] rlpEncoded) {
        this.rlpEncoded = rlpEncoded;
        this.parsed = false;
    }

    public void rlpParse() {
        if (parsed) {
            return;
        }

        RLPList decodedTxList = RLP.decode2(rlpEncoded);
        RLPList summary = (RLPList) decodedTxList.get(0);

        this.receipt = new AionTxReceipt(summary.get(0).getRLPData());
        this.value = decodeBigInteger(summary.get(1).getRLPData());
        this.deletedAccounts = decodeDeletedAccounts((RLPList) summary.get(2));
        this.touchedStorage = decodeTouchedStorage(summary.get(3));
        this.result = summary.get(4).getRLPData();
        this.logs = decodeLogs((RLPList) summary.get(5));
        byte[] failed = summary.get(6).getRLPData();
        int value = RLP.decodeInt(failed, 0);
        this.failed = TransactionStatus.getStatus(value);

        this.parsed = true;
    }

    private static BigInteger decodeBigInteger(byte[] encoded) {
        return isEmpty(encoded) ? BigInteger.ZERO : new BigInteger(1, encoded);
    }

    public byte[] getEncoded() {
        if (rlpEncoded != null) {
            return rlpEncoded;
        }

        // Note that the returned encoding does not have any information about internal transactions
        // TODO: Figure out whether we should remove internal txs from this entirely class entirely
        this.rlpEncoded =
                RLP.encodeList(
                        this.receipt.getEncoded(),
                        RLP.encodeBigInteger(this.value),
                        encodeDeletedAccounts(this.deletedAccounts),
                        encodeTouchedStorage(this.touchedStorage),
                        RLP.encodeElement(this.result),
                        encodeLogs(this.logs),
                        RLP.encodeInt(this.failed.getCode()));

        return rlpEncoded;
    }

    public static byte[] encodeTouchedStorage(TxTouchedStorage touchedStorage) {
        Collection<TxTouchedStorage.Entry> entries = touchedStorage.getEntries();
        byte[][] result = new byte[entries.size()][];

        int i = 0;
        for (TxTouchedStorage.Entry entry : entries) {
            byte[] key = RLP.encodeElement(entry.getKey().getData());
            byte[] value = RLP.encodeElement(entry.getValue().getData());
            byte[] changed = RLP.encodeInt(entry.isChanged() ? 1 : 0);

            result[i++] = RLP.encodeList(key, value, changed);
        }

        return RLP.encodeList(result);
    }

    protected static TxTouchedStorage decodeTouchedStorage(RLPElement encoded) {
        TxTouchedStorage result = new TxTouchedStorage();

        for (RLPElement entry : (RLPList) encoded) {
            RLPList asList = (RLPList) entry;

            DataWordImpl key = new DataWordImpl(asList.get(0).getRLPData());
            DataWordImpl value = new DataWordImpl(asList.get(1).getRLPData());
            byte[] changedBytes = asList.get(2).getRLPData();
            boolean changed = isNotEmpty(changedBytes) && RLP.decodeInt(changedBytes, 0) == 1;

            result.add(new TxTouchedStorage.Entry(key, value, changed));
        }

        return result;
    }

    private static List<IExecutionLog> decodeLogs(RLPList logs) {
        ArrayList<IExecutionLog> result = new ArrayList<>();
        for (RLPElement log : logs) {
            result.add(new Log(log.getRLPData()));
        }
        return result;
    }

    private static byte[] encodeLogs(List<IExecutionLog> logs) {
        byte[][] result = new byte[logs.size()][];
        for (int i = 0; i < logs.size(); i++) {
            IExecutionLog log = logs.get(i);
            result[i] = log.getEncoded();
        }

        return RLP.encodeList(result);
    }

    private static byte[] encodeStorageDiff(Map<DataWordImpl, DataWordImpl> storageDiff) {
        byte[][] result = new byte[storageDiff.size()][];
        int i = 0;
        for (Map.Entry<DataWordImpl, DataWordImpl> entry : storageDiff.entrySet()) {
            byte[] key = RLP.encodeElement(entry.getKey().getData());
            byte[] value = RLP.encodeElement(entry.getValue().getData());
            result[i++] = RLP.encodeList(key, value);
        }
        return RLP.encodeList(result);
    }

    private static Map<DataWordImpl, DataWordImpl> decodeStorageDiff(RLPList storageDiff) {
        Map<DataWordImpl, DataWordImpl> result = new HashMap<>();
        for (RLPElement entry : storageDiff) {
            DataWordImpl key = new DataWordImpl(((RLPList) entry).get(0).getRLPData());
            DataWordImpl value = new DataWordImpl(((RLPList) entry).get(1).getRLPData());
            result.put(key, value);
        }
        return result;
    }

    private static byte[] encodeDeletedAccounts(List<AionAddress> deletedAccounts) {
        byte[][] result = new byte[deletedAccounts.size()][];
        for (int i = 0; i < deletedAccounts.size(); i++) {
            result[i] = RLP.encodeElement(deletedAccounts.get(i).toByteArray());
        }
        return RLP.encodeList(result);
    }

    private static List<AionAddress> decodeDeletedAccounts(RLPList deletedAccounts) {
        List<AionAddress> result = new ArrayList<>();
        for (RLPElement deletedAccount : deletedAccounts) {
            result.add(new AionAddress(deletedAccount.getRLPData()));
        }
        return result;
    }

    public AionTransaction getTransaction() {
        if (!parsed) {
            rlpParse();
        }
        return this.getReceipt().getTransaction();
    }

    public byte[] getTransactionHash() {
        return getTransaction().getTransactionHash();
    }

    public BigInteger getValue() {
        if (!parsed) {
            rlpParse();
        }
        return value;
    }

    public List<AionAddress> getDeletedAccounts() {
        if (!parsed) {
            rlpParse();
        }
        return deletedAccounts;
    }

    public List<AionInternalTx> getInternalTransactions() {
        if (!parsed) {
            rlpParse();
        }
        return internalTransactions;
    }

    @Deprecated
    /* Use getTouchedStorage().getAll() instead */
    public Map<DataWordImpl, DataWordImpl> getStorageDiff() {
        if (!parsed) {
            rlpParse();
        }
        return storageDiff;
    }

    /**
     * Indicates whether the transaction was rejected.
     *
     * @see TransactionStatus
     * @return
     */
    public boolean isRejected() {
        if (!parsed) {
            rlpParse();
        }
        return failed == TransactionStatus.REJECTED;
    }

    /**
     * To keep compatibility with any previously written code, isFailed() now returns true both when
     * the transaction was rejected or the VM failed.
     *
     * @see TransactionStatus
     * @return
     */
    public boolean isFailed() {
        if (!parsed) {
            rlpParse();
        }
        return failed == TransactionStatus.FAILED || failed == TransactionStatus.REJECTED;
    }

    public byte[] getResult() {
        if (!parsed) {
            rlpParse();
        }
        return result;
    }

    public List<IExecutionLog> getLogs() {
        if (!parsed) {
            rlpParse();
        }
        return logs;
    }

    public AionTxReceipt getReceipt() {
        if (!parsed) {
            rlpParse();
        }

        return this.receipt;
    }

    public BigInteger getRefund() {
        if (!parsed) {
            rlpParse();
        }

        BigInteger energyLimit = BigInteger.valueOf(this.getTransaction().getEnergyLimit());
        BigInteger energyUsed = BigInteger.valueOf(this.getReceipt().getEnergyUsed());
        return energyLimit
                .subtract(energyUsed)
                .multiply(BigInteger.valueOf(this.getTransaction().getEnergyPrice()));
    }

    public BigInteger getFee() {
        if (!parsed) {
            rlpParse();
        }
        return BigInteger.valueOf(this.getReceipt().getEnergyUsed())
                .multiply(BigInteger.valueOf(this.getTransaction().getEnergyPrice()));
    }

    public BigInteger getNrgUsed() {
        return BigInteger.valueOf(this.getReceipt().getEnergyUsed());
    }

    public TxTouchedStorage getTouchedStorage() {
        return touchedStorage;
    }

    public static Builder builderFor(AionTxReceipt receipt) {
        return new Builder(receipt);
    }

    @Override
    public Object getBuilder(TxReceipt receipt) {
        return builderFor((AionTxReceipt) receipt);
    }

    /**
     * Builder for {@link AionTxExecSummary}, responsible for the correct creation of the
     * transaction summary. Contains all elements useful for referencing both the transactions and
     * the results of the transaction. This also includes results like which rows of the storage
     * (account storage {@link DetailsDataStore}) were {@code touched} by virtual machine.
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

        public Builder internalTransactions(List<AionInternalTx> internalTransactions) {
            summary.internalTransactions = unmodifiableList(internalTransactions);
            return this;
        }

        public Builder deletedAccounts(List<AionAddress> list) {
            summary.deletedAccounts = new ArrayList<>();
            summary.deletedAccounts.addAll(list);
            return this;
        }

        public Builder storageDiff(Map<DataWordImpl, DataWordImpl> storageDiff) {
            summary.storageDiff = unmodifiableMap(storageDiff);
            return this;
        }

        public Builder touchedStorage(
                Map<DataWordImpl, DataWordImpl> touched, Map<DataWordImpl, DataWordImpl> changed) {
            summary.touchedStorage.addReading(touched);
            summary.touchedStorage.addWriting(changed);
            return this;
        }

        public Builder markAsRejected() {
            summary.failed = TransactionStatus.REJECTED;
            return this;
        }

        public Builder markAsFailed() {
            summary.failed = TransactionStatus.FAILED;
            return this;
        }

        public Builder logs(List<IExecutionLog> logs) {
            summary.logs = logs;
            return this;
        }

        public Builder result(byte[] result) {
            summary.result = result;
            return this;
        }

        public AionTxExecSummary build() {
            summary.parsed = true;

            Objects.requireNonNull(summary.getResult());
            Objects.requireNonNull(summary.getReceipt());
            Objects.requireNonNull(summary.getReceipt().getTransaction());

            if (summary.logs == null) {
                summary.logs = Collections.emptyList();
            }

            if (summary.internalTransactions == null)
                summary.internalTransactions = Collections.emptyList();

            if (summary.failed != null && summary.failed != TransactionStatus.SUCCESS) {
                for (AionInternalTx transaction : summary.internalTransactions) {
                    transaction.markAsRejected();
                }
            }
            return summary;
        }
    }

    public enum TransactionStatus {

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

        TransactionStatus(int code) {
            this.code = code;
        }

        public int getCode() {
            return this.code;
        }

        public static TransactionStatus getStatus(int code) {
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
}
