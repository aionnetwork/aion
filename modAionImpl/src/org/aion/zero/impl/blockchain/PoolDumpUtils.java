package org.aion.zero.impl.blockchain;

import org.aion.zero.impl.IAion0Hub;

public class PoolDumpUtils {
    public PoolDumpUtils() {

    }

    public static void dumpPool(IAion0Hub aionHub) {
        aionHub.getPendingState().DumpPool();
    }
}
