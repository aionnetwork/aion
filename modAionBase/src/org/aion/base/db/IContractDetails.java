package org.aion.base.db;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.vm.api.interfaces.Address;

public interface IContractDetails {

    /**
     * Inserts key and value as a key-value pair. If the underlying byte array of value consists
     * only of zero bytes then ay existing key-value pair that has the same key as key will be
     * deleted.
     *
     * @param key The key.
     * @param value The value.
     */
    void put(ByteArrayWrapper key, ByteArrayWrapper value);

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
     * Returns the code of the address associated with this IContractDetails class. This is for
     * addresses that are smart contracts.
     *
     * @return the code of the associated address.
     */
    byte[] getCode();

    /**
     * Returns the code whose hash is codeHash.
     *
     * @param codeHash The hashed code.
     * @return the code.
     */
    byte[] getCode(byte[] codeHash);

    /**
     * Sets the code of the associated address to code.
     *
     * @param code The code to set.
     */
    void setCode(byte[] code);

    /**
     * Returns the storage hash.
     *
     * @return the storage hash.
     */
    byte[] getStorageHash();

    /**
     * Decodes an IContractDetails object from the RLP encoding rlpCode.
     *
     * @implNote Implementing classes may not necessarily support this method.
     * @param rlpCode The encoding to decode.
     */
    void decode(byte[] rlpCode);

    /**
     * Sets the dirty value to dirty.
     *
     * @param dirty The dirty value.
     */
    void setDirty(boolean dirty);

    /**
     * Sets the deleted value to deleted.
     *
     * @param deleted the deleted value.
     */
    void setDeleted(boolean deleted);

    /**
     * Returns true iff the IContractDetails is dirty.
     *
     * @return only if this is dirty.
     */
    boolean isDirty();

    /**
     * Returns true iff the IContractDetails is deleted.
     *
     * @return only if this is deleted.
     */
    boolean isDeleted();

    /**
     * Returns an rlp encoding of this IContractDetails object.
     *
     * @implNote Implementing classes may not necessarily support this method.
     * @return an rlp encoding of this.
     */
    byte[] getEncoded();

    /**
     * Returns a mapping of all the key-value pairs who have keys in the collection keys.
     *
     * @param keys The keys to query for.
     * @return The associated mappings.
     */
    Map<ByteArrayWrapper, ByteArrayWrapper> getStorage(Collection<ByteArrayWrapper> keys);

    /**
     * Sets the storage to contain the specified keys and values. This method creates pairings of
     * the keys and values by mapping the i'th key in storageKeys to the i'th value in
     * storageValues.
     *
     * @param storageKeys The keys.
     * @param storageValues The values.
     */
    void setStorage(List<ByteArrayWrapper> storageKeys, List<ByteArrayWrapper> storageValues);

    /**
     * Sets the storage to contain the specified key-value mappings.
     *
     * @param storage The specified mappings.
     */
    void setStorage(Map<ByteArrayWrapper, ByteArrayWrapper> storage);

    /**
     * Get the address associated with this IContractDetails.
     *
     * @return the associated address.
     */
    Address getAddress();

    /**
     * Sets the associated address to address.
     *
     * @param address The address to set.
     */
    void setAddress(Address address);

    /**
     * Returns a string representation of this IContractDetails.
     *
     * @return a string representation.
     */
    String toString();

    /** Syncs the storage trie. */
    void syncStorage();

    /**
     * Returns an IContractDetails object pertaining to a specific point in time given by the root
     * hash hash.
     *
     * @implNote Implementing classes may not necessarily support this method.
     * @param hash The root hash to search for.
     * @return the specified IContractDetails.
     */
    IContractDetails getSnapshotTo(byte[] hash);

    /**
     * Sets the data source to dataSource.
     *
     * @implNote Implementing classes may not necessarily support this method.
     * @param dataSource The new dataSource.
     */
    void setDataSource(IByteArrayKeyValueStore dataSource);

    /**
     * Returns a sufficiently deep copy of this object. It is up to all implementations of this
     * method to declare which original object references are in fact leaked by this copy, if any,
     * and to provide justification of why, despite this, the copy is nonetheless sufficiently deep.
     *
     * @return A copy of this object.
     */
    IContractDetails copy();
}
