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
import org.aion.db.store.XorDataSource;
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

    private Map<ByteArrayWrapper, byte[]> codes = new HashMap<>();
    // classes extending this rely on this value starting off as null
    private byte[] objectGraph = null;

    // using the default transaction type to specify undefined VM
    private InternalVmType vmType = InternalVmType.EITHER;

    private ByteArrayKeyValueStore dataSource;
    private ByteArrayKeyValueStore objectGraphSource = null;

    private final AionAddress address;

    private SecureTrie storageTrie = new SecureTrie(null);

    private boolean externalStorage;

    private byte[] objectGraphHash = EMPTY_DATA_HASH;
    private byte[] concatenatedStorageHash = EMPTY_DATA_HASH;

    public AionContractDetailsImpl(AionAddress address) {
        this(address, null, null);
    }

    /**
     * Creates a object with attached database access for the storage and object graph.
     *
     * @param storageSource
     * @param objectGraphSource
     */
    public AionContractDetailsImpl(AionAddress address, ByteArrayKeyValueStore storageSource, ByteArrayKeyValueStore objectGraphSource) {
        if (address == null) {
            throw new IllegalArgumentException("Address can not be null!");
        } else {
            this.address = address;
            if (ContractInfo.isPrecompiledContract(address)) {
                setVmType(InternalVmType.FVM);
            }
        }
        this.dataSource = storageSource;
        this.objectGraphSource = objectGraphSource;
    }

    private AionContractDetailsImpl(AionAddress address, SecureTrie storageTrie, Map<ByteArrayWrapper, byte[]> codes, ByteArrayKeyValueStore externalStorageSource, ByteArrayKeyValueStore objectGraphSource) {
        this(address, externalStorageSource, objectGraphSource);
        this.storageTrie = storageTrie;
        setCodes(codes);
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
        byte[] code = codes.get(ByteArrayWrapper.wrap(codeHash));
        return code == null ? EMPTY_BYTE_ARRAY : code;
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
        if (this.vmType != vmType
                && vmType != InternalVmType.EITHER
                && vmType != InternalVmType.UNKNOWN) {
            this.vmType = vmType;
        }
    }

    public InternalVmType getVmType() {
        return vmType;
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
                Optional<byte[]> dbVal = getContractObjectGraphSource().get(objectGraphHash);
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
        if (vmType == InternalVmType.AVM) {
            return computeAvmStorageHash();
        } else {
            return storageTrie.getRootHash();
        }
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

    public static AionContractDetailsImpl decode(RLPContractDetails input, InternalVmType vm) {
        return decode(input, vm, null, null);
    }

    /**
     * Decodes an AionContractDetailsImpl object from the RLP encoding.
     *
     * @param input The encoding to decode.
     */
    public static AionContractDetailsImpl decode(RLPContractDetails input, InternalVmType vm, ByteArrayKeyValueStore storageSource, ByteArrayKeyValueStore objectGraphSource) {
        AionContractDetailsImpl details = new AionContractDetailsImpl(input.address, storageSource, objectGraphSource);
        details.vmType = vm;
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
        byte[] storageRootHash;
        if (details.vmType == InternalVmType.AVM) {
            // points to the storage hash and the object graph hash
            details.concatenatedStorageHash = root.getRLPData();

            Optional<byte[]> concatenatedData =
                    details.objectGraphSource == null
                            ? Optional.empty()
                            : details.getContractObjectGraphSource().get(details.concatenatedStorageHash);
            if (concatenatedData.isPresent()) {
                RLPList data = RLP.decode2(concatenatedData.get());
                if (!(data.get(0) instanceof RLPList)) {
                    throw new IllegalArgumentException(
                            "rlp decode error: invalid concatenated storage for AVM");
                }
                RLPList pair = (RLPList) data.get(0);
                if (pair.size() != 2) {
                    throw new IllegalArgumentException(
                            "rlp decode error: invalid concatenated storage for AVM");
                }

                storageRootHash = pair.get(0).getRLPData();
                details.objectGraphHash = pair.get(1).getRLPData();
            } else {
                storageRootHash = ConstantUtil.EMPTY_TRIE_HASH;
                details.objectGraphHash = EMPTY_DATA_HASH;
            }
        } else {
            storageRootHash = root.getRLPData();
        }

        // load/deserialize storage trie
        if (details.externalStorage) {
            details.storageTrie = new SecureTrie(details.getExternalStorageDataSource(), storageRootHash);
        } else {
            details.storageTrie.deserialize(storage.getRLPData());
        }

        // switch from in-memory to external storage
        if (!details.externalStorage && !keepStorageInMem) {
            details.externalStorage = true;
            details.storageTrie.getCache().setDB(details.getExternalStorageDataSource());
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
        byte[] rlpStorageRoot;
        // encoding for AVM
        if (vmType == InternalVmType.AVM) {
            rlpStorageRoot = RLP.encodeElement(computeAvmStorageHash());
        } else {
            rlpStorageRoot = RLP.encodeElement(externalStorage ? storageTrie.getRootHash() : EMPTY_BYTE_ARRAY);
        }
        byte[] rlpStorage = RLP.encodeElement(externalStorage ? EMPTY_BYTE_ARRAY : storageTrie.serialize());
        byte[][] codes = new byte[getCodes().size()][];
        int i = 0;
        for (byte[] bytes : this.getCodes().values()) {
            codes[i++] = RLP.encodeElement(bytes);
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
        if (vmType == InternalVmType.AVM) {
            // if (objectGraph == null || Arrays.equals(objectGraphHash, EMPTY_DATA_HASH)) {
            //     throw new IllegalStateException(
            //             "The AVM object graph must be set before pushing data to disk.");
            // }

            if (objectGraphSource == null) {
                throw new NullPointerException(
                        "The contract object graph source was not initialized.");
            }

            byte[] graph = getObjectGraph();
            if (!Arrays.equals(graph, EMPTY_BYTE_ARRAY)) {
                getContractObjectGraphSource().put(objectGraphHash, graph);
            }
            getContractObjectGraphSource()
                    .put(
                            computeAvmStorageHash(),
                            RLP.encodeList(
                                    RLP.encodeElement(storageTrie.getRootHash()),
                                    RLP.encodeElement(objectGraphHash)));
        }

        if (externalStorage) {
            storageTrie.sync();
        }
    }

    /**
     * Sets the data source for storing the AVM object graph.
     *
     * @param objectGraphSource the new data source used for storing the object graph
     */
    public void setObjectGraphSource(ByteArrayKeyValueStore objectGraphSource) {
        this.objectGraphSource = objectGraphSource;
    }

    /**
     * Returns the external storage data source.
     *
     * @return the external storage data source.
     */
    private ByteArrayKeyValueStore getExternalStorageDataSource() {
        return new XorDataSource(dataSource, h256(("details-storage/" + address.toString()).getBytes()));
    }

    /**
     * Returns the data source specific to the current contract.
     *
     * @return the data source specific to the current contract.
     */
    private ByteArrayKeyValueStore getContractObjectGraphSource() {
        if (objectGraphSource == null) {
            throw new NullPointerException("The contract object graph source was not initialized.");
        } else {
            return new XorDataSource(objectGraphSource, h256(("details-graph/" + address.toString()).getBytes()));
        }
    }

    /**
     * Sets the external storage data source to dataSource.
     */
    @VisibleForTesting
    void initializeExternalStorageTrieForTest() {
        this.externalStorage = true;
        this.storageTrie = new SecureTrie(getExternalStorageDataSource());
    }

    /**
     * Returns an AionContractDetailsImpl object pertaining to a specific point in time given by the
     * storage root hash.
     *
     * @param hash the storage root hash to search for
     * @param vm used to direct the interpretation of the storage root hash, since AVM contracts
     *     also include the hash of the object graph.
     * @return the specified AionContractDetailsImpl.
     */
    public AionContractDetailsImpl getSnapshotTo(byte[] hash, InternalVmType vm) {
        // set the VM type using the code hash
        vmType = vm;

        SecureTrie snapStorage;
        AionContractDetailsImpl details;
        if (vmType == InternalVmType.AVM) {
            byte[] storageRootHash, graphHash;
            // get the concatenated storage hash from storage
            Optional<byte[]> concatenatedData =
                    objectGraphSource == null
                            ? Optional.empty()
                            : getContractObjectGraphSource().get(hash);
            if (concatenatedData.isPresent()) {
                RLPList data = RLP.decode2(concatenatedData.get());
                if (!(data.get(0) instanceof RLPList)) {
                    throw new IllegalArgumentException(
                            "rlp decode error: invalid concatenated storage for AVM");
                }
                RLPList pair = (RLPList) data.get(0);
                if (pair.size() != 2) {
                    throw new IllegalArgumentException(
                            "rlp decode error: invalid concatenated storage for AVM");
                }

                storageRootHash = pair.get(0).getRLPData();
                graphHash = pair.get(1).getRLPData();
            } else {
                storageRootHash = ConstantUtil.EMPTY_TRIE_HASH;
                graphHash = EMPTY_DATA_HASH;
            }

            snapStorage =
                    wrap(storageRootHash).equals(wrap(ConstantUtil.EMPTY_TRIE_HASH))
                            ? new SecureTrie(storageTrie.getCache(), "".getBytes())
                            : new SecureTrie(storageTrie.getCache(), storageRootHash);
            snapStorage.withPruningEnabled(storageTrie.isPruningEnabled());

            details = new AionContractDetailsImpl(this.address, snapStorage, getCodes(), this.dataSource, this.objectGraphSource);

            // object graph information
            details.objectGraphHash = graphHash;
            details.concatenatedStorageHash = hash;
        } else {
            snapStorage =
                    wrap(hash).equals(wrap(ConstantUtil.EMPTY_TRIE_HASH))
                            ? new SecureTrie(storageTrie.getCache(), "".getBytes())
                            : new SecureTrie(storageTrie.getCache(), hash);
            snapStorage.withPruningEnabled(storageTrie.isPruningEnabled());
            details = new AionContractDetailsImpl(this.address, snapStorage, getCodes(), this.dataSource, this.objectGraphSource);
        }

        // vm information
        details.vmType = this.vmType;

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
        AionContractDetailsImpl aionContractDetailsCopy = new AionContractDetailsImpl(this.address, this.dataSource, this.objectGraphSource);

        // vm information
        aionContractDetailsCopy.vmType = this.vmType;

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

        aionContractDetailsCopy.setCodes(getDeepCopyOfCodes());
        aionContractDetailsCopy.dirty = this.dirty;
        aionContractDetailsCopy.deleted = this.deleted;
        aionContractDetailsCopy.storageTrie = (this.storageTrie == null) ? null : this.storageTrie.copy();
        return aionContractDetailsCopy;
    }

    // TODO: move this method up to the parent class.
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

    public byte[] getConcatenatedStorageHash() {
        return concatenatedStorageHash;
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
                    .map(key -> key + " -> " + Hex.toHexString(codes.get(key)))
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
        ret.append("  concatenatedStorageHash: ")
                .append(Hex.toHexString(concatenatedStorageHash))
                .append("\n");

        return ret.toString();
    }
}
