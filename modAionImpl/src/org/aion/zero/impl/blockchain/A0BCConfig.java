package org.aion.zero.impl.blockchain;

import org.aion.types.AionAddress;
import org.aion.zero.impl.core.energy.AbstractEnergyStrategyLimit;

public interface A0BCConfig {

    /**
     * Retrieve the currently set mining coinbase for this particular node, blocks mined with this node
     * will use this as the coinbase.
     *
     * @return {@code coinbase} a 32-bytes address
     */
    AionAddress getCoinbase();

    /**
     * Retrieve the currently set staking coinbase for this particular node, blocks staked with this node
     * will use this as the coinbase when the node is using the internal staking block producer.
     *
     * @return {@code coinbase} a 32-bytes address
     */
    AionAddress getStakerCoinbase();
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

    /** Retrieves the desired behavior for internal transactions */
    boolean isInternalTransactionStorageEnabled();

    /**
     * Retrive status of the internal staking block producer.
     * @return status of the internal staking block producer.
     */
    boolean isInternalStakingEnabled();
}
