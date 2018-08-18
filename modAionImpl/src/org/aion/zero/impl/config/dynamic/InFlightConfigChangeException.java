package org.aion.zero.impl.config.dynamic;

public class InFlightConfigChangeException extends Exception {
    public InFlightConfigChangeException(String message) {
        super(message);
    }
}
