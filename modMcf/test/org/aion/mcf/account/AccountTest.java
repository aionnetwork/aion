package org.aion.mcf.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.junit.Before;
import org.junit.Test;

public class AccountTest {
    private ECKey key;
    private long timeout;
    private Account testAccount;

    @Before
    public void setup() {
        key = ECKeyFac.inst().create();
        timeout = 1000L;
        testAccount = new Account(key, timeout);
    }

    @Test
    public void testTimeout() {
        // get time out
        assertEquals(1000L, testAccount.getTimeout());

        testAccount.updateTimeout(2000L);

        // get time out after update
        assertEquals(2000L, testAccount.getTimeout());
    }

    @Test
    public void testKey() {
        assertEquals(key, testAccount.getKey());
    }

    @Test
    public void testNullAttributes() {
        long time = 1000;
        Account tester = new Account(null, time);

        // get key when its null
        assertNull(tester.getKey());
    }
}
