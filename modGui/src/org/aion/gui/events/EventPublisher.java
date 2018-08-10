package org.aion.gui.events;

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

import java.util.Set;

public class EventPublisher {

    private static final Logger LOG = AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());

    public static void fireAccountUnlocked(final AccountDTO account) {
        if (account != null) {
            EventBusRegistry.INSTANCE.getBus(AccountEvent.ID).post(new AccountEvent(AccountEvent.Type.UNLOCKED, account));
        }
    }

    public static void fireAccountExport(final AccountDTO account) {
        if (account != null) {
            EventBusRegistry.INSTANCE.getBus(AccountEvent.ID).post(new AccountEvent(AccountEvent.Type.EXPORT, account));
        }
    }

    public void fireAccountLocked(final AccountDTO account) {
        if (account != null) {
            EventBusRegistry.INSTANCE.getBus(AbstractAccountEvent.ID).post(new AccountEvent(AbstractAccountEvent.Type.LOCKED, account));
        }
    }

    public static void fireMnemonicCreated(final String mnemonic) {
        if (mnemonic != null) {
            EventBusRegistry.INSTANCE.getBus(UiMessageEvent.ID)
                    .post(new UiMessageEvent(UiMessageEvent.Type.MNEMONIC_CREATED, mnemonic));
        }
    }

    public static void fireFatalErrorEncountered(final String message) {
        EventBusRegistry.INSTANCE.getBus(ErrorEvent.ID).post(new ErrorEvent(ErrorEvent.Type.FATAL, message));
    }

    public static void fireTransactionFinished() {
        EventBusRegistry.INSTANCE.getBus(RefreshEvent.ID).post(new RefreshEvent(RefreshEvent.Type.TRANSACTION_FINISHED));
    }

    public static void fireTransactionResubmited(final SendTransactionDTO transaction) {
        EventBusRegistry.INSTANCE.getBus(TransactionEvent.ID).post(new TransactionEvent(TransactionEvent.Type.RESUBMIT, transaction));
    }

    public static void fireConsoleLogged(final String message) {
        EventBusRegistry.INSTANCE.getBus(UiMessageEvent.ID)
                .post(new UiMessageEvent(UiMessageEvent.Type.CONSOLE_LOG, message));
    }

    public static void fireUnexpectedApiDisconnection() {
        EventBusRegistry.INSTANCE.getBus(EventBusRegistry.KERNEL_BUS)
                .post(new UnexpectedApiDisconnectedEvent());
    }

    public void fireDisconnected() {
        EventBusRegistry.INSTANCE.getBus(RefreshEvent.ID).post(new RefreshEvent(RefreshEvent.Type.DISCONNECTED));
    }

    public void fireConnectionEstablished() {
        EventBusRegistry.INSTANCE.getBus(RefreshEvent.ID).post(new RefreshEvent(RefreshEvent.Type.CONNECTED));
    }

    public void fireAccountChanged(final AccountDTO account) {
        if (account != null) {
            EventBusRegistry.INSTANCE.getBus(AccountEvent.ID).post(new AccountEvent(AccountEvent.Type.CHANGED, account));
        }
    }

    public void fireAccountAdded(final AccountDTO account) {
        if (account != null) {
            EventBusRegistry.INSTANCE.getBus(AccountEvent.ID).post(new AccountEvent(AccountEvent.Type.ADDED, account));
        }
    }

    public void fireAccountsRecovered(final Set<String> addresses) {
        if (addresses != null && !addresses.isEmpty()) {
            EventBusRegistry.INSTANCE.getBus(AbstractAccountEvent.ID).post(new AccountListEvent(AbstractAccountEvent.Type.RECOVERED, addresses));
        }
    }
}
