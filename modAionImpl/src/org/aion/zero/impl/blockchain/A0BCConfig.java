package org.aion.zero.impl.blockchain;

import org.aion.types.AionAddress;
import org.aion.zero.impl.core.energy.AbstractEnergyStrategyLimit;

public interface A0BCConfig {
    /**
     * Retrieve the currently set extra data for this particular node, blocks mined with this node
     * will use this as extra data.
     *
     * @return {@code extraData} a (up to) 32-byte value
     */
    byte[] getExtraData();

    AionAddress getMinerCoinbase();

    /** Retrieves the selected energy strategy algorithm */
    AbstractEnergyStrategyLimit getEnergyLimitStrategy();

    /** Retrieves the desired behavior for internal transactions */
    boolean isInternalTransactionStorageEnabled();
}
