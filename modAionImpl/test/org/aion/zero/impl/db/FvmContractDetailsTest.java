package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.base.ConstantUtil.EMPTY_TRIE_HASH;
import static org.aion.crypto.HashUtil.h256;
import static org.aion.zero.impl.db.DetailsDataStore.fromEncoding;
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
import org.aion.rlp.SharedRLPItem;
import org.aion.rlp.SharedRLPList;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.AddressUtils;
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
    @Mock Logger log;
    byte[] mockRoot = new byte[32];

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
        assertThat(details.address).isEqualTo(address);
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
        FvmContractDetails.decodeAtRoot(null, mockDatabase, mockRoot);
    }

    @Test(expected = NullPointerException.class)
    public void testDecode_withNullStorageDatabase() {
        FvmContractDetails.decodeAtRoot(mockInput, null, mockRoot);
    }

    @Test(expected = NullPointerException.class)
    public void testDecode_withNullConsensusRoot() {
        FvmContractDetails.decodeAtRoot(mockInput, mockDatabase, null);
    }

    @Test(expected = NullPointerException.class)
    public void testDecode_withNullAddress() {
        RLPContractDetails input = new RLPContractDetails(null, false, null, null, null);
        FvmContractDetails.decodeAtRoot(input, mockDatabase, mockRoot);
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
        FvmContractDetails details = FvmContractDetails.decodeAtRoot(input, mockDatabase, storageHash);
        assertThat(details.address).isEqualTo(address);
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

        RLPElement storageTrie = RLP.decode2SharedList(trie.serialize());

        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));

        byte[] codeBytes = RandomUtils.nextBytes(100);
        RLPElement code = mock(SharedRLPItem.class);
        when(code.getRLPData()).thenReturn(codeBytes);

        byte[] rootHash = RandomUtils.nextBytes(32);
        RLPElement root = mock(SharedRLPItem.class);
        when(root.getRLPData()).thenReturn(rootHash);

        Logger log = mock(Logger.class);
        ByteArrayKeyValueDatabase db = new MockDB("db", log);
        db.open();
        assertThat(db.isEmpty()).isTrue();

        RLPContractDetails input = new RLPContractDetails(address, false, root, storageTrie, code);
        FvmContractDetails details = FvmContractDetails.decodeAtRoot(input, db, rootHash);
        assertThat(details.address).isEqualTo(address);
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

        RLPElement storageTrie = RLP.decode2SharedList(trie.serialize());

        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));

        byte[] codeBytes = RandomUtils.nextBytes(100);
        RLPElement code = mock(SharedRLPItem.class);
        when(code.getRLPData()).thenReturn(codeBytes);

        byte[] storageHash = EMPTY_TRIE_HASH;
        RLPElement root = mock(SharedRLPItem.class);
        when(root.getRLPData()).thenReturn(storageHash);

        ByteArrayKeyValueDatabase db = new MockDB("db", log);
        db.open();
        assertThat(db.isEmpty()).isTrue();

        RLPContractDetails input = new RLPContractDetails(address, false, root, storageTrie, code);
        FvmContractDetails details = FvmContractDetails.decodeAtRoot(input, db, storageHash);
        assertThat(details.address).isEqualTo(address);
        assertThat(details.isDirty()).isTrue(); // because it uses the setCodes method
        assertThat(details.isDeleted()).isFalse();

        assertThat(details.getCodes().size()).isEqualTo(1);
        assertThat(details.getCodes().values()).contains(ByteArrayWrapper.wrap(codeBytes));
        assertThat(details.getCode(h256(codeBytes))).isEqualTo(codeBytes);

        assertThat(details.getStorageHash()).isEqualTo(storageHash);
    }

    @Test
    public void testEncodingCorrectSize() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        byte[] code = RandomUtils.nextBytes(512);

        FvmContractDetails details = new FvmContractDetails(address, mockDatabase);
        details.setCode(code);

        // ensure correct size after VM type is set
        SharedRLPList data = (SharedRLPList) RLP.decode2SharedList(details.getEncoded()).get(0);
        assertThat(data.size()).isEqualTo(3);
    }

    @Test
    public void testDecodeFromEncodingWithSmallStorage() {
        ByteArrayKeyValueDatabase db = new MockDB("db", log);
        db.open();
        assertThat(db.isEmpty()).isTrue();

        byte[] code = ByteUtil.hexStringToBytes("60016002");

        ByteArrayWrapper key_1 = ByteArrayWrapper.fromHex("111111");
        ByteArrayWrapper val_1 = ByteArrayWrapper.fromHex("aaaaaa");

        ByteArrayWrapper key_2 = ByteArrayWrapper.fromHex("222222");
        ByteArrayWrapper val_2 = ByteArrayWrapper.fromHex("bbbbbb");

        FvmContractDetails details = new FvmContractDetails(AddressUtils.ZERO_ADDRESS, db);
        details.setCode(code);
        details.put(key_1, val_1);
        details.put(key_2, val_2);

        // flush changes to the database
        details.syncStorage();

        FvmContractDetails decoded = FvmContractDetails.decodeAtRoot(fromEncoding(details.getEncoded()), db, details.getStorageHash());

        // check code
        byte[] codeHash = h256(code);
        assertThat(decoded.getCode(codeHash)).isEqualTo(code);

        // check address
        assertThat(decoded.address).isEqualTo(AddressUtils.ZERO_ADDRESS);

        // check storage
        assertThat(decoded.get(key_1)).isEqualTo(val_1);
        assertThat(decoded.get(key_2)).isEqualTo(val_2);
    }

    @Test
    public void testDecodeFromEncodingWithMultiCodeAndLargeStorage() {
        ByteArrayKeyValueDatabase db = new MockDB("db", log);
        db.open();
        assertThat(db.isEmpty()).isTrue();

        byte[] code1 = ByteUtil.hexStringToBytes("7c0100000000000000000000000000000000000000000000000000000000600035046333d546748114610065578063430fe5f01461007c5780634d432c1d1461008d578063501385b2146100b857806357eb3b30146100e9578063dbc7df61146100fb57005b6100766004356024356044356102f0565b60006000f35b61008760043561039e565b60006000f35b610098600435610178565b8073ffffffffffffffffffffffffffffffffffffffff1660005260206000f35b6100c96004356024356044356101a0565b8073ffffffffffffffffffffffffffffffffffffffff1660005260206000f35b6100f1610171565b8060005260206000f35b610106600435610133565b8360005282602052816040528073ffffffffffffffffffffffffffffffffffffffff1660605260806000f35b5b60006020819052908152604090208054600182015460028301546003909301549192909173ffffffffffffffffffffffffffffffffffffffff1684565b5b60015481565b5b60026020526000908152604090205473ffffffffffffffffffffffffffffffffffffffff1681565b73ffffffffffffffffffffffffffffffffffffffff831660009081526020819052604081206002015481908302341080156101fe575073ffffffffffffffffffffffffffffffffffffffff8516600090815260208190526040812054145b8015610232575073ffffffffffffffffffffffffffffffffffffffff85166000908152602081905260409020600101548390105b61023b57610243565b3391506102e8565b6101966103ca60003973ffffffffffffffffffffffffffffffffffffffff3381166101965285166101b68190526000908152602081905260408120600201546101d6526101f68490526102169080f073ffffffffffffffffffffffffffffffffffffffff8616600090815260208190526040902060030180547fffffffffffffffffffffffff0000000000000000000000000000000000000000168217905591508190505b509392505050565b73ffffffffffffffffffffffffffffffffffffffff33166000908152602081905260408120548190821461032357610364565b60018054808201909155600090815260026020526040902080547fffffffffffffffffffffffff000000000000000000000000000000000000000016331790555b50503373ffffffffffffffffffffffffffffffffffffffff1660009081526020819052604090209081556001810192909255600290910155565b3373ffffffffffffffffffffffffffffffffffffffff166000908152602081905260409020600201555600608061019660043960048051602451604451606451600080547fffffffffffffffffffffffff0000000000000000000000000000000000000000908116909517815560018054909516909317909355600355915561013390819061006390396000f3007c0100000000000000000000000000000000000000000000000000000000600035046347810fe381146100445780637e4a1aa81461005557806383d2421b1461006957005b61004f6004356100ab565b60006000f35b6100636004356024356100fc565b60006000f35b61007460043561007a565b60006000f35b6001543373ffffffffffffffffffffffffffffffffffffffff9081169116146100a2576100a8565b60078190555b50565b73ffffffffffffffffffffffffffffffffffffffff8116600090815260026020526040902080547fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff0016600117905550565b6001543373ffffffffffffffffffffffffffffffffffffffff9081169116146101245761012f565b600582905560068190555b505056");
        byte[] code2 = ByteUtil.hexStringToBytes("60016002");
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));

        Map<ByteArrayWrapper, ByteArrayWrapper> storage =new HashMap<>();
        storage.put(ByteArrayWrapper.fromHex("18d63b70aa690ad37cb50908746c9a55"), ByteArrayWrapper.fromHex("00000000000000000000000000000064"));
        storage.put(ByteArrayWrapper.fromHex("18d63b70aa690ad37cb50908746c9a56"), ByteArrayWrapper.fromHex("0000000000000000000000000000000c"));
        storage.put(ByteArrayWrapper.fromHex("5a448d1967513482947d1d3f6104316f"), null);
        storage.put(ByteArrayWrapper.fromHex("5a448d1967513482947d1d3f61043171"), ByteArrayWrapper.fromHex("00000000000000000000000000000014"));
        storage.put(ByteArrayWrapper.fromHex("18d63b70aa690ad37cb50908746c9a54"), null);
        storage.put(ByteArrayWrapper.fromHex("5a448d1967513482947d1d3f61043170"), ByteArrayWrapper.fromHex("00000000000000000000000000000078"));
        storage.put(ByteArrayWrapper.fromHex("c83a08bbccc01a0644d599ccd2a7c2e0"), ByteArrayWrapper.fromHex("8fbec874791c4e3f9f48a59a44686efe"));
        storage.put(ByteArrayWrapper.fromHex("5aa541c6c03f602a426f04ae47508bb8"), ByteArrayWrapper.fromHex("7a657031000000000000000000000000"));
        storage.put(ByteArrayWrapper.fromHex("5aa541c6c03f602a426f04ae47508bb9"), ByteArrayWrapper.fromHex("000000000000000000000000000000c8"));
        storage.put(ByteArrayWrapper.fromHex("5aa541c6c03f602a426f04ae47508bba"), ByteArrayWrapper.fromHex("0000000000000000000000000000000a"));
        storage.put(ByteArrayWrapper.fromHex("00000000000000000000000000000001"), ByteArrayWrapper.fromHex("00000000000000000000000000000003"));
        storage.put(ByteArrayWrapper.fromHex("5aa541c6c03f602a426f04ae47508bbb"), ByteArrayWrapper.fromHex("194bcfc3670d8a1613e5b0c790036a35"));
        storage.put(ByteArrayWrapper.fromHex("aee92919b8c3389af86ef24535e8a28c"), ByteArrayWrapper.fromHex("cfe293a85bef5915e1a7acb37bf0c685"));
        storage.put(ByteArrayWrapper.fromHex("65c996598dc972688b7ace676c89077b"), ByteArrayWrapper.fromHex("d6ee27e285f2de7b68e8db25cf1b1063"));

        FvmContractDetails details = new FvmContractDetails(address, db);
        details.setCode(code1);
        details.setCode(code2);

        // update storage
        for (ByteArrayWrapper key : storage.keySet()) {
            ByteArrayWrapper value = storage.get(key);
            if (value == null) {
                details.delete(key);
            } else {
                details.put(key, value);
            }
        }

        // flush changes to the database
        details.syncStorage();

        FvmContractDetails decoded = FvmContractDetails.decodeAtRoot(fromEncoding(details.getEncoded()), db, details.getStorageHash());

        // check code
        byte[] codeHash = h256(code1);
        assertThat(decoded.getCode(codeHash)).isEqualTo(code1);
        codeHash = h256(code2);
        assertThat(decoded.getCode(codeHash)).isEqualTo(code2);

        // check address
        assertThat(decoded.address).isEqualTo(address);

        // check storage
        for (ByteArrayWrapper key : storage.keySet()) {
            ByteArrayWrapper value = storage.get(key);
            if (value == null) {
                assertThat(decoded.get(key)).isNull();
            } else {
                assertThat(decoded.get(key)).isEqualTo(value);
            }
        }
    }
}
