package org.aion.zero.db;

import static org.aion.base.util.ByteArrayWrapper.wrap;
import static org.aion.base.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.aion.crypto.HashUtil.h256;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.aion.base.db.IByteArrayKeyValueStore;
import org.aion.base.db.IContractDetails;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.mcf.db.AbstractContractDetails;
import org.aion.mcf.ds.XorDataSource;
import org.aion.mcf.trie.SecureTrie;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPItem;
import org.aion.rlp.RLPList;
import org.aion.util.conversions.Hex;
import org.aion.vm.api.interfaces.Address;

public class AionContractDetailsImpl extends AbstractContractDetails {

    private IByteArrayKeyValueStore dataSource;

    private byte[] rlpEncoded;

    private Address address = AionAddress.EMPTY_ADDRESS();

    private SecureTrie storageTrie = new SecureTrie(null);

    public boolean externalStorage;
    private IByteArrayKeyValueStore externalStorageDataSource;

    public AionContractDetailsImpl() {}

    public AionContractDetailsImpl(int prune, int memStorageLimit) {
        super(prune, memStorageLimit);
    }

    private AionContractDetailsImpl(
            Address address, SecureTrie storageTrie, Map<ByteArrayWrapper, byte[]> codes) {
        this.address = address;
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
        // We strip leading zeros of a DataWord but not a DoubleDataWord so that when we call get
        // we can differentiate between the two.

        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        if (value.isZero()) {
            storageTrie.delete(key.getData());
        } else {
            byte[] data = RLP.encodeElement(value.getData());
            storageTrie.update(key.getData(), data);
        }

        this.setDirty(true);
        this.rlpEncoded = null;
    }

    /**
     * Returns the value associated with key if it exists, otherwise returns a DataWord consisting
     * entirely of zero bytes.
     *
     * @param key The key to query.
     * @return the corresponding value or a zero-byte DataWord if no such value.
     */
    @Override
    public ByteArrayWrapper get(ByteArrayWrapper key) {
        byte[] data = storageTrie.get(key.getData());
        return (data == null || data.length == 0)
                ? DataWord.ZERO.toWrapper()
                : new ByteArrayWrapper(RLP.decode2(data).get(0).getRLPData());
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
     * Decodes an AionContractDetailsImpl object from the RLP encoding rlpCode.
     *
     * @param rlpCode The encoding to decode.
     */
    @Override
    public void decode(byte[] rlpCode) {
        RLPList data = RLP.decode2(rlpCode);
        RLPList rlpList = (RLPList) data.get(0);

        RLPItem address = (RLPItem) rlpList.get(0);
        RLPItem isExternalStorage = (RLPItem) rlpList.get(1);
        RLPItem storageRoot = (RLPItem) rlpList.get(2);
        RLPItem storage = (RLPItem) rlpList.get(3);
        RLPElement code = rlpList.get(4);

        if (address.getRLPData() == null) {
            this.address = AionAddress.EMPTY_ADDRESS();
        } else {
            this.address = AionAddress.wrap(address.getRLPData());
        }

        if (code instanceof RLPList) {
            for (RLPElement e : ((RLPList) code)) {
                setCode(e.getRLPData());
            }
        } else {
            setCode(code.getRLPData());
        }

        // load/deserialize storage trie
        this.externalStorage = !Arrays.equals(isExternalStorage.getRLPData(), EMPTY_BYTE_ARRAY);
        if (externalStorage) {
            storageTrie = new SecureTrie(getExternalStorageDataSource(), storageRoot.getRLPData());
        } else {
            storageTrie.deserialize(storage.getRLPData());
        }
        storageTrie.withPruningEnabled(prune > 0);

        // switch from in-memory to external storage
        if (!externalStorage && storage.getRLPData().length > detailsInMemoryStorageLimit) {
            externalStorage = true;
            storageTrie.getCache().setDB(getExternalStorageDataSource());
        }

        this.rlpEncoded = rlpCode;
    }

    /**
     * Returns an rlp encoding of this AionContractDetailsImpl object.
     *
     * @return an rlp encoding of this.
     */
    @Override
    public byte[] getEncoded() {
        if (rlpEncoded == null) {

            byte[] rlpAddress = RLP.encodeElement(address.toBytes());
            byte[] rlpIsExternalStorage = RLP.encodeByte((byte) (externalStorage ? 1 : 0));
            byte[] rlpStorageRoot =
                    RLP.encodeElement(
                            externalStorage ? storageTrie.getRootHash() : EMPTY_BYTE_ARRAY);
            byte[] rlpStorage =
                    RLP.encodeElement(externalStorage ? EMPTY_BYTE_ARRAY : storageTrie.serialize());
            byte[][] codes = new byte[getCodes().size()][];
            int i = 0;
            for (byte[] bytes : this.getCodes().values()) {
                codes[i++] = RLP.encodeElement(bytes);
            }
            byte[] rlpCode = RLP.encodeList(codes);

            this.rlpEncoded =
                    RLP.encodeList(
                            rlpAddress, rlpIsExternalStorage, rlpStorageRoot, rlpStorage, rlpCode);
        }

        return rlpEncoded;
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
            put(storageKeys.get(i), storageValues.get(i));
        }
    }

    /**
     * Sets the storage to contain the specified key-value mappings.
     *
     * @param storage The specified mappings.
     */
    @Override
    public void setStorage(Map<ByteArrayWrapper, ByteArrayWrapper> storage) {
        for (ByteArrayWrapper key : storage.keySet()) {
            put(key, storage.get(key));
        }
    }

    /**
     * Get the address associated with this AionContractDetailsImpl.
     *
     * @return the associated address.
     */
    @Override
    public Address getAddress() {
        return address;
    }

    /**
     * Sets the associated address to address.
     *
     * @param address The address to set.
     */
    @Override
    public void setAddress(Address address) {
        this.address = address;
        this.rlpEncoded = null;
    }

    /** Syncs the storage trie. */
    @Override
    public void syncStorage() {
        if (externalStorage) {
            storageTrie.sync();
        }
    }

    /**
     * Sets the data source to dataSource.
     *
     * @param dataSource The new dataSource.
     */
    public void setDataSource(IByteArrayKeyValueStore dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Returns the external storage data source.
     *
     * @return the external storage data source.
     */
    private IByteArrayKeyValueStore getExternalStorageDataSource() {
        if (externalStorageDataSource == null) {
            externalStorageDataSource =
                    new XorDataSource(
                            dataSource, h256(("details-storage/" + address.toString()).getBytes()));
        }
        return externalStorageDataSource;
    }

    /**
     * Sets the external storage data source to dataSource.
     *
     * @param dataSource The new data source.
     */
    public void setExternalStorageDataSource(IByteArrayKeyValueStore dataSource) {
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
    public IContractDetails getSnapshotTo(byte[] hash) {

        IByteArrayKeyValueStore keyValueDataSource = this.storageTrie.getCache().getDb();

        SecureTrie snapStorage =
                wrap(hash).equals(wrap(EMPTY_TRIE_HASH))
                        ? new SecureTrie(keyValueDataSource, "".getBytes())
                        : new SecureTrie(keyValueDataSource, hash);
        snapStorage.withPruningEnabled(storageTrie.isPruningEnabled());

        snapStorage.setCache(this.storageTrie.getCache());

        AionContractDetailsImpl details =
                new AionContractDetailsImpl(this.address, snapStorage, getCodes());
        details.externalStorage = this.externalStorage;
        details.externalStorageDataSource = this.externalStorageDataSource;
        details.dataSource = dataSource;

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
        aionContractDetailsCopy.dataSource = this.dataSource;
        aionContractDetailsCopy.externalStorageDataSource = this.externalStorageDataSource;
        aionContractDetailsCopy.externalStorage = this.externalStorage;
        aionContractDetailsCopy.prune = this.prune;
        aionContractDetailsCopy.detailsInMemoryStorageLimit = this.detailsInMemoryStorageLimit;
        aionContractDetailsCopy.setCodes(getDeepCopyOfCodes());
        aionContractDetailsCopy.setDirty(this.isDirty());
        aionContractDetailsCopy.setDeleted(this.isDeleted());
        aionContractDetailsCopy.address =
                (this.address == null) ? null : new AionAddress(this.address.toBytes());
        aionContractDetailsCopy.rlpEncoded =
                (this.rlpEncoded == null)
                        ? null
                        : Arrays.copyOf(this.rlpEncoded, this.rlpEncoded.length);
        aionContractDetailsCopy.storageTrie =
                (this.storageTrie == null) ? null : this.storageTrie.copy();
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
