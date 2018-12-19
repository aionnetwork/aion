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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aion.base.type.AionAddress;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.vm.api.interfaces.Address;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class AccountManagerTest {
    private static AccountManager accountManager = AccountManager.inst();
    private Address notRegistered =
            AionAddress.wrap("a011111111111111111111111111111101010101010101010101010101010101");
    private final int DEFAULT_TEST_TIMEOUT = 10;

    private static ECKey k1;
    private static ECKey k2;
    private static ECKey k3;

    private static final String p1 = "password1";
    private static final String p2 = "password2";
    private static final String p3 = "password3";

    private static String address1;
    private static String address2;
    private static String address3;

    private static final String KEYSTORE_PATH;

    static {
        String storageDir = System.getProperty("local.storage.dir");
        if (storageDir == null || storageDir.equalsIgnoreCase("")) {
            storageDir = System.getProperty("user.dir");
        }
        KEYSTORE_PATH = storageDir + "/keystore";
    }

    @BeforeClass
    public static void setupClass() {
        k1 = ECKeyFac.inst().create();
        k2 = ECKeyFac.inst().create();
        k3 = ECKeyFac.inst().create();

        // record the addresses to be used later when removing files from the system
        address1 = Keystore.create(p1, k1).substring(2);
        address2 = Keystore.create(p2, k2).substring(2);
        address3 = Keystore.create(p3, k3).substring(2);
    }

    @AfterClass
    public static void cleanClass() {
        // remove the files created
        cleanFiles();
    }

    @After
    public void cleanManager() {
        // empty the map in account manager for each test
        cleanAccountManager();
    }

    @Test
    public void testUnlockAccount() {
        // unlock 2 accounts
        assertTrue(
                accountManager.unlockAccount(
                        AionAddress.wrap(k1.getAddress()), p1, DEFAULT_TEST_TIMEOUT));
        long timeOutTotal1 = Instant.now().getEpochSecond() + DEFAULT_TEST_TIMEOUT;
        assertTrue(
                accountManager.unlockAccount(
                        AionAddress.wrap(k2.getAddress()), p2, DEFAULT_TEST_TIMEOUT));
        long timeOutTotal2 = Instant.now().getEpochSecond() + DEFAULT_TEST_TIMEOUT;

        // check account manager
        List<Account> list = accountManager.getAccounts();

        int returnedTimeout1 = (int) list.get(0).getTimeout();
        int returnedTimeout2 = (int) list.get(1).getTimeout();
        byte[] returnedAddress1 = list.get(0).getKey().getAddress();
        byte[] returnedAddress2 = list.get(1).getKey().getAddress();

        // since the returned list is not ordered, have to check for all possible orders
        assertTrue(
                Arrays.equals(returnedAddress1, k1.getAddress())
                        || Arrays.equals(returnedAddress1, k2.getAddress()));
        assertTrue(
                Arrays.equals(returnedAddress2, k1.getAddress())
                        || Arrays.equals(returnedAddress2, k2.getAddress()));

        // same with the timeout, since there could be a slight(1s) difference between each unlock
        // as well
        assertTrue(returnedTimeout1 == timeOutTotal1 || returnedTimeout1 == timeOutTotal2);
        assertTrue(returnedTimeout2 == timeOutTotal1 || returnedTimeout2 == timeOutTotal2);
    }

    @Test
    public void testUnlockAccountUpdateTimeout() {
        // update the timeout from 1s to 2s
        assertTrue(
                accountManager.unlockAccount(
                        AionAddress.wrap(k1.getAddress()), p1, DEFAULT_TEST_TIMEOUT));
        assertTrue(accountManager.unlockAccount(AionAddress.wrap(k1.getAddress()), p1, 20));

        // check that the timeout is updated
        assertThat(accountManager.getAccounts().get(0).getTimeout())
                .isEqualTo(Instant.now().getEpochSecond() + 20);
        assertThat(accountManager.getAccounts().size()).isEqualTo(1);
    }

    @Test
    public void testUnlockAccountWithNotRegisteredKey() {
        assertFalse(
                accountManager.unlockAccount(notRegistered, "no password", DEFAULT_TEST_TIMEOUT));

        // check that no account has been put into the manager
        assertThat(accountManager.getAccounts().size()).isEqualTo(0);
    }

    @Test
    public void testUnlockAccountWithWrongPassword() {
        assertFalse(
                accountManager.unlockAccount(
                        AionAddress.wrap(k1.getAddress()), "not p1", DEFAULT_TEST_TIMEOUT));

        // check that no account has been put into the manager
        assertThat(accountManager.getAccounts().size()).isEqualTo(0);
    }

    @Test
    public void testUnlockAccountTimeoutGreaterThanMax() {
        // unlock account with timeout greater than max
        assertTrue(
                accountManager.unlockAccount(
                        AionAddress.wrap(k1.getAddress()), p1, AccountManager.UNLOCK_MAX + 10));

        // check that the recoded timeout is no bigger than max
        assertThat(accountManager.getAccounts().get(0).getTimeout())
                .isEqualTo(Instant.now().getEpochSecond() + AccountManager.UNLOCK_MAX);

        // now update the timeout back to a small value so it can be cleared easily during @After
        assertTrue(
                accountManager.unlockAccount(
                        AionAddress.wrap(k1.getAddress()), p1, DEFAULT_TEST_TIMEOUT));
    }

    @Test
    public void testUnlockAccountWithNegativeTimeout() {
        // try to unlock account with a negative integer as the timeout
        assertTrue(accountManager.unlockAccount(AionAddress.wrap(k1.getAddress()), p1, -1));
        int expectedTimeout = (int) Instant.now().getEpochSecond() + AccountManager.UNLOCK_DEFAULT;

        // check that the account is created and added to the manager
        assertThat(accountManager.getAccounts().size()).isEqualTo(1);
        assertThat(accountManager.getAccounts().get(0).getKey().toString())
                .isEqualTo(k1.toString());

        // however the timeout is changed to the default timeout in account manager
        assertThat(accountManager.getAccounts().get(0).getTimeout()).isEqualTo(expectedTimeout);
    }

    @Test
    public void testLockAccount() {
        // first unlock an account
        assertTrue(
                accountManager.unlockAccount(
                        AionAddress.wrap(k1.getAddress()), p1, DEFAULT_TEST_TIMEOUT));

        // now try to lock it, the timeout will change
        assertTrue(accountManager.lockAccount(AionAddress.wrap(k1.getAddress()), p1));

        // check that the account is now locked
        List<Account> accountList = accountManager.getAccounts();
        assertThat(accountList.size()).isEqualTo(1);
        assertThat(accountList.get(0).getTimeout()).isLessThan(Instant.now().getEpochSecond());
        assertArrayEquals(accountList.get(0).getKey().getAddress(), k1.getAddress());
    }

    @Test
    public void testLockAccountNotInManager() {
        // first unlock an account
        assertTrue(
                accountManager.unlockAccount(
                        AionAddress.wrap(k1.getAddress()), p1, DEFAULT_TEST_TIMEOUT));

        // try to lock a different account
        assertTrue(accountManager.lockAccount(AionAddress.wrap(k2.getAddress()), p2));

        // check that there is still only the first account in the manager
        assertThat(accountManager.getAccounts().size()).isEqualTo(1);
    }

    @Test
    public void testLockAccountWithNotRegisteredKey() {
        assertFalse(accountManager.lockAccount(notRegistered, "no password"));

        // check that no account has been put into the manager
        assertThat(accountManager.getAccounts().size()).isEqualTo(0);
    }

    @Test
    public void testLockAccountWithWrongPassword() {
        // first unlock an account
        assertTrue(
                accountManager.unlockAccount(
                        AionAddress.wrap(k1.getAddress()), p1, DEFAULT_TEST_TIMEOUT + 1));

        // check if its there
        assertThat(accountManager.getAccounts().size()).isEqualTo(1);

        // try to lock with wrong password
        assertFalse(accountManager.lockAccount(AionAddress.wrap(k1.getAddress()), "not p1"));
    }

    @Test
    public void testGetKeyReturned() {
        // first unlock an account
        assertTrue(
                accountManager.unlockAccount(
                        AionAddress.wrap(k1.getAddress()), p1, DEFAULT_TEST_TIMEOUT));

        // retrieve the key
        ECKey ret = accountManager.getKey(AionAddress.wrap(k1.getAddress()));

        // check equality
        assertArrayEquals(ret.getAddress(), k1.getAddress());
    }

    @Test
    public void testGetKeyRemoved() {
        // first unlock an account
        assertTrue(
                accountManager.unlockAccount(
                        AionAddress.wrap(k1.getAddress()), p1, DEFAULT_TEST_TIMEOUT));

        // lock the account
        assertTrue(accountManager.lockAccount(AionAddress.wrap(k1.getAddress()), p1));

        // retrieve key, but instead it is removed
        assertNull(accountManager.getKey(AionAddress.wrap(k1.getAddress())));

        // check that it was removed
        assertThat(accountManager.getAccounts().size()).isEqualTo(0);
    }

    @Test
    public void testGetKeyNotInMap() {
        // check that there are currently no accounts in the manager
        assertThat(accountManager.getAccounts().size()).isEqualTo(0);

        // try to get a key not in the manager
        assertNull(accountManager.getKey(AionAddress.wrap(k1.getAddress())));
    }

    @Test
    public void testUnlockAndLockMultipleTimes() {
        // first an account
        assertTrue(
                accountManager.unlockAccount(
                        AionAddress.wrap(k1.getAddress()), p1, AccountManager.UNLOCK_DEFAULT));
        assertThat(accountManager.getAccounts().size()).isEqualTo(1);

        // lock k1 and check that timeout is changed
        assertTrue(accountManager.lockAccount(AionAddress.wrap(k1.getAddress()), p1));
        List<Account> accountsList;
        accountsList = accountManager.getAccounts();
        assertThat(accountsList.size()).isEqualTo(1);
        assertThat(accountsList.get(0).getTimeout()).isLessThan(Instant.now().getEpochSecond());

        // now unlock account with k1 again and check that timeout is changed
        assertTrue(
                accountManager.unlockAccount(
                        AionAddress.wrap(k1.getAddress()), p1, AccountManager.UNLOCK_DEFAULT));
        assertThat(accountManager.getAccounts().size()).isEqualTo(1);
        assertThat(accountsList.get(0).getTimeout())
                .isEqualTo(Instant.now().getEpochSecond() + AccountManager.UNLOCK_DEFAULT);
    }

    private static void cleanAccountManager() {
        // lock all the accounts, which modifies the timeout
        accountManager.lockAccount(AionAddress.wrap(k1.getAddress()), p1);
        accountManager.lockAccount(AionAddress.wrap(k2.getAddress()), p2);
        accountManager.lockAccount(AionAddress.wrap(k3.getAddress()), p3);

        // remove accounts
        accountManager.getKey(AionAddress.wrap(k1.getAddress()));
        accountManager.getKey(AionAddress.wrap(k2.getAddress()));
        accountManager.getKey(AionAddress.wrap(k3.getAddress()));

        // check that manager is cleared
        assertThat(accountManager.getAccounts().size()).isEqualTo(0);
    }

    private static void cleanFiles() {
        // get a list of all the files in keystore directory
        File folder = new File(KEYSTORE_PATH);
        File[] AllFilesInDirectory = folder.listFiles();
        List<String> allFileNames = new ArrayList<>();
        List<String> filesToBeDeleted = new ArrayList<>();

        // check for invalid or wrong path - should not happen
        if (AllFilesInDirectory == null) return;

        for (File file : AllFilesInDirectory) {
            allFileNames.add(file.getName());
        }

        // get a list of the files needed to be deleted, check the ending of file names with
        // corresponding addresses
        for (String name : allFileNames) {
            String ending = "";
            if (name.length() > 64) {
                ending = name.substring(name.length() - 64);
            }

            if (ending.equals(address1) || ending.equals(address2) || ending.equals(address3)) {
                filesToBeDeleted.add(KEYSTORE_PATH + "/" + name);
            }
        }

        // iterate and delete those files
        for (String name : filesToBeDeleted) {
            File file = new File(name);
            file.delete();
        }
    }
}
