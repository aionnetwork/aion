package org.aion.mcf.blockchain;

public enum TxResponse {
    SUCCESS(),
    INVALID_TX(),
    INVALID_TX_NRG_PRICE(),
    INVALID_FROM(),
    INVALID_ACCOUNT(),
    ALREADY_CACHED(),
    CACHED_NONCE(),
    CACHED_POOLMAX(),
    REPAID(),
    ALREADY_SEALED(),
    REPAYTX_POOL_EXCEPTION(),
    REPAYTX_LOWPRICE(),
    DROPPED(),
    EXCEPTION(),
}