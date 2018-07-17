package org.aion.wallet.events;

public class ErrorEvent extends AbstractEvent<ErrorEvent.Type> {

    public static final String ID = "app.error";

    private final String message;

    public ErrorEvent(final Type eventType, final String message) {
        super(eventType);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public enum Type {
        FATAL
    }
}
