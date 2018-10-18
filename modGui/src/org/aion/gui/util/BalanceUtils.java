/*
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
 * Contributors:
 *     Aion foundation.
 */

package org.aion.gui.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * @author Cristian Ilca, Centrys Inc.
 */
public class BalanceUtils {

    public static final String CCY_SEPARATOR = " ";

    private static final int PRECISION = 18;

    private static final BigDecimal WEI_MULTIPLIER = BigDecimal.valueOf(1E18);

    public static String formatBalance(final BigInteger balance) {
        if (BigInteger.ZERO.equals(balance) || balance == null) {
            return String.valueOf(0);
        }
        BigDecimal bigDecimalBalance = new BigDecimal(balance);
        return bigDecimalBalance.divide(WEI_MULTIPLIER, PRECISION, RoundingMode.HALF_EVEN)
            .stripTrailingZeros().toPlainString();
    }

    public static BigInteger extractBalance(final String formattedBalance) {
        return new BigDecimal(formattedBalance).multiply(WEI_MULTIPLIER).toBigInteger();
    }
}
