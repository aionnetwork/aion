package org.aion.mcf.db;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Database-like functionality.
 *
 * @apiNote Allows only batch operations on data.
 */
public interface Repository<AS> {

    /**
     * Creates a tracker repository for caching future changes.
     *
     * @return the new tracker repository
     */
    RepositoryCache startTracking();

    /**
     * Performs batch updates on the data.
     *
     * @param accountStates cached account states
     * @param contractDetails cached contract details
     */
    void updateBatch(
            Map<AionAddress, AS> accountStates,
            Map<AionAddress, ContractDetails> contractDetails,
            Map<AionAddress, TransformedCodeInfo> transformedCodeCache);

    /** Reverts all the changes performed by this repository. */
    void rollback();

    /** Closes the connection to the database. */
    void close();

    /** Reduce the size of the database when possible. */
    void compact();

    // navigate through snapshots
    // --------------------------------------------------------------------------------------

    /**
     * Used to check for corruption in the state database.
     *
     * @param root a world state trie root
     * @return {@code true} if the root is valid, {@code false} otherwise
     */
    boolean isValidRoot(byte[] root);

    /**
     * Used to check for corruption in the index database.
     *
     * @param hash a block hash
     * @return {@code true} if the block hash has a corresponding index, {@code false} otherwise
     */
    boolean isIndexed(byte[] hash, long level);

    byte[] getRoot();

    /**
     * Return to one of the previous snapshots by moving the root.
     *
     * @param root - new root
     */
    void syncToRoot(byte[] root);

    /**
     * TODO: differentiate between the sync to root and snapshot functionality
     *
     * @param root hash data
     * @return repo root
     */
    Repository getSnapshotTo(byte[] root);

    /**
     * @return {@code true} if the repository is a snapshot (with limited functionality), {@code
     *     false} otherwise
     */
    boolean isSnapshot();

    byte[] getBlockHashByNumber(long blockNumber);

    /**
     * Retrieves the vm type used when the given contract with the given code hash was deployed.
     *
     * @param contract the contract address for which the vm type is being retrieved
     * @param codeHash the hash of the code with which the contract was deployed allowing
     *     distinction between contracts deployed on separate chains
     * @return the vm type used when the given contract with the given code hash was deployed
     */
    InternalVmType getVMUsed(AionAddress contract, byte[] codeHash);

    /**
     * Returns the transaction type used to deploy the contract indicating which VM was used.
     *
     * @return the transaction type used to deploy the contract indicating which VM was used
     */
    InternalVmType getVmType(AionAddress contract);

    /**
     * Set the transformed code to the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @param transformedCode the transformed code
     */
    void setTransformedCode(AionAddress address, byte[] codeHash, int avmVersion, byte[] transformedCode);

    /**
     * Retrieves the transformed code for the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @return the transformed code associated to the account in {@code byte} array format
     */
    byte[] getTransformedCode(AionAddress address, byte[] codeHash, int avmVersion);

    /**
     * Retrieves the code for the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @return the code associated to the account in {@code byte} array format
     */
    byte[] getCode(AionAddress address);

    /**
     * Returns {@code true} only if the specified account has non-empty storage associated with it. Otherwise {@code false}.
     *
     * @param address The account address.
     * @return whether the account has non-empty storage or not.
     */
    boolean hasStorage(AionAddress address);

    /**
     * Checks if the database contains an account state associated with the given address.
     *
     * @param address the address of the account of interest
     * @return {@code true} if the account exists, {@code false} otherwise
     */
    boolean hasAccountState(AionAddress address);

    /**
     * Retrieves the current state of the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @return a {@link AS} object representing the account state as is stored in the database or
     *     cache
     */
    AS getAccountState(AionAddress address);

    /**
     * Retrieves the current balance of the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @return a {@link BigInteger} value representing the current account balance
     */
    BigInteger getBalance(AionAddress address);

    /**
     * Retrieves the current nonce of the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @return a {@link BigInteger} value representing the current account nonce
     */
    BigInteger getNonce(AionAddress address);

    /**
     * Checks if the database contains contract details associated with the given address.
     *
     * @param addr the address of the account of interest
     * @return {@code true} if there are contract details associated with the account, {@code false}
     *     otherwise
     */
    boolean hasContractDetails(AionAddress addr);

    /**
     * Retrieves the contract details of the account associated with the given address.
     *
     * @param addr the address of the account of interest
     * @return a {@link ContractDetails} object representing the contract details as are stored in
     *     the database or cache
     */
    ContractDetails getContractDetails(AionAddress addr);

    /**
     * Returns a byte array from contract storage representing an encoding of the object graph for
     * the given contract.
     *
     * @param contract the account address
     * @return a byte array from contract storage representing an encoding of the object graph for
     *     the given contract
     */
    byte[] getObjectGraph(AionAddress contract);

    /**
     * Retrieves the entries for the specified key values stored at the account associated with the
     * given address.
     *
     * @param address the address of the account of interest
     * @param keys the collection of keys of interest (which may be {@code null})
     * @return the storage entries for the specified keys, or the full storage if the key collection
     *     is {@code null}
     * @apiNote When called with a null key collection, the method retrieves all the storage keys.
     */
    Map<ByteArrayWrapper, ByteArrayWrapper> getStorage(AionAddress address, Collection<ByteArrayWrapper> keys);

    //    /**
    //     * Retrieves the storage size the account associated with the given address.
    //     *
    //     * @param address
    //     *            the address of the account of interest
    //     * @return the number of storage entries for the given account
    //     */
    //    int getStorageSize(Address address);
    //
    //    /**
    //     * Retrieves all the storage keys for the account associated with the given
    //     * address.
    //     *
    //     * @param address
    //     *            the address of the account of interest
    //     * @return the set of storage keys, or an empty set if the given account
    //     *         address does not exist
    //     */
    //    Set<ByteArrayByteArrayWrapper> getStorageKeys(Address address);

    /**
     * Retrieves the stored value for the specified key stored at the account associated with the
     * given address.
     *
     * @param address the address of the account of interest
     * @param key the key of interest
     * @return a {@link ByteArrayWrapper} representing the data associated with the given key
     */
    ByteArrayWrapper getStorageValue(AionAddress address, ByteArrayWrapper key);
}
