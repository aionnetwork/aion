package org.aion.log;

/** Logger modules available to classes in the kernel */
public enum LogEnum {
    GEN,
    CONS,
    CACHE,
    SYNC,
    API,
    VM,
    NET,
    DB,
    EVTMGR,
    TXPOOL,
    TX,
    P2P,
    ROOT,
    SURVEY,
    GUI;

    public static boolean contains(String _module) {
        for (LogEnum module : values()) {
            if (module.name().equalsIgnoreCase(_module)) return true;
        }

        return false;
    }
}
