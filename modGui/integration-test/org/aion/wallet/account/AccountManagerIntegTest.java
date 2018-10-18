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
package org.aion.wallet.account;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Supplier;
import org.aion.gui.model.BalanceRetriever;
import org.aion.gui.util.AionConstants;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.exception.ValidationException;
import org.aion.wallet.storage.WalletStorage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AccountManagerIntegTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * Import a keystore file (with password 'testpass') and then export it.
     */
    @Test
    public void testImportExport() throws Exception {
        // set up test data
        File tempLocalStorageDir = tempFolder.newFolder("localStorageDir");
        File tempExportDir = tempFolder.newFolder("export");
        String localStorageDir = tempLocalStorageDir.getAbsolutePath();
        String exportDir = tempExportDir.getAbsolutePath();

        System.setProperty("local.storage.dir", localStorageDir);

        final byte[] testKeystoreBytes;
        try (InputStream is = new FileInputStream(
            System.getProperty("user.dir") + "/" + "test-files/test-keystore")) {
            testKeystoreBytes = ByteStreams.toByteArray(is);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            fail();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            fail();
            return;
        }

        // set up classes for test
        BalanceRetriever balanceRetriever = mock(BalanceRetriever.class);
        Supplier currencySupplier = () -> AionConstants.CCY;
        ConsoleManager consoleManager = mock(ConsoleManager.class);
        WalletStorage walletStorage = new WalletStorage(
            localStorageDir,
            Paths.get(localStorageDir, "keystore")
        );

        AccountManager accountManager = new AccountManager(
            balanceRetriever, currencySupplier, consoleManager, walletStorage);
        String testKeystorePassword = "testpass";
        boolean shouldKeep = false;

        // verify import
        AccountDTO account = accountManager
            .importKeystore(testKeystoreBytes, testKeystorePassword, shouldKeep);

        String expectedAddress = "0xa09210aec9c66539097a4ae9c536828eef4c69c4f65a68469326534b3cb10f5e";
        assertThat(account.getPublicAddress(), is(expectedAddress));

        // verify export
        String exportPassword = "all ur base";
        String outFile = accountManager.exportAccount(account, exportPassword, exportDir);

        assertThat(Files.exists(Paths.get(exportDir, outFile)), is(true));

        final byte[] exportedKeystoreBytes;
        try (InputStream is = new FileInputStream(exportDir + File.separator + outFile)) {
            exportedKeystoreBytes = ByteStreams.toByteArray(is);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            fail();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            fail();
            return;
        }

        try {
            accountManager.importKeystore(exportedKeystoreBytes, exportPassword, shouldKeep);
        } catch (ValidationException ve) {
            // importing it should make it AccountManager try to import
            // an account that's already added, so it'll throw
            return;
        }
        fail(
            "Expected ValidationException to be thrown for importing the same account a second time.");
    }
}