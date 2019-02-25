package org.aion.p2p;

import java.util.HashSet;
import java.util.Set;

/** @author chris */
public class Ctrl {

    public static final byte NET = 0;

    public static final byte SYNC = 1;

    public static final byte UNKNOWN = Byte.MAX_VALUE;

    private static Set<Byte> active =
            new HashSet<>() {
                {
                    add(NET);
                    add(SYNC);
                }
            };

    /**
     * @param _ctrl byte
     * @return byte method provided to filter any decoded ctrl (byte)
     */
    public static byte filter(byte _ctrl) {
        return active.contains(_ctrl) ? _ctrl : UNKNOWN;
    }
}
