package org.aion.wallet.events;

public class AbstractEvent<T extends Enum> {

    private final T eventType;

    protected AbstractEvent(final T eventType) {
        this.eventType = eventType;
    }

    public T getType() {
        return eventType;
    }
}
