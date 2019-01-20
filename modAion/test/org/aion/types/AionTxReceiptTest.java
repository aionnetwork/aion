package org.aion.types;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.aion.crypto.HashUtil;
import org.aion.mcf.vm.types.Log;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.zero.types.AionTxReceipt;
import org.junit.Test;

public class AionTxReceiptTest {

    private byte[] EMPTY_BYTE_ARRAY = new byte[0];

    @Test
    public void testSerialization() {
        AionTxReceipt receipt = new AionTxReceipt();
        receipt.setError("");
        receipt.setExecutionResult(HashUtil.h256(EMPTY_BYTE_ARRAY));

        List<IExecutionLog> infos = new ArrayList<>();
        receipt.setLogs(infos);
        receipt.setPostTxState(HashUtil.h256(EMPTY_BYTE_ARRAY));

        byte[] encoded = receipt.getEncoded();
        AionTxReceipt resp = new AionTxReceipt(encoded);

        assertThat(resp.getTransactionOutput(), is(equalTo(receipt.getTransactionOutput())));
        assertThat(resp.getBloomFilter(), is(equalTo(receipt.getBloomFilter())));
        assertThat(resp.getError(), is(equalTo(receipt.getError())));
    }
}
