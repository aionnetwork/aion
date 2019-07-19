package org.aion.zero.impl.db;

import static org.aion.crypto.HashUtil.h256;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.aion.db.impl.ByteArrayKeyValueStore;
import org.aion.mcf.db.ContractDetails;
import org.aion.mcf.db.InternalVmType;
import org.aion.types.AionAddress;
import org.aion.util.conversions.Hex;
import org.aion.util.types.ByteArrayWrapper;

/** Contract details cache implementation. */
public class ContractDetailsCacheImpl extends AbstractContractDetails {

    private Map<ByteArrayWrapper, ByteArrayWrapper> storage = new HashMap<>();

    public ContractDetails origContract;

    public ContractDetailsCacheImpl(ContractDetails origContract) {
        this.origContract = origContract;
        if (origContract != null) {
            if (origContract instanceof AbstractContractDetails) {
                setCodes(((AbstractContractDetails) this.origContract).getCodes());
            } else {
                setCode(origContract.getCode());
            }
            this.origContract.setTransformedCode(origContract.getTransformedCode());
        }
    }

    public static ContractDetailsCacheImpl copy(ContractDetailsCacheImpl cache) {
        ContractDetailsCacheImpl copy = new ContractDetailsCacheImpl(cache.origContract);
        copy.setCodes(new HashMap<>(cache.getCodes()));
        copy.vmType = cache.vmType;
        if (cache.objectGraph != null) {
            copy.objectGraph = Arrays.copyOf(cache.objectGraph, cache.objectGraph.length);
        }
        copy.storage = new HashMap<>(cache.storage);
        copy.setDirty(cache.isDirty());
        copy.setDeleted(cache.isDeleted());
        copy.prune = cache.prune;
        copy.detailsInMemoryStorageLimit = cache.detailsInMemoryStorageLimit;
        if (cache.getTransformedCode() != null) {
            copy.setTransformedCode(cache.getTransformedCode().clone());
        }
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
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        storage.put(key, value);
        setDirty(true);
    }

    @Override
    public void delete(ByteArrayWrapper key) {
        Objects.requireNonNull(key);

        storage.put(key, null);
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
        ByteArrayWrapper value;

        // go to parent if not locally stored
        if (!storage.containsKey(key)) {
            if (origContract == null) {
                return null;
            }
            value = origContract.get(key);

            // save a copy to local storage
            if (value != null) {
                storage.put(key.copy(), value.copy());
            } else {
                storage.put(key.copy(), null);
            }
        } else { // check local storage
            value = storage.get(key);

            if (value != null) {
                value = value.copy();
            }
        }
        return value;
    }

    public void setVmType(InternalVmType vmType) {
        if (this.vmType != vmType && vmType != InternalVmType.EITHER) {
            this.vmType = vmType;
        }
    }

    public InternalVmType getVmType() {
        if (vmType == InternalVmType.EITHER && origContract != null) {
            // not necessary to set as dirty
            vmType = origContract.getVmType();
        }
        return vmType;
    }

    @Override
    public byte[] getObjectGraph() {
        if (objectGraph == null) {
            if (origContract == null) {
                return null;
            } else {
                objectGraph = origContract.getObjectGraph();
            }
        }
        return objectGraph;
    }

    @Override
    public void setObjectGraph(byte[] graph) {
        Objects.requireNonNull(graph);

        objectGraph = graph;
        setDirty(true);
    }

    @Override
    public byte[] getTransformedCode() {
        if (performCode == null && this.origContract != null) {
            performCode = origContract.getTransformedCode();
        }
        return performCode;
    }

    /**
     * Returns the storage hash.
     *
     * @return the storage hash.
     */
    @Override
    public byte[] getStorageHash() {
        return origContract == null ? null : origContract.getStorageHash();
    }

    /** This method is not supported. */
    @Override
    public void decode(byte[] rlpCode) {
        throw new RuntimeException("Not supported by this implementation.");
    }

    /** This method is not supported. */
    @Override
    public void decode(byte[] rlpCode, boolean fastCheck) {
        throw new RuntimeException("Not supported by this implementation.");
    }

    /** This method is not supported. */
    @Override
    public byte[] getEncoded() {
        throw new RuntimeException("Not supported by this implementation.");
    }

    /**
     * Get the address associated with this ContractDetailsCacheImpl.
     *
     * @return the associated address.
     */
    @Override
    public AionAddress getAddress() {
        return (origContract == null) ? null : origContract.getAddress();
    }

    /**
     * Sets the address associated with this ContractDetailsCacheImpl.
     *
     * @param address The address to set.
     */
    @Override
    public void setAddress(AionAddress address) {
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
     * Puts all of the key-value pairs and object graph from this ContractDetailsCacheImpl into the
     * original contract injected into this class' constructor, transfers over any code and sets the
     * original contract to dirty only if it already is dirty or if this class is dirty, otherwise
     * sets it as clean.
     */
    public void commit() {

        if (origContract == null) {
            return;
        }

        // passing on the vm type
        if (vmType != InternalVmType.EITHER && vmType != InternalVmType.UNKNOWN) {
            origContract.setVmType(vmType);
        }

        // passing on the object graph
        if (objectGraph != null) {
            origContract.setObjectGraph(objectGraph);
        }

        // passing on the storage keys
        for (ByteArrayWrapper key : storage.keySet()) {
            ByteArrayWrapper value = storage.get(key);
            if (value != null) {
                origContract.put(key, storage.get(key));
            } else {
                origContract.delete(key);
            }
        }

        if (origContract instanceof AbstractContractDetails) {
            ((AbstractContractDetails) origContract).appendCodes(getCodes());
        } else {
            origContract.setCode(getCode());
        }

        origContract.setTransformedCode(getTransformedCode());
        origContract.setDirty(this.isDirty() || origContract.isDirty());
    }

    /** This method is not supported. */
    @Override
    public ContractDetails getSnapshotTo(byte[] hash, InternalVmType vm) {
        throw new UnsupportedOperationException("No snapshot option during cache state");
    }

    /** This method is not supported. */
    @Override
    public void setDataSource(ByteArrayKeyValueStore dataSource) {
        throw new UnsupportedOperationException("Can't set datasource in cache implementation.");
    }

    /** This method is not supported. */
    @Override
    public void setObjectGraphSource(ByteArrayKeyValueStore objectGraphSource) {
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

        ContractDetails originalContractCopy =
                (this.origContract == null) ? null : this.origContract.copy();
        ContractDetailsCacheImpl copy = new ContractDetailsCacheImpl(originalContractCopy);
        copy.vmType = this.vmType;
        if (this.objectGraph != null) {
            copy.objectGraph = Arrays.copyOf(this.objectGraph, this.objectGraph.length);
        }
        copy.storage = getDeepCopyOfStorage();
        copy.prune = this.prune;
        copy.detailsInMemoryStorageLimit = this.detailsInMemoryStorageLimit;
        copy.setCodes(getDeepCopyOfCodes());
        copy.setDirty(this.isDirty());
        copy.setDeleted(this.isDeleted());
        if (this.getTransformedCode() != null) {
            copy.setTransformedCode(this.getTransformedCode().clone());
        }
        return copy;
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

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append("  VM: ").append(vmType.toString()).append("\n");
        ret.append("  dirty: ").append(isDirty()).append("\n");

        byte[] code = getCode();
        if (code != null) {
            ret.append("  Code: ")
                    .append(
                            (code.length < 2
                                    ? Hex.toHexString(getCode())
                                    : code.length + " versions"))
                    .append("\n");
        } else {
            ret.append("  Code: null\n");
        }

        byte[] storage = getStorageHash();
        if (storage != null) {
            ret.append("  Storage: ").append(Hex.toHexString(storage)).append("\n");
        } else {
            ret.append("  Storage: null\n");
        }

        ret.append("  objectGraphHash: ")
                .append(objectGraph == null ? "null" : Hex.toHexString(h256(objectGraph)))
                .append("\n");
        if (origContract != null && origContract instanceof AionContractDetailsImpl) {
            byte[] hash = ((AionContractDetailsImpl) origContract).getConcatenatedStorageHash();
            if (hash != null) {
                ret.append("  concatenatedStorageHash: ")
                        .append(Hex.toHexString(hash))
                        .append("\n");
            }
        }
        return ret.toString();
    }
}
