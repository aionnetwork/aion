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

    private static final String KEYSTORE_PATH;

    static {
        String storageDir = System.getProperty("local.storage.dir");
        if (storageDir == null || storageDir.equalsIgnoreCase("")) {
            storageDir = System.getProperty("user.dir");
        }
        KEYSTORE_PATH = storageDir + "/keystore";
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
        cleanFiles(address);
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
        cleanFiles(address);
    }

    @Test
    public void testKeyCreate() {
        String password = randomPassword();

        ECKey key = ECKeyFac.inst().create();
        assertNotNull(key);

        String addr = Keystore.create(password, key);
        assertEquals(addr.substring(2), ByteUtil.toHexString(key.getAddress()));
        cleanFiles(addr);
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
        cleanFiles(address);
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
        cleanFiles(addr);
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
        cleanFiles(addr);
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
        cleanFiles(addr);
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
    public void testAccountSorted(){
        String addr1 = Keystore.create("p1", ECKeyFac.inst().create());
        String addr2 = Keystore.create("p2", ECKeyFac.inst().create());

        List<String> res = Keystore.accountsSorted();

        assertEquals(2, res.size());
        assertEquals(addr1, res.get(0));
        assertEquals(addr2, res.get(1));

        cleanFiles(addr1);
        cleanFiles(addr2);
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

    private static void cleanFiles(String address){
        // get a list of all the files in keystore directory
        File folder = new File(KEYSTORE_PATH);
        File[] AllFilesInDirectory = folder.listFiles();

        // check for invalid or wrong path - should not happen
        if(AllFilesInDirectory == null)
            return;


        for (File file: AllFilesInDirectory){
            String ending = file.getName().substring(file.getName().length()-64);
            if(ending.equals(address.substring(2))){
                File f = new File(KEYSTORE_PATH + "/"+ file.getName());
                f.delete();
            }
        }
    }
}
