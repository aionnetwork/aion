package org.aion.mcf.db;

import java.util.Collection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.aion.base.db.IByteArrayKeyValueStore;
import org.aion.base.db.IContractDetails;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.vm.api.interfaces.Address;

/** Contract details cache implementation. */
public class ContractDetailsCacheImpl extends AbstractContractDetails {

    private Map<ByteArrayWrapper, ByteArrayWrapper> storage = new HashMap<>();

    public IContractDetails origContract;

    public ContractDetailsCacheImpl(IContractDetails origContract) {
        this.origContract = origContract;
        if (origContract != null) {
            if (origContract instanceof AbstractContractDetails) {
                setCodes(((AbstractContractDetails) this.origContract).getCodes());
            } else {
                setCode(origContract.getCode());
            }
        }
    }

    public static ContractDetailsCacheImpl copy(ContractDetailsCacheImpl cache) {
        ContractDetailsCacheImpl copy = new ContractDetailsCacheImpl(cache.origContract);
        copy.setCodes(new HashMap<>(cache.getCodes()));
        copy.storage = new HashMap<>(cache.storage);
        copy.setDirty(cache.isDirty());
        copy.setDeleted(cache.isDeleted());
        copy.prune = cache.prune;
        copy.detailsInMemoryStorageLimit = cache.detailsInMemoryStorageLimit;
        return copy;
    }

    /**
     * Inserts the key-value pair key and value, or if value consists only of zero bytes, deletes
     * any key-value pair whose key is key.
     *
     * @param key The key.
     * @param value The value.
     */
    @Override
    public void put(ByteArrayWrapper key, ByteArrayWrapper value) {
        storage.put(key, value);
        setDirty(true);
    }

    /**
     * Returns the value associated with key if it exists, otherwise returns null.
     *
     * @param key The key to query.
     * @return the associated value or null.
     */
    @Override
    public ByteArrayWrapper get(ByteArrayWrapper key) {
        ByteArrayWrapper value = storage.get(key);
        if (value != null) {
            value = value.copy();
        } else {
            if (origContract == null) {
                return null;
            }
            value = origContract.get(key);
            // TODO: the VM must pad the given ZERO value if expecting a fixed size byte array
            value = (value == null) ? ByteArrayWrapper.ZERO : value;
            storage.put(key.copy(), value.isZero() ? ByteArrayWrapper.ZERO.copy() : value.copy());
        }

        if (value == null || value.isZero()) {
            return null;
        } else {
            return value;
        }
    }

    /**
     * Returns the storage hash.
     *
     * @return the storage hash.
     */
    @Override
    public byte[] getStorageHash() {
        return origContract.getStorageHash();
    }

    /** This method is not supported. */
    @Override
    public void decode(byte[] rlpCode) {
        throw new RuntimeException("Not supported by this implementation.");
    }

    /** This method is not supported. */
    @Override
    public byte[] getEncoded() {
        throw new RuntimeException("Not supported by this implementation.");
    }

    /**
     * Returns a mapping of all the key-value pairs who have keys in the collection keys.
     *
     * @param keys The keys to query for.
     * @return The associated mappings.
     */
    @Override
    public Map<ByteArrayWrapper, ByteArrayWrapper> getStorage(Collection<ByteArrayWrapper> keys) {
        Map<ByteArrayWrapper, ByteArrayWrapper> storage = new HashMap<>();
        if (keys == null) {
            throw new IllegalArgumentException("Input keys can't be null");
        } else {
            for (ByteArrayWrapper key : keys) {
                ByteArrayWrapper value = get(key);

                // we check if the value is not null,
                // cause we keep all historical keys
                if ((value != null) && (!value.isZero())) {
                    storage.put(key, value);
                }
            }
        }

        return storage;
    }

    /**
     * Sets the storage to contain the specified keys and values. This method creates pairings of
     * the keys and values by mapping the i'th key in storageKeys to the i'th value in
     * storageValues.
     *
     * @param storageKeys The keys.
     * @param storageValues The values.
     */
    @Override
    public void setStorage(
            List<ByteArrayWrapper> storageKeys, List<ByteArrayWrapper> storageValues) {

        for (int i = 0; i < storageKeys.size(); ++i) {

            ByteArrayWrapper key = storageKeys.get(i);
            ByteArrayWrapper value = storageValues.get(i);

            put(key, value);
        }
    }

    /**
     * Sets the storage to contain the specified key-value mappings.
     *
     * @param storage The specified mappings.
     */
    @Override
    public void setStorage(Map<ByteArrayWrapper, ByteArrayWrapper> storage) {
        for (Map.Entry<ByteArrayWrapper, ByteArrayWrapper> entry : storage.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Get the address associated with this ContractDetailsCacheImpl.
     *
     * @return the associated address.
     */
    @Override
    public Address getAddress() {
        return (origContract == null) ? null : origContract.getAddress();
    }

    /**
     * Sets the address associated with this ContractDetailsCacheImpl.
     *
     * @param address The address to set.
     */
    @Override
    public void setAddress(Address address) {
        if (origContract != null) {
            origContract.setAddress(address);
        }
    }

    /** Syncs the storage trie. */
    @Override
    public void syncStorage() {
        if (origContract != null) {
            origContract.syncStorage();
        }
    }

    /**
     * Puts all of the key-value pairs in this ContractDetailsCacheImple into the original contract
     * injected into this class' constructor, transfers over any code and sets the original contract
     * to dirty only if it already is dirty or if this class is dirty, otherwise sets it as clean.
     */
    public void commit() {

        if (origContract == null) {
            return;
        }

        for (ByteArrayWrapper key : storage.keySet()) {
            origContract.put(key, storage.get(key));
        }

        if (origContract instanceof AbstractContractDetails) {
            ((AbstractContractDetails) origContract).appendCodes(getCodes());
        } else {
            origContract.setCode(getCode());
        }
        origContract.setDirty(this.isDirty() || origContract.isDirty());
    }

    /** This method is not supported. */
    @Override
    public IContractDetails getSnapshotTo(byte[] hash) {
        throw new UnsupportedOperationException("No snapshot option during cache state");
    }

    /** This method is not supported. */
    @Override
    public void setDataSource(IByteArrayKeyValueStore dataSource) {
        throw new UnsupportedOperationException("Can't set datasource in cache implementation.");
    }

    /**
     * Returns a sufficiently deep copy of this contract details object.
     *
     * <p>If this contract details object's "original contract" is of type {@link
     * ContractDetailsCacheImpl}, and the same is true for all of its ancestors, then this method
     * will return a perfectly deep copy of this contract details object.
     *
     * <p>Otherwise, the "original contract" copy will retain some references that are also held by
     * the object it is a copy of. In particular, the following references will not be copied:
     *
     * <p>- The external storage data source. - The previous root of the trie will pass its original
     * object reference if this root is not of type {@code byte[]}. - The current root of the trie
     * will pass its original object reference if this root is not of type {@code byte[]}. - Each
     * {@link org.aion.rlp.Value} object reference held by each of the {@link
     * org.aion.mcf.trie.Node} objects in the underlying cache.
     *
     * @return A copy of this object.
     */
    @Override
    public ContractDetailsCacheImpl copy() {
        // TODO: better to move this check into all constructors instead.
        if (this == this.origContract) {
            throw new IllegalStateException(
                    "Cannot copy a ContractDetailsCacheImpl whose original contract is itself!");
        }

        IContractDetails originalContractCopy =
                (this.origContract == null) ? null : this.origContract.copy();
        ContractDetailsCacheImpl contractDetailsCacheCopy =
                new ContractDetailsCacheImpl(originalContractCopy);
        contractDetailsCacheCopy.storage = getDeepCopyOfStorage();
        contractDetailsCacheCopy.prune = this.prune;
        contractDetailsCacheCopy.detailsInMemoryStorageLimit = this.detailsInMemoryStorageLimit;
        contractDetailsCacheCopy.setCodes(getDeepCopyOfCodes());
        contractDetailsCacheCopy.setDirty(this.isDirty());
        contractDetailsCacheCopy.setDeleted(this.isDeleted());
        return contractDetailsCacheCopy;
    }

    private Map<ByteArrayWrapper, byte[]> getDeepCopyOfCodes() {
        Map<ByteArrayWrapper, byte[]> originalCodes = this.getCodes();

        if (originalCodes == null) {
            return null;
        }

        Map<ByteArrayWrapper, byte[]> copyOfCodes = new HashMap<>();
        for (Entry<ByteArrayWrapper, byte[]> codeEntry : originalCodes.entrySet()) {

            ByteArrayWrapper keyWrapper = null;
            if (codeEntry.getKey() != null) {
                byte[] keyBytes = codeEntry.getKey().getData();
                keyWrapper = new ByteArrayWrapper(Arrays.copyOf(keyBytes, keyBytes.length));
            }

            byte[] copyOfValue =
                    (codeEntry.getValue() == null)
                            ? null
                            : Arrays.copyOf(codeEntry.getValue(), codeEntry.getValue().length);
            copyOfCodes.put(keyWrapper, copyOfValue);
        }
        return copyOfCodes;
    }

    private Map<ByteArrayWrapper, ByteArrayWrapper> getDeepCopyOfStorage() {
        if (this.storage == null) {
            return null;
        }

        Map<ByteArrayWrapper, ByteArrayWrapper> storageCopy = new HashMap<>();
        for (Entry<ByteArrayWrapper, ByteArrayWrapper> storageEntry : this.storage.entrySet()) {
            ByteArrayWrapper keyWrapper = null;
            ByteArrayWrapper valueWrapper = null;

            if (storageEntry.getKey() != null) {
                byte[] keyBytes = storageEntry.getKey().getData();
                keyWrapper = new ByteArrayWrapper(Arrays.copyOf(keyBytes, keyBytes.length));
            }

            if (storageEntry.getValue() != null) {
                byte[] valueBytes = storageEntry.getValue().getData();
                valueWrapper = new ByteArrayWrapper(Arrays.copyOf(valueBytes, valueBytes.length));
            }

            storageCopy.put(keyWrapper, valueWrapper);
        }
        return storageCopy;
    }
}
