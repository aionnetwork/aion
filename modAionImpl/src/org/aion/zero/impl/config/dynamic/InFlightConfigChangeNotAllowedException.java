package org.aion.zero.impl.config.dynamic;

public class InFlightConfigChangeNotAllowedException extends InFlightConfigChangeException {
    public InFlightConfigChangeNotAllowedException(String message) {
        super(message);
    }
}
