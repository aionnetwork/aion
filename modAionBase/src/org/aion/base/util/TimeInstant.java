/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
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
