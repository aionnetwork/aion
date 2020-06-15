package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.base.ConstantUtil.EMPTY_TRIE_HASH;
import static org.aion.crypto.HashUtil.h256;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.impl.ByteArrayKeyValueStore;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.precompiled.ContractInfo;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPItem;
import org.aion.rlp.RLPList;
import org.aion.rlp.SharedRLPItem;
import org.aion.rlp.SharedRLPList;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.db.DetailsDataStore.RLPContractDetails;
import org.aion.zero.impl.trie.SecureTrie;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

/**
 * Unit tests for {@link AvmContractDetails}.
 *
 * @author Alexandra Roatis
 */
public class AvmContractDetailsTest {
    @Mock AionAddress mockAddress;
    @Mock ByteArrayKeyValueStore mockDatabase;
    @Mock RLPContractDetails mockInput;
    byte[] mockRoot = new byte[32];

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_withNullAddress() {
        new AvmContractDetails(null, mockDatabase, mockDatabase);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_withNullStorageDatabase() {
        new AvmContractDetails(mockAddress, null, mockDatabase);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_withNullGraphDatabase() {
        new AvmContractDetails(mockAddress, mockDatabase, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_withPrecompiledContractAddress() {
        new AvmContractDetails(ContractInfo.TOKEN_BRIDGE.contractAddress, mockDatabase, mockDatabase);
    }

    /** Ensures that the external expectations for a new instance are met. */
    @Test
    public void testStateAfterInstantiation() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        AvmContractDetails details = new AvmContractDetails(address, mockDatabase, mockDatabase);
        assertThat(details.address).isEqualTo(address);
        assertThat(details.isDirty()).isFalse();
        assertThat(details.isDeleted()).isFalse();
        assertThat(details.getObjectGraph()).isEqualTo(EMPTY_BYTE_ARRAY);
        assertThat(details.getCodes()).isEmpty();
    }

    @Test(expected = NullPointerException.class)
    public void testDecode_withNullInput() {
        AvmContractDetails.decodeAtRoot(null, mockDatabase, mockDatabase, mockRoot);
    }

    @Test(expected = NullPointerException.class)
    public void testDecode_withNullStorageDatabase() {
        AvmContractDetails.decodeAtRoot(mockInput, null, mockDatabase, mockRoot);
    }

    @Test(expected = NullPointerException.class)
    public void testDecode_withNullGraphDatabase() {
        AvmContractDetails.decodeAtRoot(mockInput, mockDatabase, null, mockRoot);
    }

    @Test(expected = NullPointerException.class)
    public void testDecode_withNullConsensusRoot() {
        AvmContractDetails.decodeAtRoot(mockInput, mockDatabase, mockDatabase, null);
    }

    @Test(expected = NullPointerException.class)
    public void testDecode_withNullAddress() {
        RLPContractDetails input = new RLPContractDetails(null, false, null, null, null);
        AvmContractDetails.decodeAtRoot(input, mockDatabase, mockDatabase, mockRoot);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecode_withIncorrectEncodingForConcatenatedData() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));

        byte[] rootHash = RandomUtils.nextBytes(32);
        RLPElement root = mock(RLPItem.class);
        when(root.getRLPData()).thenReturn(rootHash);

        byte[] codeBytes1 = RandomUtils.nextBytes(100);
        byte[] codeBytes2 = RandomUtils.nextBytes(100);
        RLPList code = new RLPList();
        code.add(new RLPItem(codeBytes1));
        code.add(new RLPItem(codeBytes2));

        byte[] storageHash = RandomUtils.nextBytes(32);
        when(mockDatabase.get(rootHash)).thenReturn(Optional.of(RLP.encodeElement(storageHash)));

        RLPContractDetails input = new RLPContractDetails(address, true, root, null, code);
        AvmContractDetails.decodeAtRoot(input, mockDatabase, mockDatabase, rootHash);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecode_withIncorrectListForConcatenatedData() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));

        byte[] rootHash = RandomUtils.nextBytes(32);
        RLPElement root = mock(RLPItem.class);
        when(root.getRLPData()).thenReturn(rootHash);

        byte[] codeBytes1 = RandomUtils.nextBytes(100);
        byte[] codeBytes2 = RandomUtils.nextBytes(100);
        RLPList code = new RLPList();
        code.add(new RLPItem(codeBytes1));
        code.add(new RLPItem(codeBytes2));

        byte[] storageHash = RandomUtils.nextBytes(32);
        when(mockDatabase.get(rootHash)).thenReturn(Optional.of(RLP.encodeList(RLP.encodeElement(storageHash))));

        RLPContractDetails input = new RLPContractDetails(address, true, root, null, code);
        AvmContractDetails.decodeAtRoot(input, mockDatabase, mockDatabase, rootHash);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecode_withMissingConcatenatedData() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));

        byte[] rootHash = RandomUtils.nextBytes(32);
        RLPElement root = mock(RLPItem.class);
        when(root.getRLPData()).thenReturn(rootHash);

        byte[] codeBytes1 = RandomUtils.nextBytes(100);
        byte[] codeBytes2 = RandomUtils.nextBytes(100);
        RLPList code = new RLPList();
        code.add(new RLPItem(codeBytes1));
        code.add(new RLPItem(codeBytes2));

        when(mockDatabase.get(rootHash)).thenReturn(Optional.empty());

        RLPContractDetails input = new RLPContractDetails(address, true, root, null, code);
        AvmContractDetails.decodeAtRoot(input, mockDatabase, mockDatabase, rootHash);
    }

    @Test
    public void testDecode_withExternalStorageAndMultiCode() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));

        byte[] rootHash = RandomUtils.nextBytes(32);
        RLPElement root = mock(RLPItem.class);
        when(root.getRLPData()).thenReturn(rootHash);

        byte[] codeBytes1 = RandomUtils.nextBytes(100);
        byte[] codeBytes2 = RandomUtils.nextBytes(100);
        RLPList code = new RLPList();
        code.add(new RLPItem(codeBytes1));
        code.add(new RLPItem(codeBytes2));

        byte[] storageHash = RandomUtils.nextBytes(32);

        byte[] graphHash = RandomUtils.nextBytes(32);
        byte[] graphBytes = RandomUtils.nextBytes(100);
        when(mockDatabase.get(rootHash)).thenReturn(Optional.of(RLP.encodeList(RLP.encodeElement(storageHash), RLP.encodeElement(graphHash))));
        when(mockDatabase.get(graphHash)).thenReturn(Optional.of(graphBytes));

        RLPContractDetails input = new RLPContractDetails(address, true, root, null, code);
        AvmContractDetails details = AvmContractDetails.decodeAtRoot(input, mockDatabase, mockDatabase, rootHash);
        assertThat(details.address).isEqualTo(address);
        assertThat(details.isDirty()).isTrue(); // because it uses the setCodes method
        assertThat(details.isDeleted()).isFalse();
        assertThat(details.getObjectGraph()).isEqualTo(graphBytes);

        assertThat(details.getCodes().size()).isEqualTo(2);
        assertThat(details.getCodes().values()).contains(ByteArrayWrapper.wrap(codeBytes1));
        assertThat(details.getCodes().values()).contains(ByteArrayWrapper.wrap(codeBytes2));
        assertThat(details.getCode(h256(codeBytes1))).isEqualTo(codeBytes1);
        assertThat(details.getCode(h256(codeBytes2))).isEqualTo(codeBytes2);

        byte[] concatenated = new byte[storageHash.length + graphHash.length];
        System.arraycopy(storageHash, 0, concatenated, 0, storageHash.length);
        System.arraycopy(graphHash, 0, concatenated, storageHash.length, graphHash.length);
        assertThat(details.getStorageHash()).isEqualTo(h256(concatenated));
    }

    @Test
    public void testDecode_withInLineStorageAndTransition() {
        SecureTrie trie = new SecureTrie(null);
        Map<ByteArrayWrapper, ByteArrayWrapper> storage = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            byte[] key = RandomUtils.nextBytes(32);
            byte[] value = RandomUtils.nextBytes(100);

            trie.update(key, RLP.encodeElement(value));
            storage.put(ByteArrayWrapper.wrap(key), ByteArrayWrapper.wrap(value));
        }

        RLPElement storageTrie = RLP.decode2SharedList(trie.serialize());

        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));

        byte[] codeBytes = RandomUtils.nextBytes(100);
        RLPElement code = mock(SharedRLPItem.class);
        when(code.getRLPData()).thenReturn(codeBytes);

        byte[] rootHash = RandomUtils.nextBytes(32);
        RLPElement root = mock(SharedRLPItem.class);
        when(root.getRLPData()).thenReturn(rootHash);

        byte[] storageHash = RandomUtils.nextBytes(32);

        byte[] graphHash = RandomUtils.nextBytes(32);
        byte[] graphBytes = RandomUtils.nextBytes(100);
        when(mockDatabase.get(rootHash)).thenReturn(Optional.of(RLP.encodeList(RLP.encodeElement(storageHash), RLP.encodeElement(graphHash))));
        when(mockDatabase.get(graphHash)).thenReturn(Optional.of(graphBytes));

        Logger log = mock(Logger.class);
        ByteArrayKeyValueDatabase db = new MockDB("db", log);
        db.open();
        assertThat(db.isEmpty()).isTrue();

        RLPContractDetails input = new RLPContractDetails(address, false, root, storageTrie, code);
        AvmContractDetails details = AvmContractDetails.decodeAtRoot(input, db, mockDatabase, rootHash);
        assertThat(details.address).isEqualTo(address);
        assertThat(details.isDirty()).isTrue(); // because it uses the setCodes method
        assertThat(details.isDeleted()).isFalse();
        assertThat(details.getObjectGraph()).isEqualTo(graphBytes);

        assertThat(details.getCodes().size()).isEqualTo(1);
        assertThat(details.getCodes().values()).contains(ByteArrayWrapper.wrap(codeBytes));
        assertThat(details.getCode(h256(codeBytes))).isEqualTo(codeBytes);

        storageHash = trie.getRootHash();
        byte[] concatenated = new byte[storageHash.length + graphHash.length];
        System.arraycopy(storageHash, 0, concatenated, 0, storageHash.length);
        System.arraycopy(graphHash, 0, concatenated, storageHash.length, graphHash.length);
        assertThat(details.getStorageHash()).isEqualTo(h256(concatenated));

        for (ByteArrayWrapper key : storage.keySet()) {
            assertThat(details.get(key)).isEqualTo(storage.get(key));
        }

        assertThat(db.isEmpty()).isFalse();
    }

    @Test
    public void testDecode_withInLineStorageAndEmptyStorageTrie() {
        SecureTrie trie = new SecureTrie(null);
        Map<ByteArrayWrapper, ByteArrayWrapper> storage = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            byte[] key = RandomUtils.nextBytes(32);
            byte[] value = RandomUtils.nextBytes(100);

            trie.update(key, RLP.encodeElement(value));
            storage.put(ByteArrayWrapper.wrap(key), ByteArrayWrapper.wrap(value));
        }

        RLPElement storageTrie = RLP.decode2SharedList(trie.serialize());

        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));

        byte[] codeBytes = RandomUtils.nextBytes(100);
        RLPElement code = mock(SharedRLPItem.class);
        when(code.getRLPData()).thenReturn(codeBytes);

        byte[] rootHash = RandomUtils.nextBytes(32);
        RLPElement root = mock(SharedRLPItem.class);
        when(root.getRLPData()).thenReturn(rootHash);

        byte[] storageHash = EMPTY_TRIE_HASH;

        byte[] graphHash = RandomUtils.nextBytes(32);
        byte[] graphBytes = RandomUtils.nextBytes(100);
        when(mockDatabase.get(rootHash)).thenReturn(Optional.of(RLP.encodeList(RLP.encodeElement(storageHash), RLP.encodeElement(graphHash))));
        when(mockDatabase.get(graphHash)).thenReturn(Optional.of(graphBytes));

        Logger log = mock(Logger.class);
        ByteArrayKeyValueDatabase db = new MockDB("db", log);
        db.open();
        assertThat(db.isEmpty()).isTrue();

        RLPContractDetails input = new RLPContractDetails(address, false, root, storageTrie, code);
        AvmContractDetails details = AvmContractDetails.decodeAtRoot(input, db, mockDatabase, rootHash);
        assertThat(details.address).isEqualTo(address);
        assertThat(details.isDirty()).isTrue(); // because it uses the setCodes method
        assertThat(details.isDeleted()).isFalse();
        assertThat(details.getObjectGraph()).isEqualTo(graphBytes);

        assertThat(details.getCodes().size()).isEqualTo(1);
        assertThat(details.getCodes().values()).contains(ByteArrayWrapper.wrap(codeBytes));
        assertThat(details.getCode(h256(codeBytes))).isEqualTo(codeBytes);

        byte[] concatenated = new byte[storageHash.length + graphHash.length];
        System.arraycopy(storageHash, 0, concatenated, 0, storageHash.length);
        System.arraycopy(graphHash, 0, concatenated, storageHash.length, graphHash.length);
        assertThat(details.getStorageHash()).isEqualTo(h256(concatenated));

        for (ByteArrayWrapper key : storage.keySet()) {
            assertThat(details.get(key)).isNull();
        }

        assertThat(db.isEmpty()).isFalse();
    }

    @Test
    public void testEncodingCorrectSize() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        byte[] code = RandomUtils.nextBytes(512);

        AvmContractDetails details = new AvmContractDetails(address, mockDatabase, mockDatabase);
        details.setCode(code);

        // ensure correct size after VM type is set
        SharedRLPList data = (SharedRLPList) RLP.decode2SharedList(details.getEncoded()).get(0);
        assertThat(data.size()).isEqualTo(3);
    }
}