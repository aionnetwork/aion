package org.aion.os;

public class KernelControlException extends Exception {
    public KernelControlException(String message) {
        super(message);
    }

    public KernelControlException(String message, Throwable cause) {
        super(message, cause);
    }
}
