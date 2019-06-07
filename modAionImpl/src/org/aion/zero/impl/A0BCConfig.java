package org.aion.zero.impl;
import org.aion.types.AionAddress;
import org.aion.zero.impl.core.energy.AbstractEnergyStrategyLimit;

public interface A0BCConfig {

    /**
     * Retrieve the currently set coinbase for this particular node, blocks mined with this node
     * will use this as the coinbase.
     *
     * @return {@code coinbase} a 32-bytes address
     */
    AionAddress getCoinbase();

    /**
     * Retrieve the currently set extra data for this particular node, blocks mined with this node
     * will use this as extra data.
     *
     * @return {@code extraData} a (up to) 32-byte value
     */
    byte[] getExtraData();

    /**
     * Retrieves whether the kernel should exit on a block conflict.
     *
     * @return {@true} if system should exit on a block conflict
     */
    boolean getExitOnBlockConflict();

    AionAddress getMinerCoinbase();

    /** Retrieves the number indicating how many blocks between each flush */
    int getFlushInterval();

    /** Retrieves the selected energy strategy algorithm */
    AbstractEnergyStrategyLimit getEnergyLimitStrategy();

}
