package org.aion.base;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.aion.crypto.HashUtil;
import org.aion.types.Log;
import org.junit.Test;

public class AionTxReceiptTest {

    private byte[] EMPTY_BYTE_ARRAY = new byte[0];

    @Test
    public void testSerialization() {
        AionTxReceipt receipt = new AionTxReceipt();
        receipt.setError("");
        receipt.setExecutionResult(HashUtil.h256(EMPTY_BYTE_ARRAY));

        List<Log> infos = new ArrayList<>();
        receipt.setLogs(infos);
        receipt.setPostTxState(HashUtil.h256(EMPTY_BYTE_ARRAY));

        byte[] encoded = receipt.getEncoded();
        AionTxReceipt resp = new AionTxReceipt(encoded);

        assertArrayEquals(resp.getTransactionOutput(), receipt.getTransactionOutput());
        assertEquals(resp.getBloomFilter(), receipt.getBloomFilter());
        assertEquals(resp.getError(), receipt.getError());
    }
}
