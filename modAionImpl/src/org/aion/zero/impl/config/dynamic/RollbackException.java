package org.aion.zero.impl.config.dynamic;

import java.util.LinkedList;
import java.util.List;

public class RollbackException extends Exception {
    private final List<Throwable> reasons;

    public RollbackException(String message, List<? extends Throwable> throwables) {
        super(message);
        reasons = new LinkedList<>(throwables);
    }
}
