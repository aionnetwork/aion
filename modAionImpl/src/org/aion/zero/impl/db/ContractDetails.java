package org.aion.zero.impl.db;

import java.util.Collection;
import java.util.Map;
import org.aion.base.InternalVmType;
import org.aion.base.db.ContractDetail;
import org.aion.util.types.ByteArrayWrapper;

public interface ContractDetails extends ContractDetail {

    /**
     * Inserts a key-value pair containing the given key and the given value.
     *
     * @param key the key to be inserted
     * @param value the value to be inserted
     */
    void put(ByteArrayWrapper key, ByteArrayWrapper value);

    /**
     * Deletes any key-value pair that matches the given key.
     *
     * @param key the key to be deleted
     */
    void delete(ByteArrayWrapper key);

    /**
     * Returns the value associated with key.
     *
     * @implNote Some implementations may handle a non-existent key-value pair differently.
     * @param key The key to query.
     * @return The associated value or some non-value indicator in the case of no such key-value
     *     pair.
     */
    ByteArrayWrapper get(ByteArrayWrapper key);

    /**
     * Returns the code whose hash is codeHash.
     *
     * @param codeHash The hashed code.
     * @return the code.
     */
    byte[] getCode(byte[] codeHash);

    /** Returns all the stored codes keyed by their code hashes. */
    Map<ByteArrayWrapper, ByteArrayWrapper> getCodes();

    /**
     * Sets the code of the associated address to code.
     *
     * @param code The code to set.
     */
    void setCode(byte[] code);

    /** Stores all the given codes. */
    void appendCodes(Map<ByteArrayWrapper, ByteArrayWrapper> codes);

    /**
     * Returns the transaction type used to deploy the contract indicating which VM was used.
     *
     * @return the transaction type used to deploy the contract indicating which VM was used
     */
    InternalVmType getVmType();

    /**
     * Returns a byte array from contract storage representing an encoding of the object graph for
     * the given contract.
     *
     * @return a byte array from contract storage representing an encoding of the object graph for
     *     the given contract
     */
    byte[] getObjectGraph();

    /**
     * Saves the object graph for the given contract into contract storage.
     *
     * @param graph a byte array representing an encoding of the object graph for the given contract
     */
    void setObjectGraph(byte[] graph);

    /**
     * Returns the storage hash.
     *
     * @return the storage hash.
     */
    byte[] getStorageHash();

    /** Marks the contract as dirty (i.e. to be written). */
    void markAsDirty();

    /** Marks the contract as deleted. */
    void delete();

    /**
     * Returns true iff the ContractDetails is dirty.
     *
     * @return only if this is dirty.
     */
    boolean isDirty();

    /**
     * Returns true iff the ContractDetails is deleted.
     *
     * @return only if this is deleted.
     */
    boolean isDeleted();

    /**
     * Returns a mapping of all the key-value pairs that have keys in the given collection keys.
     *
     * @param keys the keys to query for
     * @return the associated mappings
     */
    Map<ByteArrayWrapper, ByteArrayWrapper> getStorage(Collection<ByteArrayWrapper> keys);
}
