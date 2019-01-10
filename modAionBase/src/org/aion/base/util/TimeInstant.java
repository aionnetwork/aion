package org.aion.base.util;

import java.time.Instant;

public final class TimeInstant {
    private static Instant instant;

    public static final TimeInstant EPOCH = new TimeInstant();

    public static TimeInstant now() {
        TimeInstant.instant = Instant.now();
        return EPOCH;
    }

    public long toEpochSec() {
        return instant.getEpochSecond();
    }

    //    public long toEpochNano() {
    //        long seconds = instant.getEpochSecond();
    //        int ns = instant.getNano();
    //
    //        if (seconds < 0 && ns > 0) {
    //            long nanos = Math.multiplyExact(seconds + 1, 1000_000_000);
    //            long adjustment = ns - 1000_000_000;
    //            return Math.addExact(nanos, adjustment);
    //        } else {
    //            long nanos = Math.multiplyExact(seconds, 1000_000_000);
    //            return Math.addExact(nanos, ns);
    //        }
    //    }

    public long toEpochMicro() {
        long seconds = instant.getEpochSecond();
        int nanos = instant.getNano();

        if (seconds < 0 && nanos > 0) {
            long micros = Math.multiplyExact(seconds + 1, 1000_000);
            long adjustment = nanos / 1000 - 1000_000;
            return Math.addExact(micros, adjustment);
        } else {
            long micros = Math.multiplyExact(seconds, 1000_000);
            return Math.addExact(micros, nanos / 1000);
        }
    }

    //    public long toEpochMilli() {
    //        long seconds = instant.getEpochSecond();
    //        int nanos = instant.getNano();
    //
    //        if (seconds < 0 && nanos > 0) {
    //            long milli = Math.multiplyExact(seconds + 1, 1000);
    //            long adjustment = nanos / 1000_000 - 1000;
    //            return Math.addExact(milli, adjustment);
    //        } else {
    //            long milli = Math.multiplyExact(seconds, 1000);
    //            return Math.addExact(milli, nanos / 1000_000);
    //        }
    //    }
}
