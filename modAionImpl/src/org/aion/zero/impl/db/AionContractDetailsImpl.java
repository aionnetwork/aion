package org.aion.zero.impl.db;

import static org.aion.crypto.HashUtil.EMPTY_DATA_HASH;
import static org.aion.crypto.HashUtil.h256;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.aion.util.types.ByteArrayWrapper.wrap;

import com.google.common.annotations.VisibleForTesting;
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

public class AionContractDetailsImpl implements StoredContractDetails {
    private boolean dirty = false;
    private boolean deleted = false;

    /** Indicates the maximum storage size before shifting to the storage database. */
    @VisibleForTesting
    static int detailsInMemoryStorageLimit = 64 * 1024;

    private Map<ByteArrayWrapper, ByteArrayWrapper> codes = new HashMap<>();
    // classes extending this rely on this value starting off as null
    private byte[] objectGraph = null;

    private final ByteArrayKeyValueStore externalStorageSource;
    private final ByteArrayKeyValueStore objectGraphSource;

    private final AionAddress address;

    private SecureTrie storageTrie = new SecureTrie(null);

    private boolean externalStorage;

    private byte[] objectGraphHash = EMPTY_DATA_HASH;
    private byte[] concatenatedStorageHash = EMPTY_DATA_HASH;

    public AionContractDetailsImpl(AionAddress address) {
        this(address, null, null);
    }

    /**
     * Creates a object with attached database access for the external storage and object graph.
     *
     * @param externalStorageSource the external storage data source associated with the given
     *     contract address
     * @param objectGraphSource the object graph data source associated with the given contract
     *     address
     */
    public AionContractDetailsImpl(AionAddress address, ByteArrayKeyValueStore externalStorageSource, ByteArrayKeyValueStore objectGraphSource) {
        if (address == null) {
            throw new IllegalArgumentException("Address can not be null!");
        } else {
            this.address = address;
        }
        this.externalStorageSource = externalStorageSource;
        this.objectGraphSource = objectGraphSource;
    }

    private AionContractDetailsImpl(AionAddress address, SecureTrie storageTrie, Map<ByteArrayWrapper, ByteArrayWrapper> codes, ByteArrayKeyValueStore externalStorageSource, ByteArrayKeyValueStore objectGraphSource) {
        this(address, externalStorageSource, objectGraphSource);
        this.storageTrie = storageTrie;
        this.codes = new HashMap<>(codes);
    }

    @VisibleForTesting
    public boolean isExternalStorage() {
        return externalStorage;
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
        if (objectGraph == null) {
            // the object graph was not stored yet
            if (Arrays.equals(objectGraphHash, EMPTY_DATA_HASH)) {
                return EMPTY_BYTE_ARRAY;
            } else if (objectGraphSource != null) {
                // note: the enforced use of optional is rather cumbersome here
                Optional<byte[]> dbVal = objectGraphSource.get(objectGraphHash);
                objectGraph = dbVal.isPresent() ? dbVal.get() : null;
            }
        }

        return objectGraph == null ? EMPTY_BYTE_ARRAY : objectGraph;
    }

    @Override
    public void setObjectGraph(byte[] graph) {
        Objects.requireNonNull(graph);

        this.objectGraph = graph;
        this.objectGraphHash = h256(objectGraph);

        dirty = true;
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

    public static AionContractDetailsImpl decode(RLPContractDetails input) {
        return decode(input, null, null);
    }

    /**
     * Decodes an AionContractDetailsImpl object from the RLP encoding.
     *
     * @param input The encoding to decode.
     */
    public static AionContractDetailsImpl decode(RLPContractDetails input, ByteArrayKeyValueStore storageSource, ByteArrayKeyValueStore objectGraphSource) {
        AionContractDetailsImpl details = new AionContractDetailsImpl(input.address, storageSource, objectGraphSource);
        details.externalStorage = input.isExternalStorage;

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
        RLPElement root = input.storageRoot;
        RLPElement storage = input.storageTrie;
        boolean keepStorageInMem = storage.getRLPData().length <= detailsInMemoryStorageLimit;

        // Instantiates the storage interpreting the storage root according to the VM specification.
        byte[] storageRootHash = root.getRLPData();

        // load/deserialize storage trie
        if (details.externalStorage) {
            details.storageTrie = new SecureTrie(details.externalStorageSource, storageRootHash);
        } else {
            details.storageTrie.deserialize(storage.getRLPData());
        }

        // switch from in-memory to external storage
        if (!details.externalStorage && !keepStorageInMem) {
            details.externalStorage = true;
            details.storageTrie.getCache().setDB(details.externalStorageSource);
        }
        return details;
    }

    /**
     * Returns an rlp encoding of this AionContractDetailsImpl object.
     *
     * <p>The encoding is a list of 6 elements:<br>
     * { 0:address, 1:isExternalStorage, 2:storageRoot, 3:storage, 4:code, 5:vmType }
     *
     * @return an rlp encoding of this.
     */
    @Override
    public byte[] getEncoded() {
        byte[] rlpAddress = RLP.encodeElement(address.toByteArray());
        byte[] rlpIsExternalStorage = RLP.encodeByte((byte) (externalStorage ? 1 : 0));
        byte[] rlpStorageRoot = RLP.encodeElement(externalStorage ? storageTrie.getRootHash() : EMPTY_BYTE_ARRAY);
        byte[] rlpStorage = RLP.encodeElement(externalStorage ? EMPTY_BYTE_ARRAY : storageTrie.serialize());
        byte[][] codes = new byte[getCodes().size()][];
        int i = 0;
        for (ByteArrayWrapper bytes : this.getCodes().values()) {
            codes[i++] = RLP.encodeElement(bytes.toBytes());
        }
        byte[] rlpCode = RLP.encodeList(codes);

        return RLP.encodeList(rlpAddress, rlpIsExternalStorage, rlpStorageRoot, rlpStorage, rlpCode);
    }

    /**
     * Get the address associated with this AionContractDetailsImpl.
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
        if (externalStorage) {
            storageTrie.sync();
        }
    }

    /**
     * Sets the external storage data source to dataSource.
     */
    @VisibleForTesting
    void initializeExternalStorageTrieForTest() {
        this.externalStorage = true;
        this.storageTrie = new SecureTrie(externalStorageSource);
    }

    /**
     * Returns an AionContractDetailsImpl object pertaining to a specific point in time given by the
     * storage root hash.
     *
     * @param hash the storage root hash to search for
     * @return the specified AionContractDetailsImpl.
     */
    public AionContractDetailsImpl getSnapshotTo(byte[] hash) {
        SecureTrie snapStorage;
        AionContractDetailsImpl details;
        snapStorage =
                wrap(hash).equals(wrap(ConstantUtil.EMPTY_TRIE_HASH))
                        ? new SecureTrie(storageTrie.getCache(), "".getBytes())
                        : new SecureTrie(storageTrie.getCache(), hash);
        snapStorage.withPruningEnabled(storageTrie.isPruningEnabled());
        details = new AionContractDetailsImpl(this.address, snapStorage, this.codes, this.externalStorageSource, this.objectGraphSource);

        // storage information
        details.externalStorage = this.externalStorage;

        return details;
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
    public AionContractDetailsImpl copy() {
        AionContractDetailsImpl aionContractDetailsCopy = new AionContractDetailsImpl(this.address, this.externalStorageSource, this.objectGraphSource);

        // storage information
        aionContractDetailsCopy.externalStorage = this.externalStorage;

        // object graph information
        aionContractDetailsCopy.objectGraph =
                objectGraph == null
                        ? null
                        : Arrays.copyOf(this.objectGraph, this.objectGraph.length);
        aionContractDetailsCopy.objectGraphHash =
                Arrays.equals(objectGraphHash, EMPTY_DATA_HASH)
                        ? EMPTY_DATA_HASH
                        : Arrays.copyOf(this.objectGraphHash, this.objectGraphHash.length);

        // storage hash used by AVM
        aionContractDetailsCopy.concatenatedStorageHash =
                Arrays.equals(concatenatedStorageHash, EMPTY_DATA_HASH)
                        ? EMPTY_DATA_HASH
                        : Arrays.copyOf(
                                this.concatenatedStorageHash, this.concatenatedStorageHash.length);

        aionContractDetailsCopy.codes = new HashMap<>(codes);
        aionContractDetailsCopy.dirty = this.dirty;
        aionContractDetailsCopy.deleted = this.deleted;
        aionContractDetailsCopy.storageTrie = (this.storageTrie == null) ? null : this.storageTrie.copy();
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

        ret.append("  objectGraphHash: ").append(Hex.toHexString(objectGraphHash)).append("\n");

        return ret.toString();
    }
}
