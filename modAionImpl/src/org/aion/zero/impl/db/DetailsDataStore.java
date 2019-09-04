package org.aion.zero.impl.db;

import static org.aion.util.types.ByteArrayWrapper.wrap;

import java.util.Iterator;
import java.util.Optional;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.store.JournalPruneDataSource;
import org.aion.mcf.db.InternalVmType;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.slf4j.Logger;

/** Detail data storage , */
public class DetailsDataStore {
    private JournalPruneDataSource storageDSPrune;

    private ByteArrayKeyValueDatabase detailsSrc;
    private ByteArrayKeyValueDatabase storageSrc;
    private ByteArrayKeyValueDatabase graphSrc;
    private Logger log;

    public DetailsDataStore(
            ByteArrayKeyValueDatabase detailsCache,
            ByteArrayKeyValueDatabase storageCache,
            ByteArrayKeyValueDatabase graphCache,
            Logger log) {
        this.detailsSrc = detailsCache;
        this.storageSrc = storageCache;
        this.graphSrc = graphCache;
        this.log = log;
        this.storageDSPrune = new JournalPruneDataSource(storageSrc, log);
    }

    /**
     * Fetches the ContractDetails from the cache, and if it doesn't exist, add to the remove set.
     *
     * @param key the contract address as bytes
     * @param vm the virtual machine used at contract deployment
     * @return
     */
    public synchronized AionContractDetailsImpl get(InternalVmType vm, byte[] key) {

        Optional<byte[]> rawDetails = detailsSrc.get(key);

        // If it doesn't exist in cache or database.
        if (!rawDetails.isPresent()) {
            return null;
        }

        // Found something from cache or database, return it by decoding it.
        AionContractDetailsImpl detailsImpl = new AionContractDetailsImpl(storageDSPrune, graphSrc);
        detailsImpl.setVmType(vm);
        detailsImpl.decode(rawDetails.get()); // We can safely get as we checked
        // if it is present.

        return detailsImpl;
    }

    /** Determine if the contract exists in the database. */
    public synchronized boolean isPresent(byte[] key) {
        Optional<byte[]> rawDetails = detailsSrc.get(key);
        return rawDetails.isPresent();
    }

    public synchronized void update(AionAddress key, AionContractDetailsImpl contractDetails) {

        contractDetails.setAddress(key);
        contractDetails.setObjectGraphSource(graphSrc);

        // Put into cache.
        byte[] rawDetails = contractDetails.getEncoded();
        detailsSrc.put(key.toByteArray(), rawDetails);

        contractDetails.syncStorage();
    }

    public synchronized void remove(byte[] key) {
        ByteArrayWrapper wrappedKey = wrap(key);
        detailsSrc.delete(key);
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
            AionContractDetailsImpl detailsImpl = new AionContractDetailsImpl(storageDSPrune, graphSrc);
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
            graphSrc.close();
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
