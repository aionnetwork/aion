/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 */

package org.aion.base.db;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Functionality for a key-value store allowing itemized updates.
 *
 * @param <KEY> the data type of the keys
 * @param <VALUE> the data type of the values
 * @author Alexandra Roatis
 * @implNote For the underlying database connection, if {@code isClosed() == true}, then all
 *     function calls which require the database connection will throw a {@link RuntimeException},
 *     as documented by this interface.
 */
public interface IKeyValueStore<KEY, VALUE> extends AutoCloseable {

    /**
     * Returns {@code true} if this data store contains no elements.
     *
     * @return {@code true} if this data store contains no elements
     * @throws RuntimeException if the data store is closed
     */
    boolean isEmpty();

    /**
     * Returns the set of keys stored in the database.
     *
     * @return a set containing all the stored keys
     * @throws RuntimeException if the data store is closed
     * @apiNote Returns an empty set if the database keys could not be retrieved.
     */
    Set<KEY> keys();

    /**
     * Retrieves a value from the data store, wrapped in an {@link Optional} object. It is fulfilled
     * if a value was retrieved from the data store, otherwise the optional is empty.
     *
     * @param key the key for the new entry
     * @throws RuntimeException if the data store is closed
     * @throws IllegalArgumentException if the key is {@code null}
     */
    Optional<VALUE> get(KEY key);

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
    void put(KEY key, VALUE value);

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
    void delete(KEY key);

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
    void putToBatch(KEY key, VALUE value);

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
    void deleteInBatch(KEY key);

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
    void putBatch(Map<KEY, VALUE> input);

    /**
     * Deletes the given keys from the data store. Makes no guarantees about when the entries are
     * actually deleted from the underlying data store.
     *
     * @param keys a {@link Collection} of keys to be deleted form storage
     * @throws RuntimeException if the data store is closed
     * @throws IllegalArgumentException if the collection contains a {@code null} key
     */
    void deleteBatch(Collection<KEY> keys);

    /**
     * Checks that the data source connection is open. Throws a {@link RuntimeException} if the data
     * source connection is closed.
     *
     * @implNote Always do this check after acquiring a lock on the class/data. Otherwise it might
     *     produce inconsistent results due to lack of synchronization.
     */
    void check();
}
