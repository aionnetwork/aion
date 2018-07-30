package org.aion.gui.events;

public class HeaderPaneButtonEvent extends AbstractUIEvent<HeaderPaneButtonEvent.Type> {

    public static final String ID = "gui.header_button";

    public HeaderPaneButtonEvent(final Type eventType) {
        super(eventType);
    }

    public enum Type {
        OVERVIEW, SEND, RECEIVE, HISTORY, CONTRACTS, SETTINGS
    }
}
