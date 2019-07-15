package org.aion.precompiled.type;

import java.math.BigInteger;
import org.aion.types.AionAddress;

/**
 * An interface that allows the caller to supply the precompiled contract module with access to
 * state updates and state queries.
 */
public interface IExternalStateForPrecompiled {

    /**
     * Commits any state changes in this external state to its parent external state.
     */
    void commit();

    /**
     * Returns a new external state that is a direct child of this external state.
     *
     * @return a child external state.
     */
    IExternalStateForPrecompiled newChildExternalState();

    /**
     * Adds the specified key-value pair to the storage space of the given address, overwriting the
     * value of the pairing with this new value if the key already exists.
     *
     * @param address The address.
     * @param key The key.
     * @param value The value.
     */
    void addStorageValue(AionAddress address, IPrecompiledDataWord key, IPrecompiledDataWord value);

    /**
     * Removes any key-value pairing whose key is the given key in the storage space of the given
     * address.
     *
     * @param address The address.
     * @param key The key.
     */
    void removeStorage(AionAddress address, IPrecompiledDataWord key);

    /**
     * Returns the value of the key-value pairing in the storage space of the given address if one
     * exists, and {@code null} otherwise.
     *
     * @param address The address.
     * @param key The key.
     * @return the value.
     */
    IPrecompiledDataWord getStorageValue(AionAddress address, IPrecompiledDataWord key);

    /**
     * Returns the balance of the specified address.
     *
     * @param address The address.
     * @return the balance.
     */
    BigInteger getBalance(AionAddress address);

    /**
     * Adds the specified amount to the balance of the given address.
     *
     * @param address The address.
     * @param amount The amount.
     */
    void addBalance(AionAddress address, BigInteger amount);

    /**
     * Returns the nonce of the specified address.
     *
     * @param address The address.
     * @return the nonce.
     */
    BigInteger getNonce(AionAddress address);

    /**
     * Increments the nonce of the given address by one.
     *
     * @param address The address.
     */
    void incrementNonce(AionAddress address);

    /**
     * Returns the block number of the current block.
     *
     * @return the block.
     */
    long getBlockNumber();

    /**
     * Returns {@code true} if the given energy limit is valid for CREATE contracts. Otherwise
     * {@code false}.
     *
     * @param energyLimit The energy limit to check.
     * @return whether the limit is valid or not.
     */
    boolean isValidEnergyLimitForCreate(long energyLimit);

    /**
     * Returns {@code true} if the given energy limit is valid for non-CREATE contracts. Otherwise
     * {@code false}.
     *
     * @param energyLimit The energy limit to check.
     * @return whether the limit is valid or not.
     */
    boolean isValidEnergyLimitForNonCreate(long energyLimit);

    /**
     * Returns {@code true} only if the nonce of the address is equal to the given nonce or not.
     *
     * @param address The address.
     * @param nonce The nonce to check.
     * @return whether the nonce is equal or not.
     */
    boolean accountNonceEquals(AionAddress address, BigInteger nonce);

    /**
     * Returns {@code true} only if the balance of the address is greater than or equal to the
     * balance or not.
     *
     * @param address The address.
     * @param balance The balance to check.
     * @return whether the balance is equal or not.
     */
    boolean accountBalanceIsAtLeast(AionAddress address, BigInteger balance);

    /**
     * Deducts the given energyCost from the address.
     *
     * @param address The address.
     * @param energyCost The energy cost to deduct.
     */
    void deductEnergyCost(AionAddress address, BigInteger energyCost);
}
