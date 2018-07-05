package org.aion.mcf.config.dynamic2;

import java.util.LinkedList;
import java.util.List;

public class RollbackException extends InFlightConfigChangeException {
    private final List<Throwable> reasons;

    public RollbackException(String message, List<? extends Throwable> throwables) {
        super(message);
        reasons = new LinkedList<>(throwables);
    }
}
