package org.aion.zero.impl.valid;

import static org.aion.util.types.AddressUtils.ZERO_ADDRESS;
import static org.junit.Assert.assertEquals;

import org.aion.base.AionTransaction;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.precompiled.ContractInfo;
import org.aion.types.AionAddress;
import org.aion.zero.impl.types.TxResponse;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

public class TXValidatorTest {
    private ECKey key = ECKeyFac.inst().create();
    boolean unityForkEnabled = true;

    byte[] nonce = RandomUtils.nextBytes(16);
    byte[] value = RandomUtils.nextBytes(16);
    byte[] data = new byte[0];
    long nrg = 21000;
    long nrgPrice = 10_000_000_000L;
    byte type = 0;

    @Test
    public void validateTxSignatureSwapForkDisabledReceiverAddressTest() {

        boolean signatureSwapEnabled = false;
        byte[] dest = RandomUtils.nextBytes(32);
        dest[0] = (byte) 0xa1;

        AionTransaction tx =
            AionTransaction.create(
                key,
                nonce,
                new AionAddress(dest),
                value,
                data,
                nrg,
                nrgPrice,
                type, null);

        TxResponse response = TXValidator.validateTx(tx, unityForkEnabled, signatureSwapEnabled);

        assertEquals(TxResponse.SUCCESS, response);
    }

    @Test
    public void validateTxSignatureSwapForkEnabledReceiverAddressTest() {

        boolean signatureSwapEnabled = true;
        byte[] dest = RandomUtils.nextBytes(32);
        dest[0] = (byte) 0xa1;
        AionTransaction tx =
            AionTransaction.create(
                key,
                nonce,
                new AionAddress(dest),
                value,
                data,
                nrg,
                nrgPrice,
                type, null);

        TxResponse response = TXValidator.validateTx(tx, unityForkEnabled, signatureSwapEnabled);

        assertEquals(TxResponse.INVALID_TX_DESTINATION, response);
    }

    @Test
    public void validateTxSignatureSwapForkEnabledReceiverAddressIsPreCompiledTest() {

        boolean signatureSwapEnabled = true;
        for (ContractInfo contractInfo : ContractInfo.values()) {
            AionTransaction tx =
                AionTransaction.create(
                    key,
                    nonce,
                    contractInfo.contractAddress,
                    value,
                    data,
                    nrg,
                    nrgPrice,
                    type, null);

            TxResponse response = TXValidator.validateTx(tx, unityForkEnabled, signatureSwapEnabled);

            // Note: TOTAL CURRENCY contract has been disabled.
            if (contractInfo.contractAddress.equals(ContractInfo.TOTAL_CURRENCY.contractAddress)) {
                assertEquals(TxResponse.INVALID_TX_DESTINATION, response);
            } else {
                assertEquals(TxResponse.SUCCESS, response);
            }
        }
    }

    @Test
    public void validateTxSignatureSwapForkEnabledReceiverAddressIsZeroTest() {

        boolean signatureSwapEnabled = true;
        AionTransaction tx =
            AionTransaction.create(
                key,
                nonce,
                ZERO_ADDRESS,
                value,
                data,
                nrg,
                nrgPrice,
                type, null);

        TxResponse response = TXValidator.validateTx(tx, unityForkEnabled, signatureSwapEnabled);
        assertEquals(TxResponse.SUCCESS, response);
    }
}
