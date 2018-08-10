package org.aion.gui.events;

import org.aion.wallet.events.AbstractEvent;

public class RefreshEvent extends AbstractEvent<RefreshEvent.Type> {

    public static final String ID = "ui.data_refresh";

    public RefreshEvent(final Type eventType) {
        super(eventType);
    }

    public enum Type {
        CONNECTED,
        DISCONNECTED,
        TRANSACTION_FINISHED,
        TIMER
    }
}
