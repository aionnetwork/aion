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
package org.aion.gui.model;

import com.google.common.eventbus.Subscribe;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.EventPublisher;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.connector.dto.BlockDTO;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.events.AbstractAccountEvent;
import org.aion.wallet.events.AccountEvent;
import org.aion.wallet.events.AccountListEvent;

/**
 * Event handlers for account change related events. Not great to have a class that's just disparate
 * event handlers, but it contains some logic for coordinating AccountManager and
 * TransactionProcessor that isn't convenient to refactor at the moment. Think about how to organize
 * this when we refactor event management.
 *
 * <p>This class should thought of as part of Controller layer.
 */
public class AccountChangeHandlers {
    private final AccountManager accountManager;
    private final TransactionProcessor transactionProcessor;

    public AccountChangeHandlers(
            AccountManager accountManager, TransactionProcessor transactionProcessor) {
        this.accountManager = accountManager;
        this.transactionProcessor = transactionProcessor;
        EventBusRegistry.INSTANCE.getBus(AbstractAccountEvent.ID).register(this);
    }

    @Subscribe
    private void handleAccountEvent(final AccountEvent event) {
        final AccountDTO account = event.getPayload();
        if (AbstractAccountEvent.Type.CHANGED.equals(event.getType())) {
            accountManager.updateAccount(account);
        } else if (AbstractAccountEvent.Type.ADDED.equals(event.getType())) {
            transactionProcessor.processTxnsFromBlockAsync(
                    null, Collections.singleton(account.getPublicAddress()));
        }
    }

    @Subscribe
    private void handleAccountListEvent(final AccountListEvent event) {
        if (AbstractAccountEvent.Type.RECOVERED.equals(event.getType())) {
            final Set<String> addresses = event.getPayload();
            final BlockDTO oldestSafeBlock = accountManager.getOldestSafeBlock(addresses, i -> {});
            transactionProcessor.processTxnsFromBlockAsync(oldestSafeBlock, addresses);
            final Iterator<String> addressesIterator = addresses.iterator();
            AccountDTO account = accountManager.getAccount(addressesIterator.next());
            account.setActive(true);
            new EventPublisher().fireAccountChanged(account);
        }
    }
}
