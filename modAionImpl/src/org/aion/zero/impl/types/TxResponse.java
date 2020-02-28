package org.aion.zero.impl.types;

public enum TxResponse {
    SUCCESS(0, false),
    INVALID_TX(1, true),
    INVALID_TX_NRG_PRICE(2, true),
    INVALID_FROM(3, true),
    INVALID_ACCOUNT(4, true),
    ALREADY_CACHED(5, false),
    CACHED_NONCE(6, false),
    CACHED_POOLMAX(7, false),
    REPAID(8, false),
    ALREADY_SEALED(9, false),
    REPAYTX_BUFFER_FULL(10, true),
    REPAYTX_LOWPRICE(11, true),
    DROPPED(12, true),
    EXCEPTION(13, true),
    INVALID_TX_NRG_LIMIT(14, true),
    INVALID_TX_NONCE(15, true),
    INVALID_TX_TIMESTAMP(16, true),
    INVALID_TX_VALUE(17, true),
    INVALID_TX_DATA(18, true),
    INVALID_TX_HASH(19, true),
    INVALID_TX_SIGNATURE(20, true),
    INVALID_TX_TYPE(21, true),
    INVALID_TX_BEACONHASH(22, true),
    CACHED_TIMEOUT(23, false),
    TXPOOL_TIMEOUT(24,false),
    CACHED_ACCOUNTMAX(25, false);


    private int val;
    private boolean fail;

    TxResponse(int val, boolean fail) {
        this.val = val;
        this.fail = fail;
    }

    public int getVal() {
        return val;
    }

    public boolean isFail() {
        return fail;
    }

    public boolean isSuccess() {
        return !fail;
    }
}
