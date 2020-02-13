package org.aion.zero.impl.db;

import static org.aion.crypto.HashUtil.EMPTY_DATA_HASH;
import static org.aion.crypto.HashUtil.h256;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.aion.util.types.ByteArrayWrapper.wrap;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.aion.base.ConstantUtil;
import org.aion.db.impl.ByteArrayKeyValueStore;
import org.aion.mcf.db.InternalVmType;
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
 * Stores contract details as required by the FVM.
 *
 * <p>The encoding produced for storing the contract contains 3 elements:<br>
 * {@code { 0:address, 1:storageRoot, 2:code }}.
 *
 * <p>{@link RLPContractDetails} objects created based on the old encoding:<br>
 * {@code { 0:address, 1: isExternalStorage, 2:storageRoot, 3:storageTrie, 4:code }}<br>
 * are accepted by the decode functionality. When the storage read from disk was in-line the
 * contract will transition to the external storage database.
 */
public class FvmContractDetails implements StoredContractDetails {
    private boolean dirty = false;
    private boolean deleted = false;

    private Map<ByteArrayWrapper, ByteArrayWrapper> codes = new HashMap<>();

    private final ByteArrayKeyValueStore externalStorageSource;

    private final AionAddress address;

    private SecureTrie storageTrie;

    /**
     * Creates an object with attached database access for the external storage.
     *
     * @param externalStorageSource the external storage data source associated with the given
     *     contract address
     * @throws NullPointerException when any of the given parameters are null
     */
    public FvmContractDetails(AionAddress address, ByteArrayKeyValueStore externalStorageSource) {
        Objects.requireNonNull(address, "The address cannot be null!");
        Objects.requireNonNull(externalStorageSource,"The storage data source cannot be null!");
        this.address = address;
        this.externalStorageSource = externalStorageSource;
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
     * Adds the key-value pair to the database unless value is an ByteArrayWrapper whose underlying
     * byte array consists only of zeros. In this case, if key already exists in the database it
     * will be deleted.
     *
     * @param key The key.
     * @param value The value.
     */
    @Override
    public void put(ByteArrayWrapper key, ByteArrayWrapper value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        // The following must be done before making this call:
        // We strip leading zeros of a DataWordImpl but not a DoubleDataWord so that when we call
        // get we can differentiate between the two.

        byte[] data = RLP.encodeElement(value.toBytes());
        storageTrie.update(key.toBytes(), data);

        dirty = true;
    }

    @Override
    public void delete(ByteArrayWrapper key) {
        Objects.requireNonNull(key);

        storageTrie.delete(key.toBytes());

        dirty = true;
    }

    /**
     * Returns the value associated with key if it exists, otherwise returns a DataWordImpl
     * consisting entirely of zero bytes.
     *
     * @param key The key to query.
     * @return the corresponding value or a zero-byte DataWordImpl if no such value.
     */
    @Override
    public ByteArrayWrapper get(ByteArrayWrapper key) {
        byte[] data = storageTrie.get(key.toBytes());
        return (data == null || data.length == 0)
                ? null
                : ByteArrayWrapper.wrap(RLP.decode2(data).get(0).getRLPData());
    }

    public void setVmType(InternalVmType vmType) {
        // nothing to do
    }

    public InternalVmType getVmType() {
        return InternalVmType.FVM;
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
        throw new UnsupportedOperationException("The object graph is an AVM feature. It is not compatible with the FVM.");
    }

    @Override
    public void setObjectGraph(byte[] graph) {
        throw new UnsupportedOperationException("The object graph is an AVM feature. It is not compatible with the FVM.");
    }

    /**
     * Returns the storage hash.
     *
     * @return the storage hash.
     */
    @Override
    public byte[] getStorageHash() {
        return storageTrie.getRootHash();
    }

    /**
     * Decodes an FvmContractDetails object from the RLP encoding and returns a snapshot to the
     * specific point in the blockchain history given by the consensus root hash.
     *
     * @param input the stored encoding representing the contract details
     * @param storageSource the data source for the contract storage data
     * @param consensusRoot the consensus root linking to specific external storage and object graph
     *     data at the point of interest in the blockchain history
     * @return a snapshot of the contract details with the information it contained at the specified
     *     point in the blockchain history
     */
    public static FvmContractDetails decodeAtRoot(RLPContractDetails input, ByteArrayKeyValueStore storageSource, byte[] consensusRoot) {
        Objects.requireNonNull(input, "The contract data for the snapshot cannot be null.");
        Objects.requireNonNull(consensusRoot, "The consensus root for the snapshot cannot be null.");

        // additional null check are performed by the constructor
        FvmContractDetails details = new FvmContractDetails(input.address, storageSource);

        RLPElement code = input.code;
        if (code instanceof RLPList) {
            for (RLPElement e : ((RLPList) code)) {
                details.setCode(e.getRLPData());
            }
        } else {
            details.setCode(code.getRLPData());
        }

        // NOTE: under normal circumstances the VM type is set by the details data store
        // Do not forget to set the vmType value externally during tests!!!
        RLPElement storage = input.storageTrie;

        // load/deserialize storage trie
        if (input.isExternalStorage) { // ensure transition from old encoding
            details.storageTrie = new SecureTrie(details.externalStorageSource, consensusRoot);
        } else {
            details.storageTrie = new SecureTrie(null);
            details.storageTrie.deserialize(storage.getRLPData());
            // switch from in-memory to external storage
            details.storageTrie.getCache().setDB(details.externalStorageSource);
            details.storageTrie.sync();
        }
        if (Arrays.equals(consensusRoot, ConstantUtil.EMPTY_TRIE_HASH)) {
            details.storageTrie = new SecureTrie(details.storageTrie.getCache(), "".getBytes());
        }
        return details;
    }

    /**
     * Returns an rlp encoding of this FvmContractDetails object.
     *
     * <p>The encoding is a list of 3 elements:<br>
     * { 0:address, 1:storageRoot, 2:code }
     *
     * @return an rlp encoding of this.
     */
    @Override
    public byte[] getEncoded() {
        byte[] rlpAddress = RLP.encodeElement(address.toByteArray());
        byte[] rlpStorageRoot = RLP.encodeElement(storageTrie.getRootHash());
        byte[][] codes = new byte[getCodes().size()][];
        int i = 0;
        for (ByteArrayWrapper bytes : this.getCodes().values()) {
            codes[i++] = RLP.encodeElement(bytes.toBytes());
        }
        byte[] rlpCode = RLP.encodeList(codes);

        return RLP.encodeList(rlpAddress, rlpStorageRoot, rlpCode);
    }

    /**
     * Get the address associated with this FvmContractDetails.
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
    public FvmContractDetails copy() {
        FvmContractDetails aionContractDetailsCopy = new FvmContractDetails(this.address, this.externalStorageSource);

        // storage information
        aionContractDetailsCopy.codes = new HashMap<>(codes);
        aionContractDetailsCopy.dirty = this.dirty;
        aionContractDetailsCopy.deleted = this.deleted;
        aionContractDetailsCopy.storageTrie = this.storageTrie.copy();
        return aionContractDetailsCopy;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append("  VM: ").append(InternalVmType.FVM.toString()).append("\n");
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
        return ret.toString();
    }
}
