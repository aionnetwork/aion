package org.aion.mcf.db;

import java.math.BigInteger;
import org.aion.db.impl.ByteArrayKeyValueStore;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;

/**
 * Repository interface for individual account additions and updates.
 *
 * @implNote Tracking a repository should be done through implementing this interface.
 */
public interface RepositoryCache<AS, BSB> extends Repository<AS, BSB> {

    /**
     * Creates a new account state in the database or cache.
     *
     * @param address the address of the account to be created
     * @return a {@link AS} object storing the newly created account state
     */
    AS createAccount(AionAddress address);

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

    /**
     * Flushes its state to other in such a manner that other receives sufficiently deep copies of
     * its AccountState and {@link ContractDetails} objects.
     *
     * <p>If {@code clearStateAfterFlush == true} then this repository's state will be completely
     * cleared after this method returns, otherwise it will retain all of its state.
     *
     * <p>A "sufficiently deep copy" is an imperfect deep copy (some original object references get
     * leaked) but such that for all conceivable use cases these imperfections should go unnoticed.
     * This is because doing something like copying the underlying data store makes no sense, both
     * repositories should be accessing it, and there are some other cases where objects are defined
     * as type {@link Object} and are cast to their expected types and copied, but will not be
     * copied if they are not in fact their expected types. This is something to be aware of. Most
     * of the imperfection results from the inability to copy {@link ByteArrayKeyValueStore} and
     * SecureTrie perfectly or at all (in the case of the former), for the above reasons.
     *
     * @param other The repository that will consume the state of this repository.
     * @param clearStateAfterFlush True if this repository should clear its state after flushing.
     */
    void flushCopiesTo(Repository other, boolean clearStateAfterFlush);
}
