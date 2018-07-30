/*
 ******************************************************************************
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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 *****************************************************************************
 */
package org.aion.mcf.account;

import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class AccountManagerTest {
    private ECKey k1;
    private ECKey k2;
    private ECKey k3;

    private final String p1 = "password1";
    private final String p2 = "password2";
    private final String p3 = "password3";

    private AccountManager accountManager = AccountManager.inst();
    private Address addr = Address.wrap("a011111111111111111111111111111101010101010101010101010101010101");

    @Before
    public void setup(){
        k1 = ECKeyFac.inst().create();
        k2 = ECKeyFac.inst().create();
        k3 = ECKeyFac.inst().create();

        Keystore.create(p1, k1);
        Keystore.create(p2, k2);
        Keystore.create(p3, k3);
    }

    @Test
    public void testSingletonAccountManager(){
        // unlock some accounts ----------------------------------------------------------------------------------------
        assertTrue(accountManager.unlockAccount(Address.wrap(k1.getAddress()), p1, 2000));
        assertTrue(accountManager.unlockAccount(Address.wrap(k2.getAddress()), p2, 1));

        // time out more than max
        assertTrue(accountManager.unlockAccount(Address.wrap(k3.getAddress()), p3, 86401));

        // not registered key
        assertFalse(accountManager.unlockAccount(addr, "no pass", 2000));

        // account already present, update the timeout
        assertTrue(accountManager.unlockAccount(Address.wrap(k1.getAddress()), p1, 4000));

        // lock some accounts ------------------------------------------------------------------------------------------
        assertTrue(accountManager.lockAccount(Address.wrap(k2.getAddress()), p2));

        // not registered key
        assertFalse(accountManager.lockAccount(addr, "no pass"));

        // get accounts ------------------------------------------------------------------------------------------------
        assertEquals(3, accountManager.getAccounts().size());

        // get key -----------------------------------------------------------------------------------------------------
        ECKey result = accountManager.getKey(Address.wrap(k1.getAddress()));
        assertArrayEquals(k1.getAddress(), result.getAddress());
        assertArrayEquals(k1.getPubKey(), result.getPubKey());
        assertArrayEquals(k1.getPrivKeyBytes(), result.getPrivKeyBytes());

        // key not exist
        assertNull(accountManager.getKey(addr));

        // past time out, remove k2, check if account map size has decreased
        ECKey res2 = accountManager.getKey(Address.wrap(k2.getAddress()));
        assertNull(res2);
        assertEquals(2, accountManager.getAccounts().size());
    }
}
