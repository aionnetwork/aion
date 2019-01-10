package org.aion.p2p;

import java.util.HashSet;
import java.util.Set;

/** @author chris */
public class Ver {

    public static final short V0 = 0;

    // for test
    public static final short V1 = 1;

    public static final short UNKNOWN = Short.MAX_VALUE;

    private static Set<Short> active =
            new HashSet<>() {
                {
                    this.add(V0);
                    this.add(V1);
                }
            };

    /**
     * @param _version short
     * @return short method provided to filter any decoded version (short)
     */
    public static short filter(short _version) {
        return active.contains(_version) ? _version : UNKNOWN;
    }
}
