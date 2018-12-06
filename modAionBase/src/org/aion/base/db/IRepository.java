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

import java.util.Map;
import java.util.Set;
import org.aion.vm.api.interfaces.Address;

/**
 * Database-like functionality.
 *
 * @apiNote Allows only batch operations on data.
 */
public interface IRepository<AS, DW, BSB> extends IRepositoryQuery<AS, DW> {

    /**
     * Creates a tracker repository for caching future changes.
     *
     * @return the new tracker repository
     */
    IRepositoryCache startTracking();

    /** Commits all the changes made in this repository to the database storage. */
    void flush();

    /**
     * Performs batch updates on the data.
     *
     * @param accountStates cached account states
     * @param contractDetails cached contract details
     */
    void updateBatch(
            Map<Address, AS> accountStates, Map<Address, IContractDetails<DW>> contractDetails);

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
     * @param root
     * @return
     */
    IRepository getSnapshotTo(byte[] root);

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
}
