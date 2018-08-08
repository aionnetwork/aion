package org.aion.gui.controller.partials;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.Subscribe;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;
import org.aion.gui.controller.AbstractController;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.HeaderPaneButtonEvent;
import org.aion.gui.events.RefreshEvent;
import org.aion.log.AionLoggerFactory;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.events.AccountEvent;
import org.aion.wallet.exception.ValidationException;
import org.aion.wallet.storage.WalletStorage;
import org.aion.wallet.ui.components.partials.AddAccountDialog;
import org.aion.wallet.ui.components.partials.ImportAccountDialog;
import org.aion.wallet.ui.components.partials.UnlockMasterAccountDialog;
import org.slf4j.Logger;

import java.net.URL;
import java.util.EnumSet;
import java.util.List;
import java.util.ResourceBundle;

public class AccountsController extends AbstractController {
    private final AccountManager accountManager;
    private final WalletStorage walletStorage;
    private final AddAccountDialog addAccountDialog;
    private final ImportAccountDialog importAccountDialog;
    private final UnlockMasterAccountDialog unlockMasterAccountDialog;
    private final ConsoleManager consoleManager;

    @FXML
    private Button addMasterAccountButton;
    @FXML
    private Button unlockMasterAccountButton;
    @FXML
    private ListView<AccountDTO> accountListView;

    private AccountDTO account;

    private static final Logger LOG = AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());

    public AccountsController(AccountManager accountManager,
                              WalletStorage walletStorage,
                              ConsoleManager consoleManager) {
        this(accountManager, walletStorage,
                new AddAccountDialog(accountManager, consoleManager),
                new ImportAccountDialog(accountManager, consoleManager),
                new UnlockMasterAccountDialog(accountManager, consoleManager),
                consoleManager);
    }

    @VisibleForTesting
    AccountsController(AccountManager accountManager,
                       WalletStorage walletStorage,
                       AddAccountDialog addAccountDialog,
                       ImportAccountDialog importAccountDialog,
                       UnlockMasterAccountDialog unlockMasterAccountDialog,
                       ConsoleManager consoleManager) {
        this.accountManager = accountManager;
        this.walletStorage = walletStorage;
        this.addAccountDialog = addAccountDialog;
        this.importAccountDialog = importAccountDialog;
        this.unlockMasterAccountDialog = unlockMasterAccountDialog;
        this.consoleManager = consoleManager;
    }

    @Override
    public void internalInit(final URL location, final ResourceBundle resources) {
        reloadAccounts();
    }

    @Override
    protected void registerEventBusConsumer() {
        super.registerEventBusConsumer();
        EventBusRegistry.INSTANCE.getBus(HeaderPaneButtonEvent.ID).register(this);
        EventBusRegistry.INSTANCE.getBus(AccountEvent.ID).register(this);
    }

    private void displayFooterActions() {
        if (walletStorage.hasMasterAccount() && !accountManager.isMasterAccountUnlocked()) {
            unlockMasterAccountButton.setVisible(true);
            addMasterAccountButton.setVisible(false);
        } else {
            unlockMasterAccountButton.setVisible(false);
            addMasterAccountButton.setVisible(true);
        }
    }

    private void reloadAccounts() {
        final Task<List<AccountDTO>> getAccountsTask = getApiTask(o -> accountManager.getAccounts(), null);
        runApiTask(
                getAccountsTask,
                evt -> reloadAccountObservableList(getAccountsTask.getValue()),
                getErrorEvent(t -> {
                }, getAccountsTask),
                getEmptyEvent()
        );
        displayFooterActions();
    }

    private void reloadAccountObservableList(List<AccountDTO> accounts) {
        for (AccountDTO account : accounts) {
            account.setActive(this.account != null && this.account.equals(account));
        }
        accountListView.setItems(FXCollections.observableArrayList(accounts));
    }

    @Subscribe
    private void handleAccountEvent(final AccountEvent event) {
        final AccountDTO account = event.getPayload();
        if (EnumSet.of(AccountEvent.Type.CHANGED, AccountEvent.Type.ADDED).contains(event.getType())) {
            if (account.isActive()) {
                this.account = account;
            }
            reloadAccounts();
        } else if (AccountEvent.Type.LOCKED.equals(event.getType())) {
            if (account.equals(this.account)) {
                this.account = null;
            }
            reloadAccounts();
        }
    }

    @Override
    protected void refreshView(final RefreshEvent event) {
        switch (event.getType()) {
            case CONNECTED:
            case TRANSACTION_FINISHED:
                reloadAccounts();
        }
    }

    @Subscribe
    private void handleHeaderPaneButtonEvent(final HeaderPaneButtonEvent event) {
        if (event.getType().equals(HeaderPaneButtonEvent.Type.ACCOUNTS)) {
            reloadAccounts();
        }
    }

    public void unlockMasterAccount(MouseEvent mouseEvent) {
        unlockMasterAccountDialog.open(mouseEvent);
    }

    public void openImportAccountDialog(MouseEvent mouseEvent) {
        importAccountDialog.open(mouseEvent);
    }

    public void openAddAccountDialog(MouseEvent mouseEvent) {
        if (this.walletStorage.hasMasterAccount()) {
            try {
                accountManager.createAccount();
                consoleManager.addLog("New address created", ConsoleManager.LogType.ACCOUNT);
            } catch (ValidationException e) {
                consoleManager.addLog("Address cannot be created", ConsoleManager.LogType.ACCOUNT, ConsoleManager.LogLevel.WARNING);
                LOG.error(e.getMessage(), e);
                // todo: display on yui
            }
            return;
        }
        addAccountDialog.open(mouseEvent);
    }


}
