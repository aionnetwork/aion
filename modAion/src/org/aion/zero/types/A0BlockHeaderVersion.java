package org.aion.zero.types;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class A0BlockHeaderVersion {
    public static byte V1 = 1;

    private static Set<Byte> active =
            new HashSet<>() {
                {
                    this.add(V1);
                }
            };

    public static boolean isActive(byte version) {
        return active.contains(version);
    }

    public static String activeVersions() {
        String toReturn = "{";

        Iterator<Byte> it = active.iterator();
        while (it.hasNext()) {
            toReturn += it.next();
        }

        return toReturn + "}";
    }
}
