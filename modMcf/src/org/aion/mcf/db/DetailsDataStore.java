/* ******************************************************************************
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
package org.aion.mcf.db;

import static org.aion.base.util.ByteArrayWrapper.wrap;

import java.util.*;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepositoryConfig;
import org.aion.base.type.Address;
import org.aion.base.type.IBlockHeader;
import org.aion.base.type.ITransaction;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.trie.JournalPruneDataSource;
import org.aion.mcf.types.AbstractBlock;

/** Detail data storage , */
public class DetailsDataStore<
        BLK extends AbstractBlock<BH, ? extends ITransaction>, BH extends IBlockHeader> {

    private JournalPruneDataSource storageDSPrune;
    private IRepositoryConfig repoConfig;

    private IByteArrayKeyValueDatabase detailsSrc;
    private IByteArrayKeyValueDatabase storageSrc;
    private Set<ByteArrayWrapper> removes = new HashSet<>();

    public DetailsDataStore() {}

    public DetailsDataStore(
            IByteArrayKeyValueDatabase detailsCache,
            IByteArrayKeyValueDatabase storageCache,
            IRepositoryConfig repoConfig) {

        this.repoConfig = repoConfig;
        withDb(detailsCache, storageCache);
    }

    public DetailsDataStore<BLK, BH> withDb(
            IByteArrayKeyValueDatabase detailsSrc, IByteArrayKeyValueDatabase storageSrc) {
        this.detailsSrc = detailsSrc;
        this.storageSrc = storageSrc;
        this.storageDSPrune = new JournalPruneDataSource(storageSrc);
        return this;
    }

    /**
     * Fetches the ContractDetails from the cache, and if it doesn't exist, add to the remove set.
     *
     * @param key
     * @return
     */
    public synchronized IContractDetails<IDataWord> get(byte[] key) {

        ByteArrayWrapper wrappedKey = wrap(key);
        Optional<byte[]> rawDetails = detailsSrc.get(key);

        // If it doesn't exist in cache or database.
        if (!rawDetails.isPresent()) {

            // Check to see if we have to remove it.
            // If it isn't in removes set, we add it to removes set.
            if (!removes.contains(wrappedKey)) {
                removes.add(wrappedKey);
            }
            return null;
        }

        // Found something from cache or database, return it by decoding it.
        IContractDetails<IDataWord> detailsImpl = repoConfig.contractDetailsImpl();
        detailsImpl.setDataSource(storageDSPrune);
        detailsImpl.decode(rawDetails.get()); // We can safely get as we checked
        // if it is present.

        return detailsImpl;
    }

    public synchronized void update(Address key, IContractDetails<IDataWord> contractDetails) {

        contractDetails.setAddress(key);
        ByteArrayWrapper wrappedKey = wrap(key.toBytes());

        // Put into cache.
        byte[] rawDetails = contractDetails == null ? null : contractDetails.getEncoded();
        detailsSrc.put(key.toBytes(), rawDetails);

        contractDetails.syncStorage();

        // Remove from the remove set.
        removes.remove(wrappedKey);
    }

    public synchronized void remove(byte[] key) {
        ByteArrayWrapper wrappedKey = wrap(key);
        detailsSrc.put(key, null);

        removes.add(wrappedKey);
    }

    public synchronized void flush() {
        flushInternal();
    }

    private long flushInternal() {
        long totalSize = 0;

        syncLargeStorage();

        // Get everything from the cache and calculate the size.
        Set<byte[]> keysFromSource = detailsSrc.keys();
        for (byte[] keyInSource : keysFromSource) {
            // Fetch the value given the keys.
            Optional<byte[]> valFromKey = detailsSrc.get(keyInSource);

            // Add to total size given size of the value
            totalSize += valFromKey.map(rawDetails -> rawDetails.length).orElse(0);
        }

        // Flushes both details and storage.
        detailsSrc.commit();
        storageSrc.commit();

        return totalSize;
    }

    public void syncLargeStorage() {

        Set<byte[]> keysFromSource = detailsSrc.keys();
        for (byte[] keyInSource : keysFromSource) {

            // Fetch the value given the keys.
            Optional<byte[]> rawDetails = detailsSrc.get(keyInSource);

            // If it is null, just continue
            if (!rawDetails.isPresent()) {
                continue;
            }

            // Decode the details.
            IContractDetails<IDataWord> detailsImpl = repoConfig.contractDetailsImpl();
            detailsImpl.setDataSource(storageDSPrune);
            detailsImpl.decode(rawDetails.get()); // We can safely get as we
            // checked if it is present.

            // IContractDetails details = entry.getValue();
            detailsImpl.syncStorage();
        }
    }

    public JournalPruneDataSource getStorageDSPrune() {
        return storageDSPrune;
    }

    public synchronized Set<ByteArrayWrapper> keys() {
        // TODO - @yao do we wanted a sorted set?
        Set<ByteArrayWrapper> keys = new HashSet<>();
        for (byte[] key : detailsSrc.keys()) {
            keys.add(wrap(key));
        }
        return keys;
    }

    public synchronized void close() {
        try {
            detailsSrc.close();
            storageSrc.close();
        } catch (Exception e) {
            throw new RuntimeException("error closing db");
        }
    }

    public static List<ByteArrayWrapper> dumpKeys(IByteArrayKeyValueDatabase ds) {
        ArrayList<ByteArrayWrapper> keys = new ArrayList<>();

        for (byte[] key : ds.keys()) {
            keys.add(wrap(key));
        }

        Collections.sort(keys);
        return keys;
    }
}
