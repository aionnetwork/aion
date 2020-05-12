package org.aion.zero.impl.db;

import static org.aion.crypto.HashUtil.EMPTY_DATA_HASH;
import static org.aion.crypto.HashUtil.h256;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.aion.mcf.db.ContractDetails;
import org.aion.base.InternalVmType;
import org.aion.util.conversions.Hex;
import org.aion.util.types.ByteArrayWrapper;

/**
 * A short-term container for contract details information used for frequent updates without the
 * overhead of storing data in a secure trie.
 */
public class InnerContractDetails implements ContractDetails {

    private Map<ByteArrayWrapper, ByteArrayWrapper> storage = new HashMap<>();

    public ContractDetails origContract;

    private boolean dirty = false;
    private boolean deleted = false;

    private Map<ByteArrayWrapper, ByteArrayWrapper> codes = new HashMap<>();
    // classes extending this rely on this value starting off as null
    private byte[] objectGraph = null;

    // using the default transaction type to specify undefined VM
    private InternalVmType vmType = InternalVmType.EITHER;

    public InnerContractDetails(ContractDetails origContract) {
        this.origContract = origContract;
        if (origContract != null) {
            this.codes = new HashMap<>(this.origContract.getCodes());
        }
    }

    public static InnerContractDetails copy(InnerContractDetails cache) {
        InnerContractDetails copy = new InnerContractDetails(cache.origContract);
        copy.codes = new HashMap<>(cache.getCodes());
        copy.vmType = cache.vmType;
        if (cache.objectGraph != null) {
            copy.objectGraph = Arrays.copyOf(cache.objectGraph, cache.objectGraph.length);
        }
        copy.storage = new HashMap<>(cache.storage);
        copy.dirty = cache.dirty;
        copy.deleted = cache.deleted;
        return copy;
    }

    @Override
    public byte[] getCode(byte[] codeHash) {
        if (java.util.Arrays.equals(codeHash, EMPTY_DATA_HASH)) {
            return EMPTY_BYTE_ARRAY;
        }
        ByteArrayWrapper code = codes.get(ByteArrayWrapper.wrap(codeHash));
        return code == null ? EMPTY_BYTE_ARRAY : code.toBytes();
    }

    @Override
    public void setCode(byte[] code) {
        if (code == null) {
            return;
        }
        try {
            codes.put(ByteArrayWrapper.wrap(h256(code)), ByteArrayWrapper.wrap(code));
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        dirty = true;
    }

    @Override
    public Map<ByteArrayWrapper, ByteArrayWrapper> getCodes() {
        return codes;
    }

    @Override
    public void appendCodes(Map<ByteArrayWrapper, ByteArrayWrapper> codes) {
        if (!this.codes.keySet().containsAll(codes.keySet())) {
            this.dirty = true;
        }
        this.codes.putAll(codes);
    }

    @Override
    public void markAsDirty() {
        this.dirty = true;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void delete() {
        // TODO: should we set dirty=true?
        this.deleted = true;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Returns {@code true} only if the account has non-empty storage associated with it. Otherwise {@code false}.

     * @return whether the account has non-empty storage or not.
     */
    public boolean hasStorage() {
        return !storage.isEmpty();
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
        dirty = true;
    }

    @Override
    public void delete(ByteArrayWrapper key) {
        Objects.requireNonNull(key);

        storage.put(key, null);
        dirty = true;
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

    /**
     * Sets the transaction type value used to deploy the contract symbolizing the VM that manages
     * the contract.
     *
     * @param vmType the transaction type value used to deploy the contract symbolizing the VM that
     *     manages the contract
     */
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
        vmType = InternalVmType.AVM;
        dirty = true;
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
     * Puts all of the key-value pairs and object graph from this InnerContractDetails into the
     * original contract injected into this class' constructor, transfers over any code and sets the
     * original contract to dirty only if it already is dirty or if this class is dirty, otherwise
     * sets it as clean.
     */
    public void commit() {

        if (origContract == null) {
            return;
        }

        if (origContract instanceof InnerContractDetails) {
            commitTo((InnerContractDetails) origContract);
        } else {
            commitTo((StoredContractDetails) origContract);
        }
    }

    /**
     * Puts all of the storage key-value pairs, code, VM type and object graph from this
     * InnerContractDetails into the given parent contract. Sets the original contract to dirty if
     * this object is dirty.
     *
     * @param parentContract the contract into which this object's changes will be pushed
     * @throws NullPointerException if the given parameter is null
     */
    public void commitTo(InnerContractDetails parentContract) {
        Objects.requireNonNull(parentContract, "Cannot commit to null contract.");

        // passing on the vm type
        if (vmType != InternalVmType.EITHER && vmType != InternalVmType.UNKNOWN) {
            parentContract.setVmType(vmType);
        }

        // passing on the object graph
        if (objectGraph != null) {
            parentContract.setObjectGraph(objectGraph);
        }

        // passing on the storage keys
        parentContract.storage.putAll(this.storage);

        parentContract.appendCodes(getCodes());
        if (this.isDirty()) {
            parentContract.markAsDirty();
        }
    }

    /**
     * Puts all of the storage key-value pairs, code and object graph from this InnerContractDetails
     * into the given parent contract. Sets the original contract to dirty if this object is dirty.
     *
     * @implNote {@link StoredContractDetails} objects do not require setting a VM type since it is
     *     specific to each implementation.
     * @param parentContract the contract into which this object's changes will be pushed
     * @throws NullPointerException if the given parameter is null
     */
    public void commitTo(StoredContractDetails parentContract) {
        Objects.requireNonNull(parentContract, "Cannot commit to null contract.");

        // passing on the object graph
        if (objectGraph != null) {
            parentContract.setObjectGraph(objectGraph);
        }

        // passing on the storage keys
        for (Map.Entry<ByteArrayWrapper, ByteArrayWrapper> entry : storage.entrySet()) {
            if (entry.getValue() != null) {
                parentContract.put(entry.getKey(), entry.getValue());
            } else {
                parentContract.delete(entry.getKey());
            }
        }

        parentContract.appendCodes(getCodes());
        if (this.isDirty()) {
            parentContract.markAsDirty();
        }
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append("  VM: ").append(vmType.toString()).append("\n");
        ret.append("  dirty: ").append(isDirty()).append("\n");

        if (codes.size() == 0) {
            ret.append("  Code: ")
                .append(Hex.toHexString(EMPTY_DATA_HASH))
                .append(" -> {}")
                .append("\n");
        } else {
            ret.append("  Code: ")
                .append(codes.keySet().stream()
                    .map(key -> key + " -> " + codes.get(key))
                    .collect(Collectors.joining(",\n          ", "{ ", " }")))
                .append("\n");
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
        return ret.toString();
    }
}
