package org.aion.mcf.db;

import java.util.Map;
import java.util.Set;
import org.aion.types.AionAddress;

/**
 * Database-like functionality.
 *
 * @apiNote Allows only batch operations on data.
 */
public interface Repository<AS, BSB> extends RepositoryQuery<AS> {

    /**
     * Creates a tracker repository for caching future changes.
     *
     * @return the new tracker repository
     */
    RepositoryCache startTracking();

    /** Commits all the changes made in this repository to the database storage. */
    void flush();

    /**
     * Performs batch updates on the data.
     *
     * @param accountStates cached account states
     * @param contractDetails cached contract details
     */
    void updateBatch(
            Map<AionAddress, AS> accountStates, Map<AionAddress, ContractDetails> contractDetails);

    /** Reverts all the changes performed by this repository. */
    void rollback();

    /**
     * Checks if the current repository has an open connection to the database.
     *
     * @return {@code true} if the database connection is open, {@code false} otherwise
     */
    boolean isClosed();

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

    // TODO: perhaps remove
    BSB getBlockStore();

    /** Performs batch transactions add. */
    void addTxBatch(Map<byte[], byte[]> pendingTx, boolean isPool);

    /** Performs batch transactions remove. */
    void removeTxBatch(Set<byte[]> pendingTx, boolean isPool);

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
     * Set the transformed code to the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @param code the transformed code
     */
    void setTransformedCode(AionAddress address, byte[] code);
}
