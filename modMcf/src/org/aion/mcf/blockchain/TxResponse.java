package org.aion.mcf.blockchain;

public enum TxResponse {
    SUCCESS(0),
    INVALID_TX(1),
    INVALID_TX_NRG_PRICE(2),
    INVALID_FROM(3),
    INVALID_ACCOUNT(4),
    ALREADY_CACHED(5),
    CACHED_NONCE(6),
    CACHED_POOLMAX(7),
    REPAID(8),
    ALREADY_SEALED(9),
    REPAYTX_POOL_EXCEPTION(10),
    REPAYTX_LOWPRICE(11),
    DROPPED(12),
    EXCEPTION(13);

    private int val;

    TxResponse(int val) {
        this.val = val;
    }

    public int getVal() {
        return val;
    }
}