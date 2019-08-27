package org.aion.zero.impl.blockchain;

public class PoolDumpUtils {
    public PoolDumpUtils() {}

    public static void dumpPool(AionHub aionHub) {
        aionHub.getPendingState().DumpPool();
    }
}
