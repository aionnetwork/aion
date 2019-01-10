package org.aion.mcf.vm;

/**
 * Virtual machine constants.
 *
 * @author yulong
 */
public class Constants {

    public static final int NRG_CODE_DEPOSIT = 1000;

    public static final int NRG_TX_CREATE = 200000;

    public static final int NRG_TX_CREATE_MAX = 5000000;

    public static final int NRG_TX_DATA_ZERO = 4;

    public static final int NRG_TX_DATA_NONZERO = 64;

    public static final int NRG_TRANSACTION = 21000;

    public static final int NRG_TRANSACTION_MAX = 2000000;

    /** Call stack depth limit. Based on EIP-150, the theoretical limit is ~340. */
    public static final int MAX_CALL_DEPTH = 128;
}
