package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.Collections;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.mcf.vm.types.Bloom;
import org.aion.types.AionAddress;
import org.aion.util.types.AddressUtils;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.junit.Test;

/** Test cases for AionTxExecSummary */
public class AionTxExecSummaryTest {

    private AionAddress defaultAddress =
            AddressUtils.wrapAddress(
                    "CAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFE");

    @Test
    public void testRLPEncoding() {
        AionTransaction mockTx =
                AionTransaction.create(
                        ECKeyFac.inst().create(),
                        BigInteger.ONE.toByteArray(),
                        defaultAddress,
                        BigInteger.ONE.toByteArray(),
                        HashUtil.EMPTY_DATA_HASH,
                        1L,
                        1L,
                        TransactionTypes.DEFAULT);

        AionTxReceipt txReceipt =
                new AionTxReceipt(HashUtil.EMPTY_TRIE_HASH, new Bloom(), Collections.EMPTY_LIST);
        txReceipt.setNrgUsed(1);
        txReceipt.setTransaction(mockTx);

        AionTxExecSummary.Builder builder = AionTxExecSummary.builderFor(txReceipt);
        builder.markAsFailed().result(new byte[0]);
        AionTxExecSummary summary = builder.build();
        byte[] encodedSummary = summary.getEncoded();

        AionTxExecSummary newSummary = new AionTxExecSummary(encodedSummary);

        newSummary.getReceipt().setTransaction(mockTx);

        assertThat(newSummary.getFee()).isEqualTo(BigInteger.ONE);
    }
}
