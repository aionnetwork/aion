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

package org.aion.gui.events;

import java.util.Set;
import org.aion.log.AionLoggerFactory;
import org.aion.wallet.connector.dto.SendTransactionDTO;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.events.AbstractAccountEvent;
import org.aion.wallet.events.AccountEvent;
import org.aion.wallet.events.AccountListEvent;
import org.aion.wallet.events.ErrorEvent;
import org.aion.wallet.events.TransactionEvent;
import org.aion.wallet.events.UiMessageEvent;
import org.slf4j.Logger;

public class EventPublisher {

    private static final Logger LOG = AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());

    public static void fireAccountUnlocked(final AccountDTO account) {
        if (account != null) {
            EventBusRegistry.INSTANCE
                    .getBus(AccountEvent.ID)
                    .post(new AccountEvent(AccountEvent.Type.UNLOCKED, account));
        }
    }

    public static void fireAccountExport(final AccountDTO account) {
        if (account != null) {
            EventBusRegistry.INSTANCE
                    .getBus(AccountEvent.ID)
                    .post(new AccountEvent(AccountEvent.Type.EXPORT, account));
        }
    }

    public void fireUnexpectedApiDisconnection() {
        EventBusRegistry.INSTANCE
                .getBus(EventBusRegistry.KERNEL_BUS)
                .post(new UnexpectedApiDisconnectedEvent());
    }

    public void fireAccountLocked(final AccountDTO account) {
        if (account != null) {
            EventBusRegistry.INSTANCE
                    .getBus(AbstractAccountEvent.ID)
                    .post(new AccountEvent(AbstractAccountEvent.Type.LOCKED, account));
        }
    }

    public static void fireMnemonicCreated(final String mnemonic) {
        if (mnemonic != null) {
            EventBusRegistry.INSTANCE
                    .getBus(UiMessageEvent.ID)
                    .post(new UiMessageEvent(UiMessageEvent.Type.MNEMONIC_CREATED, mnemonic));
        }
    }

    public static void fireFatalErrorEncountered(final String message) {
        EventBusRegistry.INSTANCE
                .getBus(ErrorEvent.ID)
                .post(new ErrorEvent(ErrorEvent.Type.FATAL, message));
    }

    public static void fireTransactionFinished() {
        EventBusRegistry.INSTANCE
                .getBus(RefreshEvent.ID)
                .post(new RefreshEvent(RefreshEvent.Type.TRANSACTION_FINISHED));
    }

    public static void fireTransactionResubmited(final SendTransactionDTO transaction) {
        EventBusRegistry.INSTANCE
                .getBus(TransactionEvent.ID)
                .post(new TransactionEvent(TransactionEvent.Type.RESUBMIT, transaction));
    }

    public static void fireConsoleLogged(final String message) {
        EventBusRegistry.INSTANCE
                .getBus(UiMessageEvent.ID)
                .post(new UiMessageEvent(UiMessageEvent.Type.CONSOLE_LOG, message));
    }

    public void fireDisconnected() {
        EventBusRegistry.INSTANCE
                .getBus(RefreshEvent.ID)
                .post(new RefreshEvent(RefreshEvent.Type.DISCONNECTED));
    }

    public void fireConnectionEstablished() {
        EventBusRegistry.INSTANCE
                .getBus(RefreshEvent.ID)
                .post(new RefreshEvent(RefreshEvent.Type.CONNECTED));
    }

    public void fireAccountChanged(final AccountDTO account) {
        if (account != null) {
            EventBusRegistry.INSTANCE
                    .getBus(AccountEvent.ID)
                    .post(new AccountEvent(AccountEvent.Type.CHANGED, account));
        }
    }

    public void fireAccountAdded(final AccountDTO account) {
        if (account != null) {
            EventBusRegistry.INSTANCE
                    .getBus(AccountEvent.ID)
                    .post(new AccountEvent(AccountEvent.Type.ADDED, account));
        }
    }

    public void fireAccountsRecovered(final Set<String> addresses) {
        if (addresses != null && !addresses.isEmpty()) {
            EventBusRegistry.INSTANCE
                    .getBus(AbstractAccountEvent.ID)
                    .post(new AccountListEvent(AbstractAccountEvent.Type.RECOVERED, addresses));
        }
    }
}
