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
        return bigDecimalBalance.divide(WEI_MULTIPLIER, PRECISION, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString();
    }

    public static BigInteger extractBalance(final String formattedBalance) {
        return new BigDecimal(formattedBalance).multiply(WEI_MULTIPLIER).toBigInteger();
    }

    //TODO will be done in future story
    public static String convertBalance() {
        return null;
    }
}
