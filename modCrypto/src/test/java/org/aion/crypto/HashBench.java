package org.aion.crypto;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/** @author jin */
public class HashBench {

    @Test
    public void bench() {

        final byte[] input = HashUtil.h256("test".getBytes());
        final int COUNT = 1000;

        byte[] outputJ = new byte[32];
        byte[] outputN = new byte[32];

        // warm up
        for (int i = 0; i < COUNT; i++) {
            HashUtil.blake256(input);
            HashUtil.blake256Native(input);
            HashUtil.keccak256(input);
        }

        // blake2b
        long ts = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            outputJ = HashUtil.blake256(input);
        }
        long te = System.nanoTime();
        System.out.println(" Blake2b       : " + (te - ts) / COUNT + " ns / call");

        // blake2b native
        ts = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            outputN = HashUtil.blake256Native(input);
        }
        te = System.nanoTime();
        System.out.println(" Blake2b native: " + (te - ts) / COUNT + " ns / call");

        assertArrayEquals(outputJ, outputN);

        // keccak
        ts = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            HashUtil.keccak256(input);
        }
        te = System.nanoTime();
        System.out.println(" Keccak        : " + (te - ts) / COUNT + " ns / call");
    }
}
