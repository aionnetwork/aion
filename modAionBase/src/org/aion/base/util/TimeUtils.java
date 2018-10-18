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

public class TimeUtils {

    /**
     * Converts minutes to millis
     *
     * @param minutes
     *            time in minutes
     * @return corresponding millis value
     */
//    public static long minutesToMillis(long minutes) {
//        return minutes * 60 * 1000;
//    }

    /**
     * Converts seconds to millis
     *
     * @param seconds
     *            time in seconds
     * @return corresponding millis value
     */
    public static long secondsToMillis(long seconds) {
        return seconds * 1000;
    }

    /**
     * Converts millis to minutes
     *
     * @param millis
     *            time in millis
     * @return time in minutes
     */
//    public static long millisToMinutes(long millis) {
//        return Math.round(millis / 60.0 / 1000.0);
//    }

    /**
     * Converts millis to seconds
     *
     * @param millis
     *            time in millis
     * @return time in seconds
     */
//    public static long millisToSeconds(long millis) {
//        return Math.round(millis / 1000.0);
//    }

    /**
     * Returns timestamp in the future after some millis passed from now
     *
     * @param millis
     *            millis count
     * @return future timestamp
     */
//    public static long timeAfterMillis(long millis) {
//        return System.currentTimeMillis() + millis;
//    }

}
