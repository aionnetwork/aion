package org.aion.mcf.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Random;

import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.junit.Before;
import org.junit.Test;

public class KeystoreTest {

    public static String randomPassword(int length) {
        Random rand = new Random();
        StringBuffer sb = new StringBuffer(length);
        while (sb.length() < length) {
            char c = (char) (rand.nextInt() & Character.MAX_VALUE);
            if (Character.isDefined(c)) 
                sb.append(c);
        }
        return sb.toString();
    }

    @Before
    public void init() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
    }

    @Test
    public void keyCreateAndRetrieve() {

        String password = randomPassword(10);
        String address = Keystore.create(password);
        assertNotNull(address);
        assertEquals(address.length(), 2 + 64);
        System.out.println("new addr: " + address);
        ECKey key = Keystore.getKey(address, password);
        assertNotNull(key);

    }
}
