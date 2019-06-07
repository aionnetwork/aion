package org.aion.mcf.blockchain;

import java.math.BigInteger;
import org.aion.types.AionAddress;
import org.aion.interfaces.tx.Transaction;
import org.aion.util.bytes.ByteUtil;

/**
 * Abstract Pending Transaction Class.
 *
 * @param <TX>
 */
public abstract class AbstractPendingTx<TX extends Transaction> {

    protected TX transaction;

    protected long blockNumber;

    public AbstractPendingTx(byte[] bytes) {
        parse(bytes);
    }

    public AbstractPendingTx(TX transaction) {
        this(transaction, 0);
    }

    public AbstractPendingTx(TX transaction, long blockNumber) {
        this.transaction = transaction;
        this.blockNumber = blockNumber;
    }

    protected abstract void parse(byte[] bs);

    public TX getTransaction() {
        return transaction;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public AionAddress getSender() {
        return transaction.getSenderAddress();
    }

    public byte[] getHash() {
        return transaction.getTransactionHash();
    }

    public byte[] getBytes() {
        byte[] numberBytes = BigInteger.valueOf(blockNumber).toByteArray();
        byte[] txBytes = transaction.getEncoded();
        byte[] bytes = new byte[1 + numberBytes.length + txBytes.length];

        bytes[0] = (byte) numberBytes.length;
        System.arraycopy(numberBytes, 0, bytes, 1, numberBytes.length);
        System.arraycopy(txBytes, 0, bytes, 1 + numberBytes.length, txBytes.length);

        return bytes;
    }

    @Override
    public String toString() {
        return "PendingTransaction ["
                + "  transaction="
                + transaction
                + ", blockNumber="
                + blockNumber
                + ']';
    }

    @Override
    public int hashCode() {
        return ByteUtil.byteArrayToInt(getSender().toByteArray())
                + ByteUtil.byteArrayToInt(transaction.getNonce());
    }
}
