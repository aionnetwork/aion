package org.aion.base.db;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Functionality for a key-value store allowing itemized updates.
 *
 * @param <KeyT> the data type of the keys
 * @param <ValueT> the data type of the values
 * @author Alexandra Roatis
 * @implNote For the underlying database connection, if {@code isClosed() == true}, then all
 *     function calls which require the database connection will throw a {@link RuntimeException},
 *     as documented by this interface.
 */
public interface IKeyValueStore<KeyT, ValueT> extends AutoCloseable {

    /**
     * Returns {@code true} if this data store contains no elements.
     *
     * @return {@code true} if this data store contains no elements
     * @throws RuntimeException if the data store is closed
     */
    boolean isEmpty();

    /**
     * Returns an {@link Iterator} over the set of keys stored in the database at the time when the
     * keys were requested. A snapshot can be used to ensure that the entries do not change while
     * iterating through the keys.
     *
     * @return an iterator over the set of stored keys
     * @throws RuntimeException if the data store is closed
     * @apiNote Returns an empty iterator if the database keys could not be retrieved.
     */
    Iterator<KeyT> keys();

    /**
     * Retrieves a value from the data store, wrapped in an {@link Optional} object. It is fulfilled
     * if a value was retrieved from the data store, otherwise the optional is empty.
     *
     * @param key the key for the new entry
     * @throws RuntimeException if the data store is closed
     * @throws IllegalArgumentException if the key is {@code null}
     */
    Optional<ValueT> get(KeyT key);

    /**
     * Stores or updates a value at the corresponding key. Makes no guarantees about when the value
     * is actually inserted into the underlying data store.
     *
     * @param key the key for the new entry
     * @param value the value for the new entry
     * @throws RuntimeException if the underlying data store is closed
     * @throws IllegalArgumentException if either the key or the value is {@code null}
     * @implNote The choice of when to push the changes to the data store is left up to the
     *     implementation.
     * @apiNote Put must have the following properties:
     *     <ol>
     *       <li>Creates a new entry in the cache, if the key-value pair does not exist in the cache
     *           or underlying data store.
     *       <li>Updates the entry in the cache when the key-value pair already exists.
     *     </ol>
     *     To delete a key one must explicitly call {@link #delete(Object)}.
     */
    void put(KeyT key, ValueT value);

    /**
     * Deletes a key from the data store. Makes no guarantees about when the value is actually
     * deleted from the underlying data store.
     *
     * @param key the key of the entry to be deleted
     * @throws RuntimeException if the underlying data store is closed
     * @throws IllegalArgumentException if the key is {@code null}
     * @implNote The choice of when to push the changes to the data store is left up to the
     *     implementation.
     */
    void delete(KeyT key);

    /**
     * Stores or updates a value at the corresponding key. The changes are cached until {@link
     * #commitBatch()} is called.
     *
     * <p>May delegate to calling {@link #put(Object, Object)} if batch functionality fails or is
     * not implemented.
     *
     * @param key the key for the new entry
     * @param value the value for the new entry
     * @throws RuntimeException if the underlying data store is closed
     * @throws IllegalArgumentException if either the key or the value is {@code null}
     * @implNote The choice of when to push the changes to the data store is left up to the
     *     implementation.
     * @apiNote Put must have the following properties:
     *     <ol>
     *       <li>Creates a new entry in the cache, if the key-value pair does not exist in the cache
     *           or underlying data store.
     *       <li>Updates the entry in the cache when the key-value pair already exists.
     *     </ol>
     *     To delete a key one must explicitly call {@link #deleteInBatch(Object)}.
     */
    void putToBatch(KeyT key, ValueT value);

    /**
     * Deletes a key from the data store. The changes are cached until {@link #commitBatch()} is
     * called.
     *
     * <p>May delegate to calling {@link #delete(Object)} if batch functionality fails or is not
     * implemented.
     *
     * @param key the key of the entry to be deleted
     * @throws RuntimeException if the underlying data store is closed
     * @throws IllegalArgumentException if the key is {@code null}
     * @implNote The choice of when to push the changes to the data store is left up to the
     *     implementation.
     */
    void deleteInBatch(KeyT key);

    /**
     * Pushes updates made using {@link #putToBatch(Object, Object)} and {@link
     * #deleteInBatch(Object)} to the underlying data source.
     */
    void commitBatch();

    /**
     * Puts or updates the data store with the given <i>key-value</i> pairs, as follows:
     *
     * <ul>
     *   <li>if the <i>key</i> is present in the data store, the stored <i>value</i> is overwritten
     *   <li>if the <i>key</i> is not present in the data store, the new <i>key-value</i> pair is
     *       stored
     * </ul>
     *
     * @param input a {@link Map} of key-value pairs to be updated in the database
     * @throws RuntimeException if the data store is closed
     * @throws IllegalArgumentException if the map contains a {@code null} key
     * @apiNote To delete a set of keys one must explicitly call {@link #deleteBatch(Collection)}.
     */
    void putBatch(Map<KeyT, ValueT> input);

    /**
     * Deletes the given keys from the data store. Makes no guarantees about when the entries are
     * actually deleted from the underlying data store.
     *
     * @param keys a {@link Collection} of keys to be deleted form storage
     * @throws RuntimeException if the data store is closed
     * @throws IllegalArgumentException if the collection contains a {@code null} key
     */
    void deleteBatch(Collection<KeyT> keys);

    /**
     * Checks that the data source connection is open. Throws a {@link RuntimeException} if the data
     * source connection is closed.
     *
     * @implNote Always do this check after acquiring a lock on the class/data. Otherwise it might
     *     produce inconsistent results due to lack of synchronization.
     */
    void check();
}
