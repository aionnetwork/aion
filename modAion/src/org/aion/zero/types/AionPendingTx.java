package org.aion.zero.types;

import java.math.BigInteger;
import java.util.Arrays;
import org.aion.mcf.blockchain.AbstractPendingTx;

/** aion pending transaction class. */
public class AionPendingTx extends AbstractPendingTx<AionTransaction> {

    public AionPendingTx(byte[] bs) {
        super(bs);
    }

    public AionPendingTx(AionTransaction transaction) {
        super(transaction, 0);
    }

    public AionPendingTx(AionTransaction tx, long bn) {
        super(tx, bn);
    }

    protected void parse(byte[] bytes) {
        byte[] numberBytes = new byte[bytes[0]];
        byte[] txBytes = new byte[bytes.length - 1 - numberBytes.length];

        System.arraycopy(bytes, 1, numberBytes, 0, numberBytes.length);
        System.arraycopy(bytes, 1 + numberBytes.length, txBytes, 0, txBytes.length);

        this.blockNumber = new BigInteger(numberBytes).longValue();
        this.transaction = new AionTransaction(txBytes);
    }

    /** Two pending transaction are equal if equal their sender + nonce */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AionPendingTx)) {
            return false;
        }

        AionPendingTx that = (AionPendingTx) o;

        return getSender().equals(that.getSender())
                && Arrays.equals(transaction.getNonce(), that.getTransaction().getNonce());
    }
}
