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

import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.junit.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class AccountManagerTest {
    private AccountManager accountManager = AccountManager.inst();
    private Address address = Address.wrap("a011111111111111111111111111111101010101010101010101010101010101");

    private ECKey k1;
    private ECKey k2;
    private ECKey k3;

    private final String p1 = "password1";
    private final String p2 = "password2";
    private final String p3 = "password3";

    private String address1;
    private String address2;
    private String address3;

    private static final String KEYSTORE_PATH;

    static {
        String storageDir = System.getProperty("local.storage.dir");
        if (storageDir == null || storageDir.equalsIgnoreCase("")) {
            storageDir = System.getProperty("user.dir");
        }
        KEYSTORE_PATH = storageDir + "/keystore";
    }

    @Before
    public void setup(){
        k1 = ECKeyFac.inst().create();
        k2 = ECKeyFac.inst().create();
        k3 = ECKeyFac.inst().create();

        // for later on removing the keystore files generated
        address1 = Keystore.create(p1, k1).substring(2);
        address2 = Keystore.create(p2, k2).substring(2);
        address3 = Keystore.create(p3, k3).substring(2);
    }

    // remove all the keystore files created
    @After
    public void clean(){
        // empty the map in account manager for each test
        // have to do this because AccountManager is a singleton class
        cleanAccountManager();

        // get a list of all the files in keystore directory
        File folder = new File(KEYSTORE_PATH);
        File[] AllFilesInDirectory = folder.listFiles();
        List<String> allFileNames = new ArrayList<>();
        List<String> filesToBeDeleted = new ArrayList<>();

        // check for invalid or wrong path - should not happen
        if(AllFilesInDirectory == null)
            return;

        for(File file: AllFilesInDirectory){
            allFileNames.add(file.getName());
        }

        // get a list of the files needed to be deleted, check the ending of file names with corresponding addresses
        for(String name: allFileNames){
            String ending = name.substring(name.length()-64);

            if(ending.equals(address1) || ending.equals(address2) || ending.equals(address3)) {
                filesToBeDeleted.add(KEYSTORE_PATH + "/"+ name);
            }
        }

        // iterate and delete those files
        for (String name: filesToBeDeleted){
            File file = new File(name);
            if (file.delete())
                System.out.println("Deleted file: " + name);
        }
    }


    @Test
    public void testSingletonAccountManager(){
//        // first check that there are no accounts
//        assertEquals(0, accountManager.getAccounts().size());
//
//        // unlock some accounts ----------------------------------------------------------------------------------------
//        assertTrue(accountManager.unlockAccount(Address.wrap(k1.getAddress()), p1, 2000));
//        assertTrue(accountManager.unlockAccount(Address.wrap(k2.getAddress()), p2, 1));
//
//        // time out more than max
//        assertTrue(accountManager.unlockAccount(Address.wrap(k3.getAddress()), p3, 86401));
//
//        // not registered key
//        assertFalse(accountManager.unlockAccount(address, "no pass", 2000));
//
//        // account already present, update the timeout
//        assertTrue(accountManager.unlockAccount(Address.wrap(k1.getAddress()), p1, 4000));
//
//        // lock some accounts ------------------------------------------------------------------------------------------
//        assertTrue(accountManager.lockAccount(Address.wrap(k2.getAddress()), p2));
//
//        // not registered key
//        assertFalse(accountManager.lockAccount(address, "no pass"));
//
//        // get accounts ------------------------------------------------------------------------------------------------
//        assertEquals(3, accountManager.getAccounts().size());
//
//        // get key -----------------------------------------------------------------------------------------------------
//        ECKey result = accountManager.getKey(Address.wrap(k1.getAddress()));
//        assertArrayEquals(k1.getAddress(), result.getAddress());
//        assertArrayEquals(k1.getPubKey(), result.getPubKey());
//        assertArrayEquals(k1.getPrivKeyBytes(), result.getPrivKeyBytes());
//
//        // key not exist
//        assertNull(accountManager.getKey(address));
//
//        // past time out, remove k2, check if account map size has decreased
//        ECKey res2 = accountManager.getKey(Address.wrap(k2.getAddress()));
//        assertNull(res2);
//        assertEquals(2, accountManager.getAccounts().size());
    }



    private void cleanAccountManager(){
        // wait for the accounts to timeout
        try {
            Thread.sleep(2000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // remove accounts
        accountManager.getKey(Address.wrap(k1.getAddress()));
        accountManager.getKey(Address.wrap(k2.getAddress()));
        accountManager.getKey(Address.wrap(k3.getAddress()));
    }
}
