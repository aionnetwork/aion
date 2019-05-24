package org.aion.vm;

/**
 * A long-lived Aion Virtual Machine.
 *
 * There is nothing special about this class that enforces the longevity of the Avm instance.
 * However, this is the singleton access-point that any thread using the Avm will come through.
 *
 * This instance should be created once, by a single thread, before any other thread can possibly
 * access this class.
 *
 * This instance should be shutdown using {@code destroy()} by only a single thread, once any other
 * threads are no longer using this class.
 */
public final class LongLivedAvm {
    private static AionVirtualMachine aionVirtualMachine;

    /**
     * This method should be called once, when the kernel starts up.
     *
     * This method is not thread-safe. Only a single thread should ever call this method, and it
     * should call it before any other thread can call {@code singleton()}.
     */
    public static void createAndStartLongLivedAvm() {
        if (aionVirtualMachine != null) {
            throw new IllegalStateException("AVM has already been initialized!");
        }
        aionVirtualMachine = AionVirtualMachine.createAndInitializeNewAvm();
    }

    /**
     * Returns the singleton instance of the long-lived AVM instance.
     *
     * @return The long-lived AVM.
     */
    public static AionVirtualMachine singleton() {
        return aionVirtualMachine;
    }

    /**
     * Shuts down and clears the current long-lived avm instance.
     */
    public static void destroy() {
        if (aionVirtualMachine != null) {
            aionVirtualMachine.acquireAvmLock();
            aionVirtualMachine.shutdown();
            aionVirtualMachine.releaseAvmLock();
            aionVirtualMachine = null;
        }
    }
}
