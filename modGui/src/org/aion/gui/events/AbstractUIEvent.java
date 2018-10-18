package org.aion.gui.events;

public class AbstractUIEvent<T extends Enum> {

    private final T eventType;

    protected AbstractUIEvent(T eventType) {
        this.eventType = eventType;
    }

    public T getType() {
        return eventType;
    }
}
