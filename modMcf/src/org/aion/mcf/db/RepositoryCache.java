package org.aion.mcf.db;

import java.math.BigInteger;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;

/**
 * Repository interface for individual account additions and updates.
 *
 * @implNote Tracking a repository should be done through implementing this interface.
 */
public interface RepositoryCache<AS> extends Repository<AS> {

    Repository<AS> getParent();

    /**
     * Creates a new account state in the database or cache.
     *
     * @param address the address of the account to be created
     */
    void createAccount(AionAddress address);

    /**
     * Deletes the account from the cache and database.
     *
     * @param address the address of the account to be deleted
     * @implNote This method only marks the account for deletion. Removing the account from the
     *     database is done at the next flush operation.
     */
    void deleteAccount(AionAddress address);

    /**
     * Increases by one the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @return the updated value of the nonce
     */
    BigInteger incrementNonce(AionAddress address);

    /**
     * Sets to a specific value the nonce of the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @return the updated nonce value for the account
     */
    BigInteger setNonce(AionAddress address, BigInteger nonce);

    /**
     * Adds the given value to the balance of the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @param value to be added to the balance
     * @return the updated balance for the account
     */
    BigInteger addBalance(AionAddress address, BigInteger value);

    /**
     * Stores code associated with an account.
     *
     * @param address the address of the account of interest
     * @param code the code that will be associated with this account
     * @implNote Calling this method on already initialized code should leave the account and
     *     contract state unaltered.
     */
    void saveCode(AionAddress address, byte[] code);

    /**
     * Sets the transaction type value used to deploy the contract symbolizing the VM that manages
     * the contract.
     *
     * @param contract the account address
     * @param vmType the transaction type value used to deploy the contract symbolizing the VM that
     *     manages the contract
     */
    void saveVmType(AionAddress contract, InternalVmType vmType);

    /**
     * Saves the object graph for the given contract into contract storage.
     *
     * @param contract the account address
     * @param graph a byte array representing an encoding of the object graph for the given contract
     */
    void saveObjectGraph(AionAddress contract, byte[] graph);

    /**
     * Store the given data at the given key in the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @param key the key at which the data will be stored
     * @param value the data to be stored
     */
    void addStorageRow(AionAddress address, ByteArrayWrapper key, ByteArrayWrapper value);

    /**
     * Remove the given storage key from the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @param key the key for which the data will be removed
     */
    void removeStorageRow(AionAddress address, ByteArrayWrapper key);

    void flushTo(Repository repo, boolean clearStateAfterFlush);
}
