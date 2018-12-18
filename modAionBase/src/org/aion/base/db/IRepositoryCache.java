package org.aion.base.db;

import java.math.BigInteger;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.vm.api.interfaces.Address;

/**
 * Repository interface for individual account additions and updates.
 *
 * @implNote Tracking a repository should be done through implementing this interface.
 */
public interface IRepositoryCache<AS, BSB> extends IRepository<AS, BSB> {

    // setters relating to user accounts
    // -------------------------------------------------------------------------------

    /**
     * Creates a new account state in the database or cache.
     *
     * @param address the address of the account to be created
     * @return a {@link AS} object storing the newly created account state
     */
    AS createAccount(Address address);

    /**
     * Deletes the account from the cache and database.
     *
     * @param address the address of the account to be deleted
     * @implNote This method only marks the account for deletion. Removing the account from the
     *     database is done at the next flush operation.
     */
    void deleteAccount(Address address);

    /**
     * Increases by one the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @return the updated value of the nonce
     */
    BigInteger incrementNonce(Address address);

    /**
     * Sets to a specific value the nonce of the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @return the updated nonce value for the account
     */
    BigInteger setNonce(Address address, BigInteger nonce);

    /**
     * Adds the given value to the balance of the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @param value to be added to the balance
     * @return the updated balance for the account
     */
    BigInteger addBalance(Address address, BigInteger value);

    // setters relating to contracts
    // -----------------------------------------------------------------------------------

    /**
     * Stores code associated with an account.
     *
     * @param address the address of the account of interest
     * @param code the code that will be associated with this account
     * @implNote Calling this method on already initialized code should leave the account and
     *     contract state unaltered.
     */
    void saveCode(Address address, byte[] code);

    // setters relating to storage
    // -------------------------------------------------------------------------------------

    /**
     * Store the given data at the given key in the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @param key the key at which the date will be stored
     * @param value the data to be stored
     */
    void addStorageRow(Address address, ByteArrayWrapper key, ByteArrayWrapper value);

    void flushTo(IRepository repo, boolean clearStateAfterFlush);
}
