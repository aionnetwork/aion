package org.aion.crypto;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertArrayEquals;

import org.aion.util.bytes.ByteUtil;
import org.junit.AfterClass;
import org.junit.Test;

public class ECKeyTest {

    @Test
    public void testSecp256k1() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.SECP256K1);

        ECKey key = ECKeyFac.inst().create();
        ECKey key2 = ECKeyFac.inst().fromPrivate(key.getPrivKeyBytes());

        System.out.println(key);
        System.out.println(key2);

        assertArrayEquals(key.getAddress(), key2.getAddress());
        assertArrayEquals(key.getPrivKeyBytes(), key2.getPrivKeyBytes());
    }

    @Test
    public void testED25519() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);

        ECKey key = ECKeyFac.inst().create();
        ECKey key2 = ECKeyFac.inst().fromPrivate(key.getPrivKeyBytes());

        System.out.println(key);
        System.out.println(key2);

        assertArrayEquals(key.getAddress(), key2.getAddress());
        assertArrayEquals(key.getPrivKeyBytes(), key2.getPrivKeyBytes());
    }

    @Test
    public void testED25519Address() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);

        ECKey key = ECKeyFac.inst().create();
        assertThat(key.getPubKey()).isNotEqualTo(key.getAddress());

        byte[] address = key.getAddress();
        // check header for address
        String addressStr = ByteUtil.toHexString(address);

        // check length
        assertThat(address.length).isEqualTo(32);

        // check that the header matches
        assertThat(addressStr.substring(0, 2).toLowerCase()).isEqualTo("a0");

        // check that the remainder matches a hashed pubKey
        String hashedPkString = ByteUtil.toHexString(HashUtil.h256(key.getPubKey()));
        assertThat(hashedPkString.substring(2)).isEqualTo(addressStr.substring(2));
    }

    @AfterClass
    public static void teardown() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
    }
}
