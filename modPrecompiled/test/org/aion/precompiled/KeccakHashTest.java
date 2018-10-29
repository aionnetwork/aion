package org.aion.precompiled;

import static junit.framework.TestCase.assertEquals;

import java.nio.charset.StandardCharsets;
import org.aion.precompiled.contracts.KeccakHash;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionResult;
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
        ExecutionResult res = keccakHasher.execute(byteArray1, INPUT_NRG);
        byte[] output = res.getOutput();

        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(32, output.length);

        System.out.println("The keccak256 hash for '" + new String(byteArray1,
            StandardCharsets.UTF_8) + "' is:");
        System.out.print("      ");
        for (byte b : output) {
            System.out.print(b + " ");
        }
        System.out.println();
    }

    @Test
    public void invalidInputLength() {
        ExecutionResult res2 = keccakHasher.execute(shortByteArray, INPUT_NRG);
        assertEquals(ResultCode.INTERNAL_ERROR, res2.getResultCode());
    }

    @Test
    public void insufficientNRG() {
        ExecutionResult res2 = keccakHasher.execute(byteArray1, 30);
        assertEquals(ResultCode.OUT_OF_NRG, res2.getResultCode());
    }
}
