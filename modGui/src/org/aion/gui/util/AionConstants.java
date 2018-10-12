package org.aion.gui.util;

import java.math.BigInteger;

public class AionConstants {

    public static final String AION_URL = "http://mainnet.aion.network";
    public final static String CCY = "AION";
    public static final String DEFAULT_NRG = "22000";
    public static final int BLOCK_MINING_TIME_SECONDS = 10;
    public static final Long BLOCK_MINING_TIME_MILLIS = BLOCK_MINING_TIME_SECONDS * 1000L;
    public static final int VALIDATION_BLOCKS_FOR_TRANSACTIONS = 50;
    private static final long AMP = (long) 1E9;
    public static final BigInteger DEFAULT_NRG_PRICE = BigInteger.valueOf(10 * AMP);

    private AionConstants() {
    }
}
