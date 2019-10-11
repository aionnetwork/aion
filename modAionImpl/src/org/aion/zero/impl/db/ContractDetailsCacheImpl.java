package org.aion.zero.impl.db;

import static org.aion.crypto.HashUtil.EMPTY_DATA_HASH;
import static org.aion.crypto.HashUtil.h256;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.aion.mcf.db.ContractDetails;
import org.aion.mcf.db.InternalVmType;
import org.aion.types.AionAddress;
import org.aion.util.conversions.Hex;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.trie.Node;

/** Contract details cache implementation. */
public class ContractDetailsCacheImpl implements ContractDetails {

    private Map<ByteArrayWrapper, ByteArrayWrapper> storage = new HashMap<>();

    public ContractDetails origContract;

    private boolean dirty = false;
    private boolean deleted = false;

    private Map<ByteArrayWrapper, byte[]> codes = new HashMap<>();
    private byte[] transformedCode;
    // classes extending this rely on this value starting off as null
    private byte[] objectGraph = null;

    // using the default transaction type to specify undefined VM
    private InternalVmType vmType = InternalVmType.EITHER;

    public ContractDetailsCacheImpl(ContractDetails origContract) {
        this.origContract = origContract;
        if (origContract != null) {
            setCodes(this.origContract.getCodes());
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
        copy.deleted = cache.deleted;
        if (cache.getTransformedCode() != null) {
            copy.setTransformedCode(cache.getTransformedCode().clone());
        }
        return copy;
    }

    @Override
    public byte[] getCode() {
        return codes.size() == 0 ? EMPTY_BYTE_ARRAY : codes.values().iterator().next();
    }

    @Override
    public byte[] getCode(byte[] codeHash) {
        if (java.util.Arrays.equals(codeHash, EMPTY_DATA_HASH)) {
            return EMPTY_BYTE_ARRAY;
        }
        byte[] code = codes.get(ByteArrayWrapper.wrap(codeHash));
        return code == null ? EMPTY_BYTE_ARRAY : code;
    }

    @Override
    public void setTransformedCode(byte[] transformedCode) {
        // ensures that the object is not set to dirty when copied
        if (!Arrays.equals(this.transformedCode, transformedCode)) {
            this.transformedCode = transformedCode;
            setDirty(true);
        }
    }

    @Override
    public void setCode(byte[] code) {
        if (code == null) {
            return;
        }
        try {
            codes.put(ByteArrayWrapper.wrap(h256(code)), code);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        setDirty(true);
    }

    @Override
    public Map<ByteArrayWrapper, byte[]> getCodes() {
        return codes;
    }

    private void setCodes(Map<ByteArrayWrapper, byte[]> codes) {
        this.codes = new HashMap<>(codes);
    }

    @Override
    public void appendCodes(Map<ByteArrayWrapper, byte[]> codes) {
        this.codes.putAll(codes);
    }

    @Override
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void delete() {
        this.deleted = true;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public Map<ByteArrayWrapper, ByteArrayWrapper> getStorage(Collection<ByteArrayWrapper> keys) {
        Map<ByteArrayWrapper, ByteArrayWrapper> storage = new HashMap<>();

        if (keys == null) {
            throw new IllegalArgumentException("Input keys cannot be null");
        } else {
            for (ByteArrayWrapper key : keys) {
                ByteArrayWrapper value = get(key);

                // we check if the value is not null,
                // cause we keep all historical keys
                if (value != null) {
                    storage.put(key, value);
                }
            }
        }

        return storage;
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
                storage.put(key, value);
            } else {
                storage.put(key, null);
            }
        } else { // check local storage
            value = storage.get(key);
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
        if (transformedCode == null && this.origContract != null) {
            transformedCode = origContract.getTransformedCode();
        }
        return transformedCode;
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

        origContract.appendCodes(getCodes());

        origContract.setTransformedCode(getTransformedCode());
        origContract.setDirty(this.isDirty() || origContract.isDirty());
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
     * Node} objects in the underlying cache.
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
        copy.setCodes(getDeepCopyOfCodes());
        copy.setDirty(this.isDirty());
        copy.deleted = this.deleted;
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

            byte[] copyOfValue =
                    (codeEntry.getValue() == null)
                            ? null
                            : Arrays.copyOf(codeEntry.getValue(), codeEntry.getValue().length);
            // the ByteArrayWrapper is immutable
            copyOfCodes.put(codeEntry.getKey(), copyOfValue);
        }
        return copyOfCodes;
    }

    private Map<ByteArrayWrapper, ByteArrayWrapper> getDeepCopyOfStorage() {
        if (this.storage == null) {
            return null;
        }

        Map<ByteArrayWrapper, ByteArrayWrapper> storageCopy = new HashMap<>();
        for (Entry<ByteArrayWrapper, ByteArrayWrapper> storageEntry : this.storage.entrySet()) {
            // the ByteArrayWrapper is immutable
            storageCopy.put(storageEntry.getKey(), storageEntry.getValue());
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
