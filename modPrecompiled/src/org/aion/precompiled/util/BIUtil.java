package org.aion.precompiled.util;

import java.math.BigInteger;

public class BIUtil {

    /**
     * @param data = not null
     * @return new positive BigInteger
     */
    public static BigInteger toBI(byte[] data) {
        return new BigInteger(1, data);
    }

    public static BigInteger max(BigInteger first, BigInteger second) {
        return first.compareTo(second) < 0 ? second : first;
    }

    public static BigInteger min(BigInteger first, BigInteger second) {
        return first.compareTo(second) > 0 ? second : first;
    }
}
