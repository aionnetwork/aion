package org.aion.zero.impl.vm.common;

import org.aion.base.Constants;
import org.aion.fastvm.FvmConstants;

public class TxNrgRule {
    private static final long CONTRACT_CREATE_TX_NRG_MAX = Constants.NRG_CREATE_CONTRACT_MAX;
    private static final long CONTRACT_CREATE_TX_NRG_MIN = FvmConstants.CREATE_TRANSACTION_FEE;
    private static final long TX_NRG_MAX = Constants.NRG_TRANSACTION_MAX;
    private static final long TX_NRG_MIN = FvmConstants.TRANSACTION_BASE_FEE;

    private static final long NRGPRICE_MIN = 10_000_000_000L; // 10 PLAT  (10 * 10 ^ -9 AION)
    private static final long NRGPRICE_MAX = 9_000_000_000_000_000_000L; //  9 AION

    public static boolean isValidNrgContractCreate(long energyLimit) {
        return (energyLimit >= CONTRACT_CREATE_TX_NRG_MIN) && (energyLimit <= CONTRACT_CREATE_TX_NRG_MAX);
    }

    public static boolean isValidNrgContractCreateAfterUnity(long energyLimit) {
        return (energyLimit >= (CONTRACT_CREATE_TX_NRG_MIN + TX_NRG_MIN))
                && (energyLimit <= CONTRACT_CREATE_TX_NRG_MAX);
    }

    public static boolean isValidNrgTx(long energyLimit) {
        return (energyLimit >= TX_NRG_MIN) && (energyLimit <= TX_NRG_MAX);
    }

    public static boolean isValidTxNrgPrice(long energyPrice) {
        return (energyPrice >= NRGPRICE_MIN) && (energyPrice <= NRGPRICE_MAX);
    }
}
