package org.aion.precompiled.contracts;

import static junit.framework.TestCase.assertEquals;

import java.nio.charset.StandardCharsets;
import org.aion.vm.FastVmResultCode;
import org.aion.vm.FastVmTransactionResult;
import org.junit.Before;
import org.junit.Test;

public class KeccakHashTest {
    private static final long INPUT_NRG = 1000;

    private byte[] byteArray1 = "a0010101010101010101010101".getBytes();
    private byte[] shortByteArray = "".getBytes();
    private KeccakHash keccakHasher;

    @Before
    public void setUp() {
        keccakHasher = new KeccakHash();
    }

    @Test
    public void testKeccak256() {
        FastVmTransactionResult res = keccakHasher.execute(byteArray1, INPUT_NRG);
        byte[] output = res.getOutput();

        assertEquals(FastVmResultCode.SUCCESS, res.getResultCode());
        assertEquals(32, output.length);

        System.out.println(
                "The keccak256 hash for '"
                        + new String(byteArray1, StandardCharsets.UTF_8)
                        + "' is:");
        System.out.print("      ");
        for (byte b : output) {
            System.out.print(b + " ");
        }
        System.out.println();
    }

    @Test
    public void invalidInputLength() {
        FastVmTransactionResult res2 = keccakHasher.execute(shortByteArray, INPUT_NRG);
        assertEquals(FastVmResultCode.FAILURE, res2.getResultCode());
    }

    @Test
    public void insufficientNRG() {
        FastVmTransactionResult res2 = keccakHasher.execute(byteArray1, 30);
        assertEquals(FastVmResultCode.OUT_OF_NRG, res2.getResultCode());
    }
}
