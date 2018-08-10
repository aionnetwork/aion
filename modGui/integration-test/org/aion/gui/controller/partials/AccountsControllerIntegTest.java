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
package org.aion.gui.controller.partials;

import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.aion.gui.controller.ControllerFactory;
import org.aion.gui.controller.DashboardController;
import org.aion.gui.controller.UiSubcomponentsFactory;
import org.aion.gui.events.RefreshEvent;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.storage.WalletStorage;
import org.aion.wallet.ui.components.partials.AddAccountDialog;
import org.aion.wallet.ui.components.partials.ImportAccountDialog;
import org.aion.wallet.ui.components.partials.UnlockMasterAccountDialog;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.matcher.base.NodeMatchers;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

import static javafx.fxml.FXMLLoader.DEFAULT_CHARSET_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testfx.api.FxAssert.verifyThat;

public class AccountsControllerIntegTest extends ApplicationTest {
    private AccountsController controller;
    private Parent accountsPaneView;

    private ControllerFactory cf;
    private AccountManager accountManager;
    private WalletStorage walletStorage;
    private AddAccountDialog addAccountDialog;
    private ImportAccountDialog importAccountDialog;
    private UnlockMasterAccountDialog unlockMasterAccountDialog;
    private ConsoleManager consoleManager;

    @Override
    public void init() {
        accountManager = mock(AccountManager.class);
        walletStorage = mock(WalletStorage.class);
        addAccountDialog = mock(AddAccountDialog.class);
        importAccountDialog = mock(ImportAccountDialog.class);
        unlockMasterAccountDialog = mock(UnlockMasterAccountDialog.class);
        consoleManager  = mock(ConsoleManager.class);
    }

    @Override
    public void start(Stage stage) throws Exception {
        cf = new ControllerFactory() {
            {
                // this initializer overwrites the mapping set in the normal ControllerFactory, so no need to do
                // the .withWalletStorage, etc.
                this.builderChooser.put(AccountsController.class, () -> new AccountsController(
                        accountManager, walletStorage, addAccountDialog, importAccountDialog, unlockMasterAccountDialog, consoleManager));
            }
        };
        FXMLLoader loader = new FXMLLoader(
                DashboardController.class.getResource("components/partials/AccountsPane.fxml"),
                null,
                null,
                cf,
                Charset.forName(DEFAULT_CHARSET_NAME),
                new LinkedList<>());
        loader.setBuilderFactory(new UiSubcomponentsFactory()
                .withAccountManager(accountManager)
                .withConsoleManager(consoleManager)
        );
        accountsPaneView = loader.load();
        controller = loader.getController();

        AnchorPane ap = new AnchorPane(accountsPaneView);
        ap.setPrefWidth(860);
        ap.setPrefHeight(570);

        stage.setScene(new Scene(ap));
        stage.show();
        stage.toFront();

        lookup("#accountsPane").query().setVisible(true);
    }

    @Test
    public void testNoMasterAccountInitialScreen() throws Exception {
        when(walletStorage.hasMasterAccount()).thenReturn(false);

        verifyThat("#addMasterAccountButton", NodeMatchers.isVisible());
        verifyThat("#unlockMasterAccountButton", not(NodeMatchers.isVisible()));
        verifyThat("#importButton", NodeMatchers.isVisible());

        clickOn("#addMasterAccountButton");
        WaitForAsyncUtils.waitForFxEvents();
        verify(addAccountDialog).open(any(MouseEvent.class));
        verify(accountManager, never()).createAccount();

        clickOn("#importButton");
        WaitForAsyncUtils.waitForFxEvents();
        verify(importAccountDialog).open(any(MouseEvent.class));
    }

    @Test
    public void testHasLockedMasterAccountInitialScreen() throws Exception {
        when(walletStorage.hasMasterAccount()).thenReturn(true);
        when(accountManager.isMasterAccountUnlocked()).thenReturn(false);

        // simulate starting up by making controller refresh
        controller.refreshView(new RefreshEvent(RefreshEvent.Type.CONNECTED));
        WaitForAsyncUtils.waitForFxEvents();
        verifyThat("#addMasterAccountButton", not(NodeMatchers.isVisible()));
        verifyThat("#unlockMasterAccountButton", NodeMatchers.isVisible());
        verifyThat("#importButton", NodeMatchers.isVisible());

        clickOn("#unlockMasterAccountButton");
        WaitForAsyncUtils.waitForFxEvents();
        verify(unlockMasterAccountDialog).open(any(MouseEvent.class));
        verify(accountManager, never()).createAccount();

        clickOn("#importButton");
        WaitForAsyncUtils.waitForFxEvents();
        verify(importAccountDialog).open(any(MouseEvent.class));
    }

    @Test
    public void testHasUnlockedMasterAccountInitialScreen() throws Exception {
        when(walletStorage.hasMasterAccount()).thenReturn(true);
        when(accountManager.isMasterAccountUnlocked()).thenReturn(true);

        List<AccountDTO> accounts = new LinkedList<>();
        String testAddress = "some test address";
        AccountDTO account = new AccountDTO(
                "anyName", testAddress, "anyBalance", "anyCurrency", false, 0
        );
        AccountDTO otherAccount = new AccountDTO(
                "otherName", "otherAddr", "otherBalance", "otherCurrency", false, 1
        );
        accounts.add(account);
        accounts.add(otherAccount);
        when(accountManager.getAccounts()).thenReturn(accounts);

        // simulate starting up by making controller refresh
        controller.refreshView(new RefreshEvent(RefreshEvent.Type.CONNECTED));
        WaitForAsyncUtils.waitForFxEvents();
        verifyThat("#addMasterAccountButton", NodeMatchers.isVisible());
        verifyThat("#unlockMasterAccountButton", not(NodeMatchers.isVisible()));
        verifyThat("#importButton", NodeMatchers.isVisible());

        clickOn("#addMasterAccountButton");
        WaitForAsyncUtils.waitForFxEvents();
        verify(accountManager).createAccount();
        verify(consoleManager).addLog(anyString(), any(ConsoleManager.LogType.class));
        verifyZeroInteractions(addAccountDialog);

        clickOn("#importButton");
        WaitForAsyncUtils.waitForFxEvents();
        verify(importAccountDialog).open(any(MouseEvent.class));

        ObservableList<AccountDTO> listItems = ((ListView<AccountDTO>)lookup("#accountListView").query()).getItems();
        assertThat(listItems.size(), is(2));
        assertThat(listItems.get(0), is(account));
        assertThat(listItems.get(1), is(otherAccount));
    }
}