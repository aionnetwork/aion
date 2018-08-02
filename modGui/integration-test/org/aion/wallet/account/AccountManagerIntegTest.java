package org.aion.wallet.account;

import com.google.common.io.ByteStreams;
import org.aion.gui.model.BalanceRetriever;
import org.aion.gui.util.AionConstants;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.storage.WalletStorage;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class AccountManagerIntegTest {
    /**
     * Import a keystore file (with password 'testpass') and then export it.
     */
    @Test
    public void testImportExport() throws Exception {
        final byte[] keystoreBytes;
        // XXX fix path behaviour
        try (InputStream is = new FileInputStream("modGui/test-files/test-keystore")) {
            keystoreBytes = ByteStreams.toByteArray(is);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            fail();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            fail();
            return;
        }

        BalanceRetriever balanceRetriever = mock(BalanceRetriever.class);
        Supplier currencySupplier = () -> AionConstants.CCY;
        ConsoleManager consoleManager = mock(ConsoleManager.class);
        WalletStorage walletStorage = new WalletStorage(
                "/tmp/wallet/storage-dir",
                Paths.get("/tmp/wallet/keystore-path")
        );

        AccountManager accountManager = new AccountManager(
                balanceRetriever, currencySupplier, consoleManager, walletStorage);
        String testKeystorePassword = "testpass";
        boolean shouldKeep = false;
        AccountDTO account = accountManager.importKeystore(keystoreBytes, testKeystorePassword, shouldKeep);

        assertThat(account.getPublicAddress(), is("0xa09210aec9c66539097a4ae9c536828eef4c69c4f65a68469326534b3cb10f5e"));

        String exportPassword = "all ur base";
        accountManager.exportAccount(account, exportPassword, "/tmp/test-out/");
    }
}