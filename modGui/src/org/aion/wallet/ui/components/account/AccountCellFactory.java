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
package org.aion.wallet.ui.components.account;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.ui.components.partials.SaveKeystoreDialog;
import org.aion.wallet.ui.components.partials.UnlockAccountDialog;

public class AccountCellFactory implements Callback<ListView<AccountDTO>, ListCell<AccountDTO>> {
    private final UnlockAccountDialog accountUnlockDialog;
    private final SaveKeystoreDialog saveKeystoreDialog;

    public AccountCellFactory(
            UnlockAccountDialog unlockAccountDialog, SaveKeystoreDialog saveKeystoreDialog) {
        this.accountUnlockDialog = unlockAccountDialog;
        this.saveKeystoreDialog = saveKeystoreDialog;
    }

    public AccountCellFactory(AccountManager accountManager, ConsoleManager consoleManager) {
        this.accountUnlockDialog = new UnlockAccountDialog(accountManager, consoleManager);
        this.saveKeystoreDialog = new SaveKeystoreDialog(accountManager, consoleManager);
    }

    @Override
    public ListCell<AccountDTO> call(ListView<AccountDTO> param) {
        return new AccountCellItem(accountUnlockDialog, saveKeystoreDialog);
    }
}
