package org.aion.gui.model;

import com.google.common.eventbus.Subscribe;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.EventPublisher;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.connector.dto.BlockDTO;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.events.AbstractAccountEvent;
import org.aion.wallet.events.AccountEvent;
import org.aion.wallet.events.AccountListEvent;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

public class AccountChangeHandlers {
    private final AccountManager accountManager;
    private final BlockTransactionProcessor blockTransactionProcessor;

    public AccountChangeHandlers(AccountManager accountManager,
                                 BlockTransactionProcessor blockTransactionProcessor) {
        this.accountManager = accountManager;
        this.blockTransactionProcessor = blockTransactionProcessor;
        EventBusRegistry.INSTANCE.getBus(AbstractAccountEvent.ID).register(this);
    }

    // originally from ApiBlockchainConnector.java
    @Subscribe
    private void handleAccountEvent(final AccountEvent event) {
        System.out.println("AccountChangeHandlers#handleAccountEvent");
        final AccountDTO account = event.getPayload();
        if (AbstractAccountEvent.Type.CHANGED.equals(event.getType())) {
            System.out.println("AccountChangeHandlers#handleAccountEvent A");
            accountManager.updateAccount(account);
        } else if (AbstractAccountEvent.Type.ADDED.equals(event.getType())) {
            System.out.println("AccountChangeHandlers#handleAccountEvent B");
//            backgroundExecutor.submit(() -> processTransactionsFromBlock(null, Collections.singleton(account.getPublicAddress())));
            blockTransactionProcessor.processTxnsFromBlockAsync(null, Collections.singleton(account.getPublicAddress()));
        }
    }

    // originally from ApiBlockchainConnector.java
    @Subscribe
    private void handleAccountListEvent(final AccountListEvent event) {
        System.out.println("AccountChangeHandlers#handleAccountListEvent");

        if (AbstractAccountEvent.Type.RECOVERED.equals(event.getType())) {
            System.out.println("AccountChangeHandlers#handleAccountListEvent A");
            final Set<String> addresses = event.getPayload();
            final BlockDTO oldestSafeBlock = accountManager.getOldestSafeBlock(addresses, i -> {});
//            backgroundExecutor.submit(() -> processTransactionsFromBlock(oldestSafeBlock, addresses));
            blockTransactionProcessor.processTxnsFromBlockAsync(oldestSafeBlock, addresses);
            final Iterator<String> addressesIterator = addresses.iterator();
            AccountDTO account = accountManager.getAccount(addressesIterator.next());
            account.setActive(true);
            EventPublisher.fireAccountChanged(account);
        }
    }
}
