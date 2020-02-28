package org.aion.txpool;

public class Constant {

    public enum TXPOOL_PROPERTY {
        PROP_TX_TIMEOUT,
        PROP_BLOCK_NRG_LIMIT,
        PROP_POOL_SIZE_MAX
    }

    public final static int MAX_BLK_SIZE = 2 * 1024 * 1024;
    public final static long MIN_ENERGY_CONSUME = 21_000L;
    public final static int TRANSACTION_TIMEOUT_DEFAULT = 3600;
    public final static int TRANSACTION_TIMEOUT_MIN = 10;
    public final static long BLOCK_ENERGY_LIMIT_MIN = 1_000_000L;
    public final static long BLOCK_ENERGY_LIMIT_DEFAULT = 10_000_000L;
    public final static int TXPOOL_SIZE_DEFAULT = 2048;
    public final static int TXPOOL_SIZE_MIN = 1024;
}
