package org.aion.mcf.blockchain;

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
    REPAYTX_POOL_EXCEPTION(10, true),
    REPAYTX_LOWPRICE(11, true),
    DROPPED(12, true),
    EXCEPTION(13, true);

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
}
