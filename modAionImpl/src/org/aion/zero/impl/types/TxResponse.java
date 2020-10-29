package org.aion.zero.impl.types;

public enum TxResponse {
    SUCCESS(0, false, "Transaction sent successfully"),
    INVALID_TX(1, true, "Invalid transaction object"),
    INVALID_TX_NRG_PRICE(2, true, "Invalid transaction energy price"),
    INVALID_FROM(3, true, "Invalid from address provided"),
    INVALID_ACCOUNT(4, true, "Account not found, or not unlocked"),
    ALREADY_CACHED(5, false, "Transaction is already in the cache"),
    CACHED_NONCE(6, false, "Transaction cached due to large nonce"),
    CACHED_POOLMAX(7, false, "Transaction cached because the pool is full"),
    REPAID(8, false, "Transaction successfully repaid"),
    ALREADY_SEALED(9, false, "Transaction has already been sealed in the blockchain DB"),
    REPAYTX_BUFFER_FULL(10, true, "Repaid transaction dropped due to the buffer full"),
    REPAYTX_LOWPRICE(11, true, "Repaid transaction needs to have a higher energy price"),
    DROPPED(12, true, "Transaction dropped"),
    EXCEPTION(13, true, "Exception"),
    INVALID_TX_NRG_LIMIT(14, true, "Invalid transaction energy limit"),
    INVALID_TX_NONCE(15, true, "Invalid transaction nonce"),
    INVALID_TX_TIMESTAMP(16, true, "Invalid transaction timestamp"),
    INVALID_TX_VALUE(17, true, "Invalid transaction value"),
    INVALID_TX_DATA(18, true, "Invalid transaction data"),
    INVALID_TX_HASH(19, true, "Invalid transaction hash"),
    INVALID_TX_SIGNATURE(20, true, "Invalid transaction signature"),
    INVALID_TX_TYPE(21, true, "Invalid transaction type"),
    INVALID_TX_BEACONHASH(22, true, "Invalid transaction beacon hash"),
    CACHED_TIMEOUT(23, false, "Transaction dropped due to the transaction cache timeout"),
    TXPOOL_TIMEOUT(24, false, "Transaction dropped due to the transaction pool timeout"),
    CACHED_ACCOUNTMAX(25, false, "Transaction dropped due to the cache account reach max"),
    INVALID_TX_DESTINATION(26, true, "Transaction destination address is invalid");


    private final int val;
    private final boolean fail;
    private final String message;

    TxResponse(int val, boolean fail, String msg) {
        this.val = val;
        this.fail = fail;
        this.message = msg;
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

    public String getMessage() {
        return message;
    }
}
