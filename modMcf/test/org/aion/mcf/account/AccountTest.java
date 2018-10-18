/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

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
