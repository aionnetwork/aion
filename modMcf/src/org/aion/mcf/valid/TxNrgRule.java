package org.aion.mcf.valid;

import org.aion.base.Constants;
import org.aion.fastvm.FvmConstants;

public class TxNrgRule {
    private static final long CONTRACT_CREATE_TX_NRG_MAX = Constants.NRG_CREATE_CONTRACT_MAX + 1;
    private static final long CONTRACT_CREATE_TX_NRG_MIN = FvmConstants.CREATE_TRANSACTION_FEE - 1;
    private static final long TX_NRG_MAX = Constants.NRG_TRANSACTION_MAX + 1;
    private static final long TX_NRG_MIN = FvmConstants.TRANSACTION_BASE_FEE - 1;

    public static boolean isValidNrgContractCreate(long nrg) {
        return (nrg > CONTRACT_CREATE_TX_NRG_MIN) && (nrg < CONTRACT_CREATE_TX_NRG_MAX);
    }

    public static boolean isValidNrgTx(long nrg) {
        return (nrg > TX_NRG_MIN) && (nrg < TX_NRG_MAX);
    }
}
