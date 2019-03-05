package org.aion.crypto.ecdsa;

import java.math.BigInteger;
import org.aion.util.conversions.Hex;
import org.junit.Test;

public class ECDSATest {

    @Test
    public void testAddress() {
        ECKeySecp256k1 key =
                new ECKeySecp256k1()
                        .fromPrivate(
                                new BigInteger(
                                        Hex.decode(
                                                "34F9460F0E4F08393D192B3C5133A6BA099AA0AD9FD54EBCCFACDFA239FF49C6")));

        System.out.println(Hex.toHexString(key.getPubKey()));
        System.out.println(Hex.toHexString(key.getPrivKeyBytes()));
    }
}
