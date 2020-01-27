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
     * Fetches the ContractDetails with the given root.
     *
     * @param vm the virtual machine used at contract deployment
     * @param key the contract address as bytes
     * @param storageRoot the requested storage root
     * @return a snapshot of the contract details with the requested root
     */
    public synchronized AionContractDetailsImpl getSnapshot(InternalVmType vm, byte[] key, byte[] storageRoot) {
        Optional<byte[]> rawDetails = detailsSrc.get(key);

        if (rawDetails.isPresent()) {
            // decode raw details and return snapshot
            AionContractDetailsImpl detailsImpl = new AionContractDetailsImpl(storageDSPrune, graphSrc);
            detailsImpl.setVmType(vm);
            detailsImpl.decode(rawDetails.get());
            return detailsImpl.getSnapshotTo(storageRoot, vm);
        } else {
            return null;
        }
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
        detailsSrc.delete(key);
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
