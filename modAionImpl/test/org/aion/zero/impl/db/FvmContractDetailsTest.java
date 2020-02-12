package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.base.ConstantUtil.EMPTY_TRIE_HASH;
import static org.aion.crypto.HashUtil.h256;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.impl.ByteArrayKeyValueStore;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPItem;
import org.aion.rlp.RLPList;
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
 * Unit tests for {@link FvmContractDetails}.
 *
 * @author Alexandra Roatis
 */
public class FvmContractDetailsTest {
    @Mock AionAddress mockAddress;
    @Mock ByteArrayKeyValueStore mockDatabase;
    @Mock RLPContractDetails mockInput;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_withNullAddress() {
        new FvmContractDetails(null, mockDatabase);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_withNullStorageDatabase() {
        new FvmContractDetails(mockAddress, null);
    }

    /** Ensures that the external expectations for a new instance are met. */
    @Test
    public void testStateAfterInstantiation() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        FvmContractDetails details = new FvmContractDetails(address, mockDatabase);
        assertThat(details.getAddress()).isEqualTo(address);
        assertThat(details.isDirty()).isFalse();
        assertThat(details.isDeleted()).isFalse();
        assertThat(details.getCodes()).isEmpty();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testObjectGraphGetter() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        FvmContractDetails details = new FvmContractDetails(address, mockDatabase);
        details.getObjectGraph();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testObjectGraphSetter() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        FvmContractDetails details = new FvmContractDetails(address, mockDatabase);
        details.setObjectGraph(new byte[0]);
    }

    @Test(expected = NullPointerException.class)
    public void testDecode_withNullInput() {
        FvmContractDetails.decode(null, mockDatabase);
    }

    @Test(expected = NullPointerException.class)
    public void testDecode_withNullStorageDatabase() {
        FvmContractDetails.decode(mockInput, null);
    }

    @Test
    public void testDecode_withExternalStorageAndMultiCode() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));

        byte[] storageHash = RandomUtils.nextBytes(32);
        RLPElement root = mock(RLPItem.class);
        when(root.getRLPData()).thenReturn(storageHash);

        byte[] codeBytes1 = RandomUtils.nextBytes(100);
        byte[] codeBytes2 = RandomUtils.nextBytes(100);
        RLPList code = new RLPList();
        code.add(new RLPItem(codeBytes1));
        code.add(new RLPItem(codeBytes2));

        RLPContractDetails input = new RLPContractDetails(address, true, root, null, code);
        FvmContractDetails details = FvmContractDetails.decode(input, mockDatabase);
        assertThat(details.getAddress()).isEqualTo(address);
        assertThat(details.isDirty()).isTrue(); // because it uses the setCodes method
        assertThat(details.isDeleted()).isFalse();

        assertThat(details.getCodes().size()).isEqualTo(2);
        assertThat(details.getCodes().values()).contains(ByteArrayWrapper.wrap(codeBytes1));
        assertThat(details.getCodes().values()).contains(ByteArrayWrapper.wrap(codeBytes2));
        assertThat(details.getCode(h256(codeBytes1))).isEqualTo(codeBytes1);
        assertThat(details.getCode(h256(codeBytes2))).isEqualTo(codeBytes2);

        assertThat(details.getStorageHash()).isEqualTo(storageHash);
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

        RLPElement storageTrie = mock(RLPItem.class);
        when(storageTrie.getRLPData()).thenReturn(trie.serialize());

        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));

        byte[] codeBytes = RandomUtils.nextBytes(100);
        RLPElement code = mock(RLPItem.class);
        when(code.getRLPData()).thenReturn(codeBytes);

        byte[] rootHash = RandomUtils.nextBytes(32);
        RLPElement root = mock(RLPItem.class);
        when(root.getRLPData()).thenReturn(rootHash);

        Logger log = mock(Logger.class);
        ByteArrayKeyValueDatabase db = new MockDB("db", log);
        db.open();
        assertThat(db.isEmpty()).isTrue();

        RLPContractDetails input = new RLPContractDetails(address, false, root, storageTrie, code);
        FvmContractDetails details = FvmContractDetails.decode(input, db);
        assertThat(details.getAddress()).isEqualTo(address);
        assertThat(details.isDirty()).isTrue(); // because it uses the setCodes method
        assertThat(details.isDeleted()).isFalse();

        assertThat(details.getCodes().size()).isEqualTo(1);
        assertThat(details.getCodes().values()).contains(ByteArrayWrapper.wrap(codeBytes));
        assertThat(details.getCode(h256(codeBytes))).isEqualTo(codeBytes);

        byte[] storageHash = trie.getRootHash();
        assertThat(details.getStorageHash()).isEqualTo(storageHash);

        for (ByteArrayWrapper key : storage.keySet()) {
            assertThat(details.get(key)).isEqualTo(storage.get(key));
        }

        assertThat(db.isEmpty()).isFalse();
    }

    @Test
    public void testDecode_withInLineStorageAndEmptyStorageTrie() {
        SecureTrie trie = new SecureTrie(null);

        RLPElement storageTrie = mock(RLPItem.class);
        when(storageTrie.getRLPData()).thenReturn(trie.serialize());

        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));

        byte[] codeBytes = RandomUtils.nextBytes(100);
        RLPElement code = mock(RLPItem.class);
        when(code.getRLPData()).thenReturn(codeBytes);

        byte[] storageHash = EMPTY_TRIE_HASH;
        RLPElement root = mock(RLPItem.class);
        when(root.getRLPData()).thenReturn(storageHash);

        Logger log = mock(Logger.class);
        ByteArrayKeyValueDatabase db = new MockDB("db", log);
        db.open();
        assertThat(db.isEmpty()).isTrue();

        RLPContractDetails input = new RLPContractDetails(address, false, root, storageTrie, code);
        FvmContractDetails details = FvmContractDetails.decode(input, db);
        assertThat(details.getAddress()).isEqualTo(address);
        assertThat(details.isDirty()).isTrue(); // because it uses the setCodes method
        assertThat(details.isDeleted()).isFalse();

        assertThat(details.getCodes().size()).isEqualTo(1);
        assertThat(details.getCodes().values()).contains(ByteArrayWrapper.wrap(codeBytes));
        assertThat(details.getCode(h256(codeBytes))).isEqualTo(codeBytes);

        assertThat(details.getStorageHash()).isEqualTo(storageHash);
    }
}