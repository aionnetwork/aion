package org.aion.zero.impl.blockchain;

import org.aion.zero.impl.AionHub;

public class PoolDumpUtils {
    public PoolDumpUtils() {

    }

    public static void dumpPool(AionHub aionHub) {
        aionHub.getPendingState().DumpPool();
    }
}
