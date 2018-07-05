package org.aion.mcf.config.dynamic2;

public class InFlightConfigChangeNotAllowedException extends InFlightConfigChangeException {
    public InFlightConfigChangeNotAllowedException(String message) {
        super(message);
    }
}
