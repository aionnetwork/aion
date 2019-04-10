package org.aion.mcf.db;

import static org.aion.types.ByteArrayWrapper.wrap;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import org.aion.interfaces.block.BlockHeader;
import org.aion.interfaces.db.ByteArrayKeyValueDatabase;
import org.aion.interfaces.db.ContractDetails;
import org.aion.interfaces.db.RepositoryConfig;
import org.aion.interfaces.tx.Transaction;
import org.aion.mcf.trie.JournalPruneDataSource;
import org.aion.mcf.types.AbstractBlock;
import org.aion.types.Address;
import org.aion.types.ByteArrayWrapper;

/** Detail data storage , */
public class DetailsDataStore<
        BLK extends AbstractBlock<BH, ? extends Transaction>, BH extends BlockHeader> {

    private JournalPruneDataSource storageDSPrune;
    private RepositoryConfig repoConfig;

    private ByteArrayKeyValueDatabase detailsSrc;
    private ByteArrayKeyValueDatabase storageSrc;
    private Set<ByteArrayWrapper> removes = new HashSet<>();

    public DetailsDataStore() {}

    public DetailsDataStore(
            ByteArrayKeyValueDatabase detailsCache,
            ByteArrayKeyValueDatabase storageCache,
            RepositoryConfig repoConfig) {

        this.repoConfig = repoConfig;
        withDb(detailsCache, storageCache);
    }

    public DetailsDataStore<BLK, BH> withDb(
            ByteArrayKeyValueDatabase detailsSrc, ByteArrayKeyValueDatabase storageSrc) {
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
    public synchronized ContractDetails get(byte[] key) {

        ByteArrayWrapper wrappedKey = wrap(key);
        Optional<byte[]> rawDetails = detailsSrc.get(key);

        // If it doesn't exist in cache or database.
        if (!rawDetails.isPresent()) {

            // Check to see if we have to remove it.
            // If it isn't in removes set, we add it to removes set.
            removes.add(wrappedKey);
            return null;
        }

        // Found something from cache or database, return it by decoding it.
        ContractDetails detailsImpl = repoConfig.contractDetailsImpl();
        detailsImpl.setDataSource(storageDSPrune);
        detailsImpl.decode(rawDetails.get()); // We can safely get as we checked
        // if it is present.

        return detailsImpl;
    }

    public synchronized void update(Address key, ContractDetails contractDetails) {

        contractDetails.setAddress(key);
        ByteArrayWrapper wrappedKey = wrap(key.toBytes());

        // Put into cache.
        byte[] rawDetails = contractDetails.getEncoded();
        detailsSrc.put(key.toBytes(), rawDetails);

        contractDetails.syncStorage();

        // Remove from the remove set.
        removes.remove(wrappedKey);
    }

    public synchronized void remove(byte[] key) {
        ByteArrayWrapper wrappedKey = wrap(key);
        detailsSrc.delete(key);

        removes.add(wrappedKey);
    }

    public synchronized void flush() {
        flushInternal();
    }

    private long flushInternal() {
        long totalSize = 0;

        syncLargeStorage();

        // Get everything from the cache and calculate the size.
        Iterator<byte[]> keysFromSource = detailsSrc.keys();
        while (keysFromSource.hasNext()) {
            byte[] keyInSource = keysFromSource.next();
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

        Iterator<byte[]> keysFromSource = detailsSrc.keys();
        while (keysFromSource.hasNext()) {
            byte[] keyInSource = keysFromSource.next();

            // Fetch the value given the keys.
            Optional<byte[]> rawDetails = detailsSrc.get(keyInSource);

            // If it is null, just continue
            if (!rawDetails.isPresent()) {
                continue;
            }

            // Decode the details.
            ContractDetails detailsImpl = repoConfig.contractDetailsImpl();
            detailsImpl.setDataSource(storageDSPrune);
            detailsImpl.decode(rawDetails.get(), true);
            // We can safely get as we checked if it is present.

            // ContractDetails details = entry.getValue();
            detailsImpl.syncStorage();
        }
    }

    public JournalPruneDataSource getStorageDSPrune() {
        return storageDSPrune;
    }

    public synchronized Iterator<ByteArrayWrapper> keys() {
        return new DetailsIteratorWrapper(detailsSrc.keys());
    }

    public synchronized void close() {
        try {
            detailsSrc.close();
            storageSrc.close();
        } catch (Exception e) {
            throw new RuntimeException("error closing db");
        }
    }

    /**
     * A wrapper for the iterator needed by {@link DetailsDataStore} conforming to the {@link
     * Iterator} interface.
     *
     * @author Alexandra Roatis
     */
    private class DetailsIteratorWrapper implements Iterator<ByteArrayWrapper> {
        private Iterator<byte[]> sourceIterator;

        /**
         * @implNote Building two wrappers for the same {@link Iterator} will lead to inconsistent
         *     behavior.
         */
        DetailsIteratorWrapper(final Iterator<byte[]> sourceIterator) {
            this.sourceIterator = sourceIterator;
        }

        @Override
        public boolean hasNext() {
            return sourceIterator.hasNext();
        }

        @Override
        public ByteArrayWrapper next() {
            return wrap(sourceIterator.next());
        }
    }
}
