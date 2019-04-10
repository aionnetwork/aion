package org.aion.crypto;

import org.aion.crypto.ecdsa.ECKeySecp256k1;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.junit.Test;

/** @author jin */
public class ECKeyBench {

    @Test
    public void bench() {

        final ECKey key1 = new ECKeySecp256k1();
        final ECKey key2 = new ECKeyEd25519();

        final byte[] input = HashUtil.h256("test".getBytes());
        final int COUNT = 1000;

        // warm up
        for (int i = 0; i < COUNT; i++) {
            key1.sign(input);
            key2.sign(input);
        }

        // ECDSA
        long ts = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            key1.sign(input);
        }
        long te = System.nanoTime();
        System.out.println(" ECDSA   sign: " + (te - ts) / COUNT + " ns / call");

        // ED25519
        ts = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            key2.sign(input);
        }
        te = System.nanoTime();
        System.out.println(" Ed25519 sign: " + (te - ts) / COUNT + " ns / call");
    }
}
