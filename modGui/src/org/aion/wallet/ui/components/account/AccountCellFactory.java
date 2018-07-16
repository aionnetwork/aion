package org.aion.wallet.ui.components.account;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.ui.components.partials.SaveKeystoreDialog;
import org.aion.wallet.ui.components.partials.UnlockAccountDialog;

import java.util.ConcurrentModificationException;

public class AccountCellFactory implements Callback<ListView<AccountDTO>, ListCell<AccountDTO>> {

    private final UnlockAccountDialog accountUnlockDialog;
    private final SaveKeystoreDialog saveKeystoreDialog;

    public AccountCellFactory(UnlockAccountDialog unlockAccountDialog,
                              SaveKeystoreDialog saveKeystoreDialog) {
        this.accountUnlockDialog = unlockAccountDialog;
        this.saveKeystoreDialog = saveKeystoreDialog;
    }

    public AccountCellFactory(AccountManager accountManager) {
        this.accountUnlockDialog = new UnlockAccountDialog(accountManager);
        this.saveKeystoreDialog = new SaveKeystoreDialog(accountManager);
    }

    @Override
    public ListCell<AccountDTO> call(ListView<AccountDTO> param) {
        return new AccountCellItem(accountUnlockDialog, saveKeystoreDialog);
    }
}
