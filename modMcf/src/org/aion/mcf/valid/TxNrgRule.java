package org.aion.mcf.valid;

import org.aion.mcf.vm.Constants;

public class TxNrgRule {
    private static final long CONTRACT_CREATE_TX_NRG_MAX = Constants.NRG_TX_CREATE_MAX + 1;
    private static final long CONTRACT_CREATE_TX_NRG_MIN = Constants.NRG_TX_CREATE - 1;
    private static final long TX_NRG_MAX = Constants.NRG_TRANSACTION_MAX + 1;
    private static final long TX_NRG_MIN = Constants.NRG_TRANSACTION - 1;

    public static boolean isValidNrgContractCreate(long nrg) {
        return (nrg > CONTRACT_CREATE_TX_NRG_MIN) && (nrg < CONTRACT_CREATE_TX_NRG_MAX) ;
    }

    public static boolean isValidNrgTx(long nrg) {
        return (nrg > TX_NRG_MIN) && (nrg < TX_NRG_MAX);
    }
}
