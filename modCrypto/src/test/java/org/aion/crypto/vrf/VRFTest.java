package org.aion.crypto.vrf;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.junit.Test;

public class VRFTest {
    @Test
    public void testCrypto_vrf_prove_with_ed25519Key() {

        ECKey key = ECKeyFac.inst().create();

        byte[] msg = "testVrf".getBytes();
        byte[] msg2 = "testVrf2".getBytes();

        byte[] proof = VRF_Ed25519.generateProof(msg, key.getPrivKeyBytes());
        byte[] proof2 = VRF_Ed25519.generateProof(msg2, key.getPrivKeyBytes());

        assertEquals(proof.length, VRF_Ed25519.PROOF_BYTES);
        assertEquals(proof2.length, VRF_Ed25519.PROOF_BYTES);

        assertTrue(VRF_Ed25519.verify(msg, proof, key.getPubKey()));
        assertFalse(VRF_Ed25519.verify(msg2, proof, key.getPubKey()));

        assertFalse(VRF_Ed25519.verify(msg, proof2, key.getPubKey()));
        assertTrue(VRF_Ed25519.verify(msg2, proof2, key.getPubKey()));
    }
}
