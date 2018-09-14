package org.libsodium.jni.crypto;

import org.junit.Test;
import org.libsodium.jni.Sodium;
import org.libsodium.jni.encoders.Hex;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.libsodium.jni.SodiumConstants.PUBLICKEY_BYTES;
import static org.libsodium.jni.SodiumConstants.SESSIONKEYBYTES;

public class KeyExchangeTest {

    @Test
    public void testClientServerKeyExchange() {
        byte[] server_pk = new byte[PUBLICKEY_BYTES];
        byte[] server_sk = new byte[SESSIONKEYBYTES];
        byte[] client_pk = new byte[PUBLICKEY_BYTES];
        byte[] client_sk = new byte[SESSIONKEYBYTES];

        assertEquals(Sodium.crypto_kx_keypair(server_pk, server_sk), 0);
        assertEquals(Sodium.crypto_kx_keypair(client_pk, client_sk), 0);

        byte[] server_rx = new byte[SESSIONKEYBYTES];
        byte[] server_tx = new byte[SESSIONKEYBYTES];
        byte[] client_rx = new byte[SESSIONKEYBYTES];
        byte[] client_tx = new byte[SESSIONKEYBYTES];

        assertEquals(Sodium.crypto_kx_server_session_keys(server_rx, server_tx, server_pk, server_sk, client_pk), 0);
        assertEquals(Sodium.crypto_kx_client_session_keys(client_rx, client_tx, client_pk, client_sk, server_pk), 0);

        assertArrayEquals(server_rx, client_tx);
        assertArrayEquals(server_tx, client_rx);
    }

    @Test
    public void testServerSessionKeyExchange() {
        Hex hex = new Hex();
        byte[] rx = new byte[SESSIONKEYBYTES];
        byte[] tx = new byte[SESSIONKEYBYTES];

        byte[] server_pk = hex.decode("f61788dd49d78f061a48adf45128be1693f6099c52d3cb9ae69f87b7ba11620c");
        byte[] server_sk = hex.decode("a7a91bbe84f15da821a5421d2a96c93c575138dee2bbaca11a818e5aeed72a49");
        byte[] client_pk = hex.decode("dc2efbd4fcdc2c4f8e8ae87ae4806d1f96b1d27e10cf1f44b2d8992c65cac41b");

        assertEquals(Sodium.crypto_kx_server_session_keys(rx, tx, server_pk, server_sk, client_pk), 0);
        assertEquals(hex.encode(rx), "09bae6d4fc5b2dbc48558c8c8a4e67dcf6611561aaea6a16897bcb8aa23d4fa1");
        assertEquals(hex.encode(tx), "79b5fe6746853894f60d76e40487072c2af8ef01c1b34d606e804b5d3dab1de9");
    }
}
