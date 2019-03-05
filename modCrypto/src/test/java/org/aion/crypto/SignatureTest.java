package org.aion.crypto;

import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.Test;

public class SignatureTest {
    @Test
    public void testSecp256k1Signature() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.SECP256K1);

        ECKey key = ECKeyFac.inst().create();
        System.out.println(key);

        byte[] msg = "test".getBytes();
        byte[] msgHash = HashUtil.h256(msg);

        ISignature sig = key.sign(msgHash);
        assertTrue(SignatureFac.verify(msgHash, sig));
        assertTrue(SignatureFac.verify(msgHash, SignatureFac.fromBytes(sig.toBytes())));
    }

    @Test
    public void testED25519Signature() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);

        ECKey key = ECKeyFac.inst().create();
        System.out.println(key);

        byte[] msg = "test".getBytes();
        byte[] msgHash = HashUtil.h256(msg);

        ISignature sig = key.sign(msgHash);
        assertTrue(SignatureFac.verify(msgHash, sig));
        assertTrue(SignatureFac.verify(msgHash, SignatureFac.fromBytes(sig.toBytes())));
    }

    @AfterClass
    public static void teardown() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
    }
}
