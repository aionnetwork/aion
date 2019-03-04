package org.aion.log;

/** Logger modules available to classes in the kernel */
public enum LogEnum {
    GEN,
    CONS,
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
    GUI;

    public static boolean contains(String _module) {
        for (LogEnum module : values()) {
            if (module.name().equalsIgnoreCase(_module)) return true;
        }

        return false;
    }
}
