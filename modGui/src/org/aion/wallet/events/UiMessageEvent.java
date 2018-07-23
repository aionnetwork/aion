package org.aion.wallet.events;

public class UiMessageEvent extends AbstractEvent<UiMessageEvent.Type> {

    public static final String ID = "ui.message";

    private final String message;

    public UiMessageEvent(final Type eventType, final String message) {
        super(eventType);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public enum Type {
        MNEMONIC_CREATED,
        CONSOLE_LOG
    }
}
