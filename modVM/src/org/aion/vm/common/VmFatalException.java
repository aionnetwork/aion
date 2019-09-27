package org.aion.vm.common;

/**
 * Signals that a virtual machine encountered a fatal error and the kernel should be shut down.
 */
public final class VmFatalException extends Exception {

    public VmFatalException(Throwable cause) {
        super(cause);
    }

    public VmFatalException(String result) {
        super(result);
    }
}
