package org.aion.wallet.events;

public abstract class AbstractAccountEvent<T> extends AbstractEvent<AbstractAccountEvent.Type> {

    public static final String ID = "account.update";

    private final T payload;

    protected AbstractAccountEvent(final Type eventType, final T payload) {
        super(eventType);
        this.payload = payload;
    }

    public final T getPayload() {
        return payload;
    }

    public enum Type {
        CHANGED, UNLOCKED, ADDED, LOCKED, EXPORT, RECOVERED
    }
}
