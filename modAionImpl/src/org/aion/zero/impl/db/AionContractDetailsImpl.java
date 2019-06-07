package org.aion.zero.impl.db;

import static org.aion.crypto.HashUtil.EMPTY_DATA_HASH;
import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.aion.crypto.HashUtil.h256;
import static org.aion.vm.api.types.ByteArrayWrapper.wrap;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import com.google.common.annotations.VisibleForTesting;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import org.aion.types.AionAddress;
import org.aion.interfaces.db.ByteArrayKeyValueStore;
import org.aion.interfaces.db.ContractDetails;
import org.aion.interfaces.db.InternalVmType;
import org.aion.mcf.ds.XorDataSource;
import org.aion.mcf.trie.SecureTrie;
import org.aion.precompiled.ContractFactory;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPItem;
import org.aion.rlp.RLPList;
import org.aion.vm.api.types.ByteArrayWrapper;

public class AionContractDetailsImpl extends AbstractContractDetails {
    private ByteArrayKeyValueStore dataSource;
    private ByteArrayKeyValueStore objectGraphSource = null;

    private byte[] rlpEncoded;

    private AionAddress address;

    private SecureTrie storageTrie = new SecureTrie(null);

    public boolean externalStorage;
    private ByteArrayKeyValueStore externalStorageDataSource;
    private ByteArrayKeyValueStore contractObjectGraphSource = null;

    private byte[] objectGraphHash = EMPTY_DATA_HASH;
    private byte[] concatenatedStorageHash = EMPTY_DATA_HASH;

    public AionContractDetailsImpl() {}

    public AionContractDetailsImpl(int prune, int memStorageLimit) {
        super(prune, memStorageLimit);
    }

    private AionContractDetailsImpl(
            AionAddress address, SecureTrie storageTrie, Map<ByteArrayWrapper, byte[]> codes) {
        if (address == null) {
            throw new IllegalArgumentException("Address can not be null!");
        } else {
            this.address = address;
        }
        this.storageTrie = storageTrie;
        setCodes(codes);
    }

    public AionContractDetailsImpl(byte[] code) throws Exception {
        if (code == null) {
            throw new Exception("Empty input code");
        }

        decode(code);
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

        byte[] data = RLP.encodeElement(value.getData());
        storageTrie.update(key.getData(), data);

        setDirty(true);
        rlpEncoded = null;
    }

    @Override
    public void delete(ByteArrayWrapper key) {
        Objects.requireNonNull(key);

        storageTrie.delete(key.getData());

        setDirty(true);
        rlpEncoded = null;
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
        byte[] data = storageTrie.get(key.getData());
        return (data == null || data.length == 0)
                ? null
                : new ByteArrayWrapper(RLP.decode2(data).get(0).getRLPData());
    }

    public void setVmType(InternalVmType vmType) {
        if (this.vmType != vmType && vmType != InternalVmType.EITHER) {
            this.vmType = vmType;

            setDirty(true);
            rlpEncoded = null;
        }
    }

    public InternalVmType getVmType() {
        return vmType;
    }

    @Override
    public void setCode(byte[] code) {
        super.setCode(code);
        if (isDirty()) {
            rlpEncoded = null;
        }
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

        this.setDirty(true);
        this.rlpEncoded = null;
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

    /**
     * Decodes an AionContractDetailsImpl object from the RLP encoding rlpCode.
     *
     * @param rlpCode The encoding to decode.
     */
    @Override
    public void decode(byte[] rlpCode) {
        decode(rlpCode, false);
    }

    /**
     * Decodes an AionContractDetailsImpl object from the RLP encoding rlpCode with fast check does
     * the contractDetails needs external storage.
     *
     * @param rlpCode The encoding to decode.
     * @param fastCheck set fastCheck option.
     */
    @Override
    public void decode(byte[] rlpCode, boolean fastCheck) {
        RLPList data = RLP.decode2(rlpCode);

        RLPList rlpList = (RLPList) data.get(0);

        // partial decode either encoding
        boolean keepStorageInMem = decodeEncodingWithoutVmType(rlpList, fastCheck);

        if (rlpList.size() == 5) {
            // the old encoding is used by FVM contracts
            // or by accounts accidentally mislabeled as contracts (issue in the repository cache)
            vmType = InternalVmType.UNKNOWN;

            // force a save with new encoding
            this.rlpEncoded = null;
        } else {
            // Decodes the new version of encoding which is a list of 6 elements, specifically:<br>
            //  { 0:address, 1:isExternalStorage, 2:storageRoot, 3:storage, 4:code, 5: vmType }
            RLPElement vm = rlpList.get(5);

            if (vm == null || vm.getRLPData() == null || vm.getRLPData().length == 0) {
                throw new IllegalArgumentException("rlp decode error: invalid vm code");
            } else {
                vmType = InternalVmType.getInstance(vm.getRLPData()[0]);
            }

            // keep encoding when compatible with new style
            this.rlpEncoded = rlpCode;
        }

        // both sides of the if above can return the UNKNOWN type
        // UNKNOWN type + code  =>  FVM contract
        if (vmType == InternalVmType.UNKNOWN) {
            byte[] code = getCode();
            if (code != null && code.length > 0) {
                vmType = InternalVmType.FVM;
                // force a save with new encoding
                this.rlpEncoded = null;
            }
        }

        if (!fastCheck || externalStorage || !keepStorageInMem) { // it was not a fast check
            decodeStorage(rlpList.get(2), rlpList.get(3), keepStorageInMem);

            if (vmType == InternalVmType.UNKNOWN) {
                if (!Arrays.equals(storageTrie.getRootHash(), EMPTY_TRIE_HASH)) {
                    // old encoding of FVM contract without code
                    vmType = InternalVmType.FVM;
                } else {
                    // no code & no storage => account mislabeled as contract
                    vmType = InternalVmType.EITHER;
                }
                // force a save with new encoding
                this.rlpEncoded = null;
            }
        }
    }

    /**
     * Decodes part of the old version of encoding which was a list of 5 elements, specifically:<br>
     * { 0:address, 1:isExternalStorage, 2:storageRoot, 3:storage, 4:code } <br>
     * without processing the storage information.
     *
     * <p>The 2:storageRoot and 3:storage must be processed externally to apply the distinct
     * interpretations based on the type of virtual machine.
     *
     * @return {@code true} if the storage must continue to be kept in memory, {@code false}
     *     otherwise
     */
    public boolean decodeEncodingWithoutVmType(RLPList rlpList, boolean fastCheck) {
        RLPItem isExternalStorage = (RLPItem) rlpList.get(1);
        RLPItem storage = (RLPItem) rlpList.get(3);
        this.externalStorage = isExternalStorage.getRLPData().length > 0;
        boolean keepStorageInMem = storage.getRLPData().length <= detailsInMemoryStorageLimit;

        // No externalStorage require.
        if (fastCheck && !externalStorage && keepStorageInMem) {
            return keepStorageInMem;
        }

        RLPItem address = (RLPItem) rlpList.get(0);
        RLPElement code = rlpList.get(4);

        if (address == null
                || address.getRLPData() == null
                || address.getRLPData().length != AionAddress.LENGTH) {
            throw new IllegalArgumentException("rlp decode error: invalid contract address");
        } else {
            this.address = new AionAddress(address.getRLPData());
        }

        if (code instanceof RLPList) {
            for (RLPElement e : ((RLPList) code)) {
                setCode(e.getRLPData());
            }
        } else {
            setCode(code.getRLPData());
        }
        return keepStorageInMem;
    }

    /** Instantiates the storage interpreting the storage root according to the VM specification. */
    public void decodeStorage(RLPElement root, RLPElement storage, boolean keepStorageInMem) {
        // different values based on the VM used
        byte[] storageRootHash;
        if (vmType == InternalVmType.AVM) {
            // points to the storage hash and the object graph hash
            concatenatedStorageHash = root.getRLPData();

            Optional<byte[]> concatenatedData =
                    objectGraphSource == null
                            ? Optional.empty()
                            : getContractObjectGraphSource().get(concatenatedStorageHash);
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
                objectGraphHash = pair.get(1).getRLPData();
            } else {
                storageRootHash = EMPTY_TRIE_HASH;
                objectGraphHash = EMPTY_DATA_HASH;
            }
        } else {
            storageRootHash = root.getRLPData();
        }

        // load/deserialize storage trie
        if (externalStorage) {
            storageTrie = new SecureTrie(getExternalStorageDataSource(), storageRootHash);
        } else {
            storageTrie.deserialize(storage.getRLPData());
        }
        storageTrie.withPruningEnabled(prune > 0);

        // switch from in-memory to external storage
        if (!externalStorage && !keepStorageInMem) {
            externalStorage = true;
            storageTrie.getCache().setDB(getExternalStorageDataSource());
        }
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
        if (rlpEncoded == null) {

            byte[] rlpAddress = RLP.encodeElement(address.toByteArray());
            byte[] rlpIsExternalStorage = RLP.encodeByte((byte) (externalStorage ? 1 : 0));
            byte[] rlpStorageRoot;
            // encoding for AVM
            if (vmType == InternalVmType.AVM) {
                rlpStorageRoot = RLP.encodeElement(computeAvmStorageHash());
            } else {
                rlpStorageRoot =
                        RLP.encodeElement(
                                externalStorage ? storageTrie.getRootHash() : EMPTY_BYTE_ARRAY);
            }
            byte[] rlpStorage =
                    RLP.encodeElement(externalStorage ? EMPTY_BYTE_ARRAY : storageTrie.serialize());
            byte[][] codes = new byte[getCodes().size()][];
            int i = 0;
            for (byte[] bytes : this.getCodes().values()) {
                codes[i++] = RLP.encodeElement(bytes);
            }
            byte[] rlpCode = RLP.encodeList(codes);

            if (vmType != InternalVmType.EITHER) {
                // vm type was not added
                byte[] rlpVmType = RLP.encodeByte(vmType.getCode());

                this.rlpEncoded =
                        RLP.encodeList(
                                rlpAddress,
                                rlpIsExternalStorage,
                                rlpStorageRoot,
                                rlpStorage,
                                rlpCode,
                                rlpVmType);
            } else {
                throw new IllegalStateException(
                        "Attempting to encode a contract without designated VM. Contract address: "
                                + address
                                + " Details: "
                                + this.toString());
            }
        }

        return rlpEncoded;
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

    /**
     * Sets the associated address to address.
     *
     * @param address The address to set.
     */
    @Override
    public void setAddress(AionAddress address) {
        if (address == null) {
            throw new IllegalArgumentException("Address can not be null!");
        }
        this.address = address;
        if (ContractFactory.isPrecompiledContract(address)) {
            setVmType(InternalVmType.FVM);
        }
        this.rlpEncoded = null;
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
     * Sets the data source to dataSource.
     *
     * @param dataSource The new dataSource.
     */
    public void setDataSource(ByteArrayKeyValueStore dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void setObjectGraphSource(ByteArrayKeyValueStore objectGraphSource) {
        this.objectGraphSource = objectGraphSource;
    }

    /**
     * Returns the external storage data source.
     *
     * @return the external storage data source.
     */
    private ByteArrayKeyValueStore getExternalStorageDataSource() {
        if (externalStorageDataSource == null) {
            externalStorageDataSource =
                    new XorDataSource(
                            dataSource, h256(("details-storage/" + address.toString()).getBytes()));
        }
        return externalStorageDataSource;
    }

    /**
     * Returns the data source specific to the current contract.
     *
     * @return the data source specific to the current contract.
     */
    private ByteArrayKeyValueStore getContractObjectGraphSource() {
        if (contractObjectGraphSource == null) {
            if (objectGraphSource == null) {
                throw new NullPointerException(
                        "The contract object graph source was not initialized.");
            } else {
                contractObjectGraphSource =
                        new XorDataSource(
                                objectGraphSource,
                                h256(("details-graph/" + address.toString()).getBytes()));
            }
        }
        return contractObjectGraphSource;
    }

    /**
     * Sets the external storage data source to dataSource.
     *
     * @param dataSource The new data source.
     * @implNote The tests are taking a shortcut here in bypassing the XorDataSource created by
     *     {@link #getExternalStorageDataSource()}. Do not use this method in production.
     */
    @VisibleForTesting
    void setExternalStorageDataSource(ByteArrayKeyValueStore dataSource) {
        // TODO: regarding the node above: the tests should be updated and the method removed
        this.externalStorageDataSource = dataSource;
        this.externalStorage = true;
        this.storageTrie = new SecureTrie(getExternalStorageDataSource());
    }

    /**
     * Returns an AionContractDetailsImpl object pertaining to a specific point in time given by the
     * root hash hash.
     *
     * @param hash The root hash to search for.
     * @return the specified AionContractDetailsImpl.
     */
    @Override
    public ContractDetails getSnapshotTo(byte[] hash) {

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
                storageRootHash = EMPTY_TRIE_HASH;
                graphHash = EMPTY_DATA_HASH;
            }

            snapStorage =
                    wrap(storageRootHash).equals(wrap(EMPTY_TRIE_HASH))
                            ? new SecureTrie(storageTrie.getCache(), "".getBytes())
                            : new SecureTrie(storageTrie.getCache(), storageRootHash);
            snapStorage.withPruningEnabled(storageTrie.isPruningEnabled());

            details = new AionContractDetailsImpl(this.address, snapStorage, getCodes());

            // object graph information
            details.objectGraphSource = this.objectGraphSource;
            details.objectGraphHash = graphHash;
            details.concatenatedStorageHash = hash;
        } else {
            snapStorage =
                    wrap(hash).equals(wrap(EMPTY_TRIE_HASH))
                            ? new SecureTrie(storageTrie.getCache(), "".getBytes())
                            : new SecureTrie(storageTrie.getCache(), hash);
            snapStorage.withPruningEnabled(storageTrie.isPruningEnabled());
            details = new AionContractDetailsImpl(this.address, snapStorage, getCodes());
        }

        // vm information
        details.vmType = this.vmType;

        // storage information
        details.externalStorage = this.externalStorage;
        details.externalStorageDataSource = this.externalStorageDataSource;
        details.dataSource = dataSource;
        details.setTransformedCode(this.getTransformedCode());

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
     * held by each of the {@link org.aion.mcf.trie.Node} objects in the underlying cache.
     *
     * @return A copy of this object.
     */
    @Override
    public AionContractDetailsImpl copy() {
        AionContractDetailsImpl aionContractDetailsCopy = new AionContractDetailsImpl();

        // vm information
        aionContractDetailsCopy.vmType = this.vmType;

        // storage information
        aionContractDetailsCopy.dataSource = this.dataSource;
        aionContractDetailsCopy.externalStorageDataSource = this.externalStorageDataSource;
        aionContractDetailsCopy.externalStorage = this.externalStorage;

        // object graph information
        aionContractDetailsCopy.objectGraphSource = this.objectGraphSource;
        aionContractDetailsCopy.contractObjectGraphSource = this.contractObjectGraphSource;
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

        aionContractDetailsCopy.prune = this.prune;
        aionContractDetailsCopy.detailsInMemoryStorageLimit = this.detailsInMemoryStorageLimit;
        aionContractDetailsCopy.setCodes(getDeepCopyOfCodes());
        aionContractDetailsCopy.setDirty(this.isDirty());
        aionContractDetailsCopy.setDeleted(this.isDeleted());
        aionContractDetailsCopy.address = this.address;
        aionContractDetailsCopy.rlpEncoded =
                (this.rlpEncoded == null)
                        ? null
                        : Arrays.copyOf(this.rlpEncoded, this.rlpEncoded.length);
        aionContractDetailsCopy.storageTrie =
                (this.storageTrie == null) ? null : this.storageTrie.copy();
        aionContractDetailsCopy.setTransformedCode(this.getTransformedCode());
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
}
