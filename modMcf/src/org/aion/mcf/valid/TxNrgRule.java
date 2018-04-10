package org.aion.mcf.valid;

public class TxNrgRule {
    private static final long CONTRACT_CREATE_TX_NRG_MAX = 5_000_001;
    private static final long TX_NRG_MAX = 2_000_001;
    private static final long TX_NRG_MIN = 20_999;

    public static boolean isValidNrgContractCreate(long nrg) {
        return (nrg > TX_NRG_MIN) && (nrg < CONTRACT_CREATE_TX_NRG_MAX) ;
    }

    public static boolean isValidNrgTx(long nrg) {
        return (nrg > TX_NRG_MIN) && (nrg < TX_NRG_MAX);
    }
}
