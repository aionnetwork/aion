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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.matchers.Null;

import static org.junit.Assert.*;

public class KeystoreTest {

    private static String randomPassword() {
        Random rand = new Random();
        StringBuilder sb = new StringBuilder(10);
        while (sb.length() < 10) {
            char c = (char) (rand.nextInt() & Character.MAX_VALUE);
            if (Character.isDefined(c)) sb.append(c);
        }
        return sb.toString();
    }

    @Before
    public void init() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
    }

    @Test
    public void keyCreateAndRetrieve() {

        String password = randomPassword();
        String address = Keystore.create(password);
        assertNotNull(address);
        assertEquals(address.length(), 2 + 64);
        System.out.println("new addr: " + address);
        ECKey key = Keystore.getKey(address, password);
        assertNotNull(key);
    }

    @Test
    public void keyCreateAndRetrieve2() {
        String password = randomPassword();
        String address = Keystore.create(password);
        assertNotNull(address);
        assertEquals(address.length(), 2 + 64);
        System.out.println("new addr: " + address);
        ECKey key = Keystore.getKey(address, password);
        assertNotNull(key);
        assertEquals("0x", (Keystore.create(password, key)));
    }

    @Test
    public void testKeyCreate() {
        String password = randomPassword();

        ECKey key = ECKeyFac.inst().create();
        assertNotNull(key);

        String addr = Keystore.create(password, key);
        assertEquals(addr.substring(2), ByteUtil.toHexString(key.getAddress()));
    }

    @Test
    public void testKeyExist() {
        String password = randomPassword();
        String address = Keystore.create(password);
        assertNotNull(address);
        assertEquals(address.length(), 2 + 64);
        System.out.println("new addr: " + address);
        ECKey key = Keystore.getKey(address, password);
        assertNotNull(key);
        assertTrue(Keystore.exist(address));
    }

    @Test
    public void testWrongAddress() {
        String wAddr = "0xb000000000000000000000000000000000000000000000000000000000000000";
        assertFalse(Keystore.exist(wAddr));

        String wAddr1 = "0x0000000000000000000000000000000000000000000000000000000000000000";
        assertFalse(Keystore.exist(wAddr1));
    }

    @Test
    public void testAccountExport() {
        String password = randomPassword();
        ECKey key = ECKeyFac.inst().create();
        assertNotNull(key);

        String addr = Keystore.create(password, key);
        assertEquals(addr.substring(2), ByteUtil.toHexString(key.getAddress()));

        Map<Address, String> arg = new HashMap<>();
        arg.put(Address.wrap(addr), password);

        Map<Address, ByteArrayWrapper> export = Keystore.exportAccount(arg);

        assertTrue(export.containsKey(Address.wrap(addr)));
        assertTrue(export.containsValue(ByteArrayWrapper.wrap(key.getPrivKeyBytes())));
    }

    @Test
    public void testAccountBackup() {
        String password = randomPassword();
        ECKey key = ECKeyFac.inst().create();
        assertNotNull(key);

        String addr = Keystore.create(password, key);
        assertEquals(addr.substring(2), ByteUtil.toHexString(key.getAddress()));

        Map<Address, String> arg = new HashMap<>();
        arg.put(Address.wrap(addr), password);

        Map<Address, ByteArrayWrapper> export = Keystore.backupAccount(arg);

        assertNotNull(export);

        File f = Keystore.getAccountFile(addr.substring(2), password);
        assertNotNull(f);

        assertTrue(export.containsKey(Address.wrap(addr)));
        try {
            assertTrue(export.containsValue(ByteArrayWrapper.wrap(Files.readAllBytes(f.toPath()))));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testList() {
        String password = randomPassword();
        ECKey key = ECKeyFac.inst().create();
        assertNotNull(key);

        String addr = Keystore.create(password, key);
        assertEquals(addr.substring(2), ByteUtil.toHexString(key.getAddress()));

        String[] addrList = Keystore.list();

        assertNotNull(addrList);

        boolean hasAddr = false;
        for (String s : addrList) {
            if (s.equals(addr)) {
                hasAddr = true;
                break;
            }
        }

        assertTrue(hasAddr);
    }

    @Test (expected = NullPointerException.class)
    public void testBackupAccountWithNullInput(){
        Keystore.backupAccount(null);
    }

    @Test (expected = NullPointerException.class)
    public void testImportAccountNull(){
        Keystore.importAccount(null);
    }

    @Test
    public void testImportAccount(){
        Map<String, String> importKey = new HashMap<>();
        ECKey key = ECKeyFac.inst().create();

        importKey.put(key.toString(), key.toString());
        Set<String> res = Keystore.importAccount(importKey);
    }

    @Test
    public void testAccountSorted(){
        List<String> res = Keystore.accountsSorted();
        for(String addr: res)
            System.out.println(addr);
    }

    @Test
    public void testKeystoreItem(){
        KeystoreItem keystoreItem = new KeystoreItem();
        keystoreItem.setId("test-id");
        keystoreItem.setVersion(5);
        keystoreItem.setAddress("fake-address");

        // test get id
        assertEquals("test-id", keystoreItem.getId());
        assertEquals(Integer.valueOf(5), keystoreItem.getVersion());
        assertEquals("fake-address", keystoreItem.getAddress());
    }
}
