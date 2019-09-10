package org.aion.precompiled.contracts;

import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.precompiled.util.AddressUtils;
import org.aion.precompiled.type.PrecompiledContract;
import org.aion.types.TransactionStatus;

public class EDVerifyContract implements PrecompiledContract {

    // set to a default cost for now, this will need to be adjusted
    private static final long COST = 3000L;
    private static final String INCORRECT_LENGTH = "Incorrect input length";

    /**
     * @param input 128 bytes of data input, [32-bytes message, 32-bytes public key, 64-bytes
     *     signature]
     * @return the verification result of the given input (publickey address for pass, all-0's
     *     address for fail)
     */
    @Override
    public PrecompiledTransactionResult execute(byte[] input, long nrgLimit) {

        // check length
        if (input == null || input.length != 128) {
            return new PrecompiledTransactionResult(
                TransactionStatus.nonRevertedFailure("FAILURE"), nrgLimit - COST, INCORRECT_LENGTH.getBytes());
        }

        if (COST > nrgLimit) {
            return new PrecompiledTransactionResult(TransactionStatus.nonRevertedFailure("OUT_OF_NRG"), 0);
        }
        byte[] msg = new byte[32];
        byte[] sig = new byte[64];
        byte[] pubKey = new byte[32];

        System.arraycopy(input, 0, msg, 0, 32);
        System.arraycopy(input, 32, pubKey, 0, 32);
        System.arraycopy(input, 64, sig, 0, 64);

        try {
            boolean verify = ECKeyEd25519.verify(msg, sig, pubKey);
            return new PrecompiledTransactionResult(
                    TransactionStatus.successful(),
                    nrgLimit - COST,
                    verify ? pubKey : AddressUtils.ZERO_ADDRESS.toByteArray());
        } catch (Exception e) {
            return new PrecompiledTransactionResult(TransactionStatus.nonRevertedFailure("FAILURE"), 0);
        }
    }
}
