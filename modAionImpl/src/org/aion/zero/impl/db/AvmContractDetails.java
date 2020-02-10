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
import java.util.Optional;
import java.util.stream.Collectors;
import org.aion.base.ConstantUtil;
import org.aion.db.impl.ByteArrayKeyValueStore;
import org.aion.mcf.db.InternalVmType;
import org.aion.precompiled.ContractInfo;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.types.AionAddress;
import org.aion.util.conversions.Hex;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.db.DetailsDataStore.RLPContractDetails;
import org.aion.zero.impl.trie.Node;
import org.aion.zero.impl.trie.SecureTrie;

/**
 * Stores contract details as required by the AVM with support for the use of the object graph and
 * the concatenated storage root value.
 *
 * <p>The encoding produced for storing the contract contains 3 elements:<br>
 * {@code { 0:address, 1:storageRoot, 2:code }}.
 *
 * <p>{@link RLPContractDetails} objects created based on the old encoding:<br>
 * {@code { 0:address, 1: isExternalStorage, 2:storageRoot, 3:storageTrie, 4:code }}<br>
 * are accepted by the decode functionality. When the storage read from disk was in-line the
 * contract will transition to the external storage database.
 */
public class AvmContractDetails implements StoredContractDetails {
    // identifies the contract
    private final AionAddress address;

    // external databases used for storage and the object graph
    private final ByteArrayKeyValueStore externalStorageSource;
    private final ByteArrayKeyValueStore objectGraphSource;

    // code variants for the same address on different chains
    private Map<ByteArrayWrapper, ByteArrayWrapper> codes = new HashMap<>();

    // attributes that record the current state of the contract
    private boolean dirty = false;
    private boolean deleted = false;
    private ByteArrayWrapper objectGraph = null;
    private byte[] objectGraphHash = EMPTY_DATA_HASH;
    private SecureTrie storageTrie;

    /**
     * Creates an object with attached database access for the external storage and object graph.
     *
     * @param externalStorageSource the external storage data source associated with the given
     *     contract address
     * @param objectGraphSource the object graph data source associated with the given contract
     *     address
     * @throws NullPointerException when any of the given parameters are null.
     * @throws IllegalArgumentException when the contract address belongs to a precompiled contract.
     */
    public AvmContractDetails(AionAddress address, ByteArrayKeyValueStore externalStorageSource, ByteArrayKeyValueStore objectGraphSource) {
        Objects.requireNonNull(address,"The address cannot be null!");
        Objects.requireNonNull(externalStorageSource,"The storage data source cannot be null!");
        Objects.requireNonNull(objectGraphSource,"The graph data source cannot be null!");
        if (ContractInfo.isPrecompiledContract(address)) {
            throw new IllegalArgumentException("The address cannot be a precompiled contract!");
        }
        this.address = address;
        this.externalStorageSource = externalStorageSource;
        this.objectGraphSource = objectGraphSource;
        this.storageTrie = new SecureTrie(this.externalStorageSource);
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
    public Map<ByteArrayWrapper, ByteArrayWrapper> getCodes() {
        return codes;
    }

    private void setCodes(Map<ByteArrayWrapper,ByteArrayWrapper> codes) {
        this.codes = new HashMap<>(codes);
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

    public void delete() {
        // TODO: should we set dirty=true?
        this.deleted = true;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    /** @throws NullPointerException when any of the given keys are null. */
    @Override
    public Map<ByteArrayWrapper, ByteArrayWrapper> getStorage(Collection<ByteArrayWrapper> keys) {
        Objects.requireNonNull(keys, "The keys cannot be null.");
        if (keys.contains(null)) {
            throw new NullPointerException("The keys cannot be null.");
        }

        Map<ByteArrayWrapper, ByteArrayWrapper> storage = new HashMap<>();
        for (ByteArrayWrapper key : keys) {
            ByteArrayWrapper value = get(key);

            // we check if the value is not null,
            // cause we keep all historical keys
            if (value != null) {
                storage.put(key, value);
            }
        }
        return storage;
    }

    /**
     * Adds the key-value pair to the database unless value is an ByteArrayWrapper whose underlying
     * byte array consists only of zeros. In this case, if key already exists in the database it
     * will be deleted.
     *
     * @param key The key.
     * @param value The value.
     * @throws NullPointerException when any of the given parameters are null.
     */
    @Override
    public void put(ByteArrayWrapper key, ByteArrayWrapper value) {
        Objects.requireNonNull(key, "The key cannot be null.");
        Objects.requireNonNull(value, "The value cannot be null.");

        byte[] data = RLP.encodeElement(value.toBytes());
        storageTrie.update(key.toBytes(), data);

        dirty = true;
    }

    /** @throws NullPointerException when the given parameters is null. */
    @Override
    public void delete(ByteArrayWrapper key) {
        Objects.requireNonNull(key, "The key cannot be null.");

        storageTrie.delete(key.toBytes());

        dirty = true;
    }

    /**
     * Returns the value associated with key if it exists, otherwise returns a DataWordImpl
     * consisting entirely of zero bytes.
     *
     * @param key The key to query.
     * @return the corresponding value or a zero-byte DataWordImpl if no such value.
     * @throws NullPointerException when the given parameters is null.
     */
    @Override
    public ByteArrayWrapper get(ByteArrayWrapper key) {
        Objects.requireNonNull(key, "The key cannot be null.");
        byte[] data = storageTrie.get(key.toBytes());
        return (data == null || data.length == 0)
                ? null
                : ByteArrayWrapper.wrap(RLP.decode2(data).get(0).getRLPData());
    }

    public void setVmType(InternalVmType vmType) {
        // nothing to do, always AVM
    }

    public InternalVmType getVmType() {
        return InternalVmType.AVM;
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
    public byte[] getObjectGraph() {
        if (objectGraph == null) {
            // the object graph was not stored yet
            if (Arrays.equals(objectGraphHash, EMPTY_DATA_HASH)) {
                return EMPTY_BYTE_ARRAY;
            } else {
                // note: the enforced use of optional is rather cumbersome here
                Optional<byte[]> dbVal = objectGraphSource.get(objectGraphHash);
                objectGraph = dbVal.map(ByteArrayWrapper::wrap).orElse(null);
            }
        }

        return objectGraph == null ? EMPTY_BYTE_ARRAY : objectGraph.toBytes();
    }
    /** @throws NullPointerException when the given parameters is null. */
    @Override
    public void setObjectGraph(final byte[] graph) {
        Objects.requireNonNull(graph);

        objectGraph = ByteArrayWrapper.wrap(graph);
        objectGraphHash = h256(graph);

        dirty = true;
    }

    /**
     * Returns the storage hash.
     *
     * @return the storage hash.
     */
    @Override
    public byte[] getStorageHash() {
        return computeAvmStorageHash();
    }

    /**
     * Computes the concatenated storage hash used by the AVM by concatenating 1: the storage root
     * and 2: the object graph and hashing the result.
     */
    private byte[] computeAvmStorageHash() {
        byte[] storageRoot = storageTrie.getRootHash();
        byte[] graphHash = objectGraphHash;
        byte[] concatenated = new byte[storageRoot.length + graphHash.length];
        System.arraycopy(storageRoot, 0, concatenated, 0, storageRoot.length);
        System.arraycopy(graphHash, 0, concatenated, storageRoot.length, graphHash.length);
        return h256(concatenated);
    }

    /**
     * Decodes an AvmContractDetails object from the RLP encoding and returns a snapshot to the
     * specific point in the blockchain history given by the consensus root hash.
     *
     * @param input the stored encoding representing the contract details
     * @param storageSource the data source for the contract storage data
     * @param objectGraphSource the data source for the object graph
     * @param consensusRoot the consensus root linking to specific external storage and object graph
     *     data at the point of interest in the blockchain history
     * @return a snapshot of the contract details with the information it contained at the specified
     *     point in the blockchain history
     */
    public static AvmContractDetails decodeAtRoot(RLPContractDetails input, ByteArrayKeyValueStore storageSource, ByteArrayKeyValueStore objectGraphSource, byte[] consensusRoot) {
        Objects.requireNonNull(input, "The contract data for the snapshot cannot be null.");
        // additional null check are performed by the constructor
        AvmContractDetails details = new AvmContractDetails(input.address, storageSource, objectGraphSource);

        RLPElement code = input.code;
        if (code instanceof RLPList) {
            for (RLPElement e : ((RLPList) code)) {
                details.setCode(e.getRLPData());
            }
        } else {
            details.setCode(code.getRLPData());
        }

        // The root is the concatenated storage hash.
        // It points to the external storage hash and the object graph hash.
        RLPElement storage = input.storageTrie;

        // Instantiates the storage interpreting the storage root according to the VM specification.
        byte[] storageRootHash;
        Optional<byte[]> concatenatedData = details.objectGraphSource.get(consensusRoot);
        if (concatenatedData.isPresent()) {
            RLPList data = RLP.decode2(concatenatedData.get());
            if (!(data.get(0) instanceof RLPList)) {
                throw new IllegalArgumentException("Invalid concatenated storage for AVM.");
            }
            RLPList pair = (RLPList) data.get(0);
            if (pair.size() != 2) {
                throw new IllegalArgumentException("Invalid concatenated storage for AVM.");
            }

            storageRootHash = pair.get(0).getRLPData();
            details.objectGraphHash = pair.get(1).getRLPData();
        } else {
            // An AVM contract must always have the object graph when written to disk.
            // As a result the concatenated storage hash cannot be missing from the database.
            throw new IllegalArgumentException("Invalid concatenated storage for AVM.");
        }

        // load/deserialize storage trie
        if (input.isExternalStorage) { // ensure transition from old encoding
            details.storageTrie = new SecureTrie(details.externalStorageSource, storageRootHash);
        } else {
            details.storageTrie = new SecureTrie(null);
            details.storageTrie.deserialize(storage.getRLPData());
            // switch from in-memory to external storage
            details.storageTrie.getCache().setDB(details.externalStorageSource);
            details.storageTrie.sync();
        }

        if (Arrays.equals(storageRootHash, ConstantUtil.EMPTY_TRIE_HASH)) {
            details.storageTrie = new SecureTrie(details.storageTrie.getCache(), "".getBytes());
        }
        return details;
    }

    /**
     * Returns an rlp encoding of this AvmContractDetails object.
     *
     * <p>The encoding is a list of 3 elements:<br>
     * { 0:address, 1:storageRoot, 2:code }
     *
     * @return an rlp encoding of this.
     */
    @Override
    public byte[] getEncoded() {
        byte[] rlpAddress = RLP.encodeElement(address.toByteArray());
        byte[] rlpStorageRoot = RLP.encodeElement(computeAvmStorageHash());
        byte[][] codesArray = new byte[codes.size()][];
        int i = 0;
        for (ByteArrayWrapper bytes : codes.values()) {
            codesArray[i++] = RLP.encodeElement(bytes.toBytes());
        }
        byte[] rlpCode = RLP.encodeList(codesArray);

        return RLP.encodeList(rlpAddress, rlpStorageRoot, rlpCode);
    }

    /**
     * Get the address associated with this AvmContractDetails.
     *
     * @return the associated address.
     */
    @Override
    public AionAddress getAddress() {
        return address;
    }

    /** Syncs the storage trie. */
    @Override
    public void syncStorage() {
        byte[] graph = getObjectGraph();
        if (!Arrays.equals(graph, EMPTY_BYTE_ARRAY)) {
            objectGraphSource.put(objectGraphHash, graph);
        }
        objectGraphSource.put(computeAvmStorageHash(), RLP.encodeList(RLP.encodeElement(storageTrie.getRootHash()), RLP.encodeElement(objectGraphHash)));

        storageTrie.sync();
    }

    /**
     * Returns a sufficiently deep copy of this contract details object.
     *
     * <p>The copy is not completely deep. The following object references will be passed on from
     * this object to the copy:
     *
     * <p>- The external storage data source: the copy will back-end on this same source. - The
     * previous root of the trie will pass its original object reference if this root is not of type
     * {@code byte[]}. - The current root of the trie will pass its original object reference if
     * this root is not of type {@code byte[]}. - Each {@link org.aion.rlp.Value} object reference
     * held by each of the {@link Node} objects in the underlying cache.
     *
     * @return A copy of this object.
     */
    @Override
    public AvmContractDetails copy() {
        AvmContractDetails aionContractDetailsCopy = new AvmContractDetails(this.address, this.externalStorageSource, this.objectGraphSource);

        // object graph information
        aionContractDetailsCopy.objectGraph = objectGraph; // can pass reference since the class is immutable
        aionContractDetailsCopy.objectGraphHash =
                Arrays.equals(objectGraphHash, EMPTY_DATA_HASH)
                        ? EMPTY_DATA_HASH
                        : Arrays.copyOf(this.objectGraphHash, this.objectGraphHash.length);

        aionContractDetailsCopy.codes = new HashMap<>(codes);
        aionContractDetailsCopy.dirty = this.dirty;
        aionContractDetailsCopy.deleted = this.deleted;
        aionContractDetailsCopy.storageTrie = (this.storageTrie == null) ? null : this.storageTrie.copy();
        return aionContractDetailsCopy;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append("  VM: ").append(InternalVmType.AVM).append("\n");
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

        ret.append("  objectGraphHash: ").append(Hex.toHexString(objectGraphHash)).append("\n");

        return ret.toString();
    }
}
