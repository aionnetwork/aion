package org.aion.p2p.impl.comm;

import java.util.HashSet;
import java.util.Set;

/** @author chris */
public final class Act {

    public static final byte DISCONNECT = 0;

    public static final byte REQ_HANDSHAKE = 1;

    public static final byte RES_HANDSHAKE = 2;

    public static final byte PING = 3;

    public static final byte PONG = 4;

    public static final byte REQ_ACTIVE_NODES = 5;

    public static final byte RES_ACTIVE_NODES = 6;

    public static final byte UNKNOWN = Byte.MAX_VALUE;

    private static Set<Byte> active =
            new HashSet<>() {
                {
                    add(REQ_HANDSHAKE);
                    add(RES_HANDSHAKE);
                    add(REQ_ACTIVE_NODES);
                    add(RES_ACTIVE_NODES);
                }
            };

    /**
     * @param _act byte
     * @return byte method provided to filter any decoded p2p action (byte)
     */
    public static byte filter(byte _act) {
        return active.contains(_act) ? _act : UNKNOWN;
    }
}
