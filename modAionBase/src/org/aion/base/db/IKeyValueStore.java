/*******************************************************************************
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
 ******************************************************************************/
package org.aion.base.db;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Functionality for a key-value cache allowing itemized updates.
 *
 * @param <K>
 *            the data type of the keys
 * @param <V>
 *            the data type of the values
 * @author Alexandra Roatis
 * @implNote For the underlying DB connection, if [isClosed() == true], then all
 *           function calls which are documented to throw RuntimeException, will
 *           throw a RuntimeException.
 */
public interface IKeyValueStore<K, V> extends AutoCloseable {

    // Querying
    // --------------------------------------------------------------------------------------------------------

    /**
     * Returns if the DB is empty or not.
     *
     * @return True if number of keys > 0, false otherwise
     * @throws RuntimeException
     *             if the data store is closed
     */
    boolean isEmpty();

    /**
     * Returns the set of keys for the database.
     *
     * @return Set of keys
     * @throws RuntimeException
     *             if the data store is closed
     * @apiNote Returns an empty set if the database keys could not be
     *          retrieved.
     */
    Set<K> keys();

    /**
     * get retrieves a value from the database, returning an optional, it is
     * fulfilled if a value was able to be retrieved from the DB, otherwise the
     * optional is empty
     *
     * @param k
     * @throws RuntimeException
     *             if the data store is closed
     * @throws IllegalArgumentException
     *             if the key is null
     */
    Optional<V> get(K k);

    // Updates for individual keys
    // -------------------------------------------------------------------------------------

    /**
     * Places or updates a value into the cache at the corresponding key. Makes
     * no guarantees about when the value is actually inserted into the
     * underlying data store.
     *
     * @param k
     *            the key for the new entry
     * @param v
     *            the value for the new entry
     * @throws RuntimeException
     *             if the underlying data store is closed
     * @throws IllegalArgumentException
     *             if the key is null
     * @implNote The choice of when to push the changes to the data store is
     *           left up to the implementation.
     * @apiNote Put must have the following properties:
     *          <ol>
     *          <li>Creates a new entry in the cache, if the key-value pair does
     *          not exist in the cache or underlying data store.</li>
     *          <li>Updates the entry in the cache when the key-value pair
     *          already exists.
     *          <li>Deletes the entry when given a {@code null} value.</li>
     *          </ol>
     */
    void put(K k, V v);

    /**
     * Delete an entry from the cache, marking it for deletion inside the data
     * store. Makes no guarantees about when the value is actually deleted from
     * the underlying data store.
     *
     * @param k
     *            the key of the entry to be deleted
     * @throws RuntimeException
     *             if the underlying data store is closed
     * @throws IllegalArgumentException
     *             if the key is null
     * @implNote The choice of when to push the changes to the data store is
     *           left up to the implementation.
     */
    void delete(K k);

    // Batch Updates
    // ---------------------------------------------------------------------------------------------------

    /**
     * Puts or updates the data store with the given <i>key-value</i> pairs, as
     * follows:
     * <ul>
     * <li>if the <i>key</i> is present in the data store, the stored
     * <i>value</i> is overwritten</li>
     * <li>if the <i>key</i> is not present in the data store, the new
     * <i>key-value</i> pair is stored</li>
     * <li>if the <i>value</i> is null, the matching stored <i>key</i> will be
     * deleted from the data store, or</li>
     * </ul>
     *
     * @param inputMap
     *            a {@link Map} of key-value pairs to be updated in the database
     * @throws RuntimeException
     *             if the data store is closed
     * @throws IllegalArgumentException
     *             if the map contains a null key
     */
    void putBatch(Map<K, V> inputMap);

    /**
     * Similar to delete, except operates on a list of keys
     *
     * @param keys
     * @throws RuntimeException
     *             if the data store is closed
     * @throws IllegalArgumentException
     *             if the collection contains a null key
     */
    void deleteBatch(Collection<K> keys);
}
