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

import java.math.BigInteger;

public class BIUtil {

    /**
     * @param value
     *            - not null
     * @return true - if the param is zero
     */
    public static boolean isZero(BigInteger value) {
        return value.compareTo(BigInteger.ZERO) == 0;
    }

    /**
     * @param valueA
     *            - not null
     * @param valueB
     *            - not null
     * @return true - if the valueA is equal to valueB is zero
     */
    public static boolean isEqual(BigInteger valueA, BigInteger valueB) {
        return valueA.compareTo(valueB) == 0;
    }

    /**
     * @param valueA
     *            - not null
     * @param valueB
     *            - not null
     * @return true - if the valueA is not equal to valueB is zero
     */
    public static boolean isNotEqual(BigInteger valueA, BigInteger valueB) {
        return !isEqual(valueA, valueB);
    }

    /**
     * @param valueA
     *            - not null
     * @param valueB
     *            - not null
     * @return true - if the valueA is less than valueB is zero
     */
    public static boolean isLessThan(BigInteger valueA, BigInteger valueB) {
        return valueA.compareTo(valueB) < 0;
    }

    /**
     * @param valueA
     *            - not null
     * @param valueB
     *            - not null
     * @return true - if the valueA is more than valueB is zero
     */
    public static boolean isMoreThan(BigInteger valueA, BigInteger valueB) {
        return valueA.compareTo(valueB) > 0;
    }

    /**
     * @param valueA
     *            - not null
     * @param valueB
     *            - not null
     * @return sum - valueA + valueB
     */
    public static BigInteger sum(BigInteger valueA, BigInteger valueB) {
        return valueA.add(valueB);
    }

    /**
     * @param data
     *            = not null
     * @return new positive BigInteger
     */
    public static BigInteger toBI(byte[] data) {
        return new BigInteger(1, data);
    }

    /**
     * @param data
     *            = not null
     * @return new positive BigInteger
     */
    public static BigInteger toBI(long data) {
        return BigInteger.valueOf(data);
    }

    public static boolean isPositive(BigInteger value) {
        return value.signum() > 0;
    }

    public static boolean isCovers(BigInteger covers, BigInteger value) {
        return !isNotCovers(covers, value);
    }

    public static boolean isNotCovers(BigInteger covers, BigInteger value) {
        return covers.compareTo(value) < 0;
    }

    public static boolean exitLong(BigInteger value) {

        return (value.compareTo(new BigInteger(Long.MAX_VALUE + ""))) > -1;
    }

    public static boolean isIn20PercentRange(BigInteger first, BigInteger second) {
        BigInteger five = BigInteger.valueOf(5);
        BigInteger limit = first.add(first.divide(five));
        return !isMoreThan(second, limit);
    }

    public static BigInteger max(BigInteger first, BigInteger second) {
        return first.compareTo(second) < 0 ? second : first;
    }

    public static BigInteger min(BigInteger first, BigInteger second) {
        return first.compareTo(second) > 0 ? second : first;
    }
}
