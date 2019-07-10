package org.aion.base;

import org.aion.crypto.HashUtil;
import org.aion.types.AionAddress;

public class TransactionUtil {
    public static long getTransactionCost(AionTransaction tx) {
        long zeroes = zeroBytesInData(tx.getData());
        long nonZeroes = tx.getData().length - zeroes;

        return (tx.isContractCreationTransaction() ? Constants.NRG_CREATE_CONTRACT_MIN : 0)
                + Constants.NRG_TRANSACTION_MIN
                + zeroes * Constants.NRG_TX_DATA_ZERO
                + nonZeroes * Constants.NRG_TX_DATA_NONZERO;
    }

    private static long zeroBytesInData(byte[] data) {
        if (data == null) {
            return 0;
        }

        int c = 0;
        for (byte b : data) {
            c += (b == 0) ? 1 : 0;
        }
        return c;
    }

    public static AionAddress calculateContractAddress(AionTransaction tx) {
        if (tx.getDestinationAddress() != null) {
            return null;
        }
        return new AionAddress(
                HashUtil.calcNewAddr(tx.getSenderAddress().toByteArray(), tx.getNonce()));
    }

    public static byte[] hashWithoutSignature(AionTransaction tx) {
        byte[] plainMsg = TransactionRlpCodec.getEncodingNoSignature(tx);
        return HashUtil.h256(plainMsg);
    }
}
