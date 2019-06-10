package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.mcf.db.DatabaseUtils.connectAndOpen;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.interfaces.db.ByteArrayKeyValueDatabase;
import org.aion.interfaces.db.ContractDetails;
import org.aion.interfaces.db.InternalVmType;
import org.aion.interfaces.db.PruneConfig;
import org.aion.interfaces.db.RepositoryConfig;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.trie.JournalPruneDataSource;
import org.aion.mcf.trie.SecureTrie;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.vm.api.types.Address;
import org.aion.vm.api.types.ByteArrayWrapper;
import org.aion.util.bytes.ByteUtil;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import org.slf4j.Logger;

public class AionContractDetailsTest {
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    private static final int IN_MEMORY_STORAGE_LIMIT =
            1000000; // CfgAion.inst().getDb().getDetailsInMemoryStorageLimit();

    protected RepositoryConfig repoConfig =
            new RepositoryConfig() {
                @Override
                public String getDbPath() {
                    return "";
                }

                @Override
                public PruneConfig getPruneConfig() {
                    return new CfgPrune(false);
                }

                @Override
                public ContractDetails contractDetailsImpl() {
                    return ContractDetailsAion.createForTesting(0, 1000000).getDetails();
                }

                @Override
                public Properties getDatabaseConfig(String db_name) {
                    Properties props = new Properties();
                    props.setProperty(DatabaseFactory.Props.DB_TYPE, DBVendor.MOCKDB.toValue());
                    props.setProperty(DatabaseFactory.Props.ENABLE_HEAP_CACHE, "false");
                    return props;
                }
            };

    private static ContractDetails deserialize(
            byte[] rlp, ByteArrayKeyValueDatabase externalStorage) {
        AionContractDetailsImpl result = new AionContractDetailsImpl();
        result.setExternalStorageDataSource(externalStorage);
        result.decode(rlp);

        return result;
    }

    @Test
    public void test_1() throws Exception {

        byte[] code = ByteUtil.hexStringToBytes("60016002");

        byte[] key_1 = ByteUtil.hexStringToBytes("111111");
        byte[] val_1 = ByteUtil.hexStringToBytes("aaaaaa");

        byte[] key_2 = ByteUtil.hexStringToBytes("222222");
        byte[] val_2 = ByteUtil.hexStringToBytes("bbbbbb");

        AionContractDetailsImpl contractDetails =
                new AionContractDetailsImpl(
                        -1, // CfgAion.inst().getDb().getPrune(),
                        1000000 // CfgAion.inst().getDb().getDetailsInMemoryStorageLimit()
                        );
        contractDetails.setCode(code);
        contractDetails.setVmType(InternalVmType.FVM);
        contractDetails.put(
                new DataWordImpl(key_1).toWrapper(), new DataWordImpl(val_1).toWrapper());
        contractDetails.put(
                new DataWordImpl(key_2).toWrapper(), new DataWordImpl(val_2).toWrapper());
        contractDetails.setAddress(Address.ZERO_ADDRESS());

        byte[] data = contractDetails.getEncoded();

        AionContractDetailsImpl contractDetails_ = new AionContractDetailsImpl(data);

        assertEquals(ByteUtil.toHexString(code), ByteUtil.toHexString(contractDetails_.getCode()));

        assertEquals(
                ByteUtil.toHexString(val_1),
                ByteUtil.toHexString(
                        contractDetails_
                                .get(new DataWordImpl(key_1).toWrapper())
                                .getNoLeadZeroesData()));

        assertEquals(
                ByteUtil.toHexString(val_2),
                ByteUtil.toHexString(
                        contractDetails_
                                .get(new DataWordImpl(key_2).toWrapper())
                                .getNoLeadZeroesData()));
    }

    @Test
    public void test_2() throws Exception {

        byte[] code =
                ByteUtil.hexStringToBytes(
                        "7c0100000000000000000000000000000000000000000000000000000000600035046333d546748114610065578063430fe5f01461007c5780634d432c1d1461008d578063501385b2146100b857806357eb3b30146100e9578063dbc7df61146100fb57005b6100766004356024356044356102f0565b60006000f35b61008760043561039e565b60006000f35b610098600435610178565b8073ffffffffffffffffffffffffffffffffffffffff1660005260206000f35b6100c96004356024356044356101a0565b8073ffffffffffffffffffffffffffffffffffffffff1660005260206000f35b6100f1610171565b8060005260206000f35b610106600435610133565b8360005282602052816040528073ffffffffffffffffffffffffffffffffffffffff1660605260806000f35b5b60006020819052908152604090208054600182015460028301546003909301549192909173ffffffffffffffffffffffffffffffffffffffff1684565b5b60015481565b5b60026020526000908152604090205473ffffffffffffffffffffffffffffffffffffffff1681565b73ffffffffffffffffffffffffffffffffffffffff831660009081526020819052604081206002015481908302341080156101fe575073ffffffffffffffffffffffffffffffffffffffff8516600090815260208190526040812054145b8015610232575073ffffffffffffffffffffffffffffffffffffffff85166000908152602081905260409020600101548390105b61023b57610243565b3391506102e8565b6101966103ca60003973ffffffffffffffffffffffffffffffffffffffff3381166101965285166101b68190526000908152602081905260408120600201546101d6526101f68490526102169080f073ffffffffffffffffffffffffffffffffffffffff8616600090815260208190526040902060030180547fffffffffffffffffffffffff0000000000000000000000000000000000000000168217905591508190505b509392505050565b73ffffffffffffffffffffffffffffffffffffffff33166000908152602081905260408120548190821461032357610364565b60018054808201909155600090815260026020526040902080547fffffffffffffffffffffffff000000000000000000000000000000000000000016331790555b50503373ffffffffffffffffffffffffffffffffffffffff1660009081526020819052604090209081556001810192909255600290910155565b3373ffffffffffffffffffffffffffffffffffffffff166000908152602081905260409020600201555600608061019660043960048051602451604451606451600080547fffffffffffffffffffffffff0000000000000000000000000000000000000000908116909517815560018054909516909317909355600355915561013390819061006390396000f3007c0100000000000000000000000000000000000000000000000000000000600035046347810fe381146100445780637e4a1aa81461005557806383d2421b1461006957005b61004f6004356100ab565b60006000f35b6100636004356024356100fc565b60006000f35b61007460043561007a565b60006000f35b6001543373ffffffffffffffffffffffffffffffffffffffff9081169116146100a2576100a8565b60078190555b50565b73ffffffffffffffffffffffffffffffffffffffff8116600090815260026020526040902080547fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff0016600117905550565b6001543373ffffffffffffffffffffffffffffffffffffffff9081169116146101245761012f565b600582905560068190555b505056");
        Address address = Address.wrap(RandomUtils.nextBytes(Address.SIZE));

        byte[] key_0 = ByteUtil.hexStringToBytes("18d63b70aa690ad37cb50908746c9a55");
        byte[] val_0 = ByteUtil.hexStringToBytes("00000000000000000000000000000064");

        byte[] key_1 = ByteUtil.hexStringToBytes("18d63b70aa690ad37cb50908746c9a56");
        byte[] val_1 = ByteUtil.hexStringToBytes("0000000000000000000000000000000c");

        byte[] key_2 = ByteUtil.hexStringToBytes("5a448d1967513482947d1d3f6104316f");

        byte[] key_3 = ByteUtil.hexStringToBytes("5a448d1967513482947d1d3f61043171");
        byte[] val_3 = ByteUtil.hexStringToBytes("00000000000000000000000000000014");

        byte[] key_4 = ByteUtil.hexStringToBytes("18d63b70aa690ad37cb50908746c9a54");

        byte[] key_5 = ByteUtil.hexStringToBytes("5a448d1967513482947d1d3f61043170");
        byte[] val_5 = ByteUtil.hexStringToBytes("00000000000000000000000000000078");

        byte[] key_6 = ByteUtil.hexStringToBytes("c83a08bbccc01a0644d599ccd2a7c2e0");
        byte[] val_6 = ByteUtil.hexStringToBytes("8fbec874791c4e3f9f48a59a44686efe");

        byte[] key_7 = ByteUtil.hexStringToBytes("5aa541c6c03f602a426f04ae47508bb8");
        byte[] val_7 = ByteUtil.hexStringToBytes("7a657031000000000000000000000000");

        byte[] key_8 = ByteUtil.hexStringToBytes("5aa541c6c03f602a426f04ae47508bb9");
        byte[] val_8 = ByteUtil.hexStringToBytes("000000000000000000000000000000c8");

        byte[] key_9 = ByteUtil.hexStringToBytes("5aa541c6c03f602a426f04ae47508bba");
        byte[] val_9 = ByteUtil.hexStringToBytes("0000000000000000000000000000000a");

        byte[] key_10 = ByteUtil.hexStringToBytes("00000000000000000000000000000001");
        byte[] val_10 = ByteUtil.hexStringToBytes("00000000000000000000000000000003");

        byte[] key_11 = ByteUtil.hexStringToBytes("5aa541c6c03f602a426f04ae47508bbb");
        byte[] val_11 = ByteUtil.hexStringToBytes("194bcfc3670d8a1613e5b0c790036a35");

        byte[] key_12 = ByteUtil.hexStringToBytes("aee92919b8c3389af86ef24535e8a28c");
        byte[] val_12 = ByteUtil.hexStringToBytes("cfe293a85bef5915e1a7acb37bf0c685");

        byte[] key_13 = ByteUtil.hexStringToBytes("65c996598dc972688b7ace676c89077b");
        byte[] val_13 = ByteUtil.hexStringToBytes("d6ee27e285f2de7b68e8db25cf1b1063");

        AionContractDetailsImpl contractDetails = new AionContractDetailsImpl();
        contractDetails.setCode(code);
        contractDetails.setVmType(InternalVmType.FVM);
        contractDetails.setAddress(address);
        contractDetails.put(
                new DataWordImpl(key_0).toWrapper(), new DataWordImpl(val_0).toWrapper());
        contractDetails.put(
                new DataWordImpl(key_1).toWrapper(), new DataWordImpl(val_1).toWrapper());
        contractDetails.delete(new DataWordImpl(key_2).toWrapper());
        contractDetails.put(
                new DataWordImpl(key_3).toWrapper(), new DataWordImpl(val_3).toWrapper());
        contractDetails.delete(new DataWordImpl(key_4).toWrapper());
        contractDetails.put(
                new DataWordImpl(key_5).toWrapper(), new DataWordImpl(val_5).toWrapper());
        contractDetails.put(
                new DataWordImpl(key_6).toWrapper(), new DataWordImpl(val_6).toWrapper());
        contractDetails.put(
                new DataWordImpl(key_7).toWrapper(), new DataWordImpl(val_7).toWrapper());
        contractDetails.put(
                new DataWordImpl(key_8).toWrapper(), new DataWordImpl(val_8).toWrapper());
        contractDetails.put(
                new DataWordImpl(key_9).toWrapper(), new DataWordImpl(val_9).toWrapper());
        contractDetails.put(
                new DataWordImpl(key_10).toWrapper(), new DataWordImpl(val_10).toWrapper());
        contractDetails.put(
                new DataWordImpl(key_11).toWrapper(), new DataWordImpl(val_11).toWrapper());
        contractDetails.put(
                new DataWordImpl(key_12).toWrapper(), new DataWordImpl(val_12).toWrapper());
        contractDetails.put(
                new DataWordImpl(key_13).toWrapper(), new DataWordImpl(val_13).toWrapper());

        byte[] data = contractDetails.getEncoded();

        AionContractDetailsImpl contractDetails_ = new AionContractDetailsImpl(data);

        assertEquals(ByteUtil.toHexString(code), ByteUtil.toHexString(contractDetails_.getCode()));

        assertTrue(address.equals(contractDetails_.getAddress()));

        assertEquals(
                ByteUtil.toHexString(val_1),
                ByteUtil.toHexString(
                        contractDetails_.get(new DataWordImpl(key_1).toWrapper()).getData()));

        assertNull(contractDetails_.get(new DataWordImpl(key_2).toWrapper()));

        assertEquals(
                ByteUtil.toHexString(val_3),
                ByteUtil.toHexString(
                        contractDetails_.get(new DataWordImpl(key_3).toWrapper()).getData()));

        assertNull(contractDetails_.get(new DataWordImpl(key_4).toWrapper()));

        assertEquals(
                ByteUtil.toHexString(val_5),
                ByteUtil.toHexString(
                        contractDetails_.get(new DataWordImpl(key_5).toWrapper()).getData()));

        assertEquals(
                ByteUtil.toHexString(val_6),
                ByteUtil.toHexString(
                        contractDetails_.get(new DataWordImpl(key_6).toWrapper()).getData()));

        assertEquals(
                ByteUtil.toHexString(val_7),
                ByteUtil.toHexString(
                        contractDetails_.get(new DataWordImpl(key_7).toWrapper()).getData()));

        assertEquals(
                ByteUtil.toHexString(val_8),
                ByteUtil.toHexString(
                        contractDetails_.get(new DataWordImpl(key_8).toWrapper()).getData()));

        assertEquals(
                ByteUtil.toHexString(val_9),
                ByteUtil.toHexString(
                        contractDetails_.get(new DataWordImpl(key_9).toWrapper()).getData()));

        assertEquals(
                ByteUtil.toHexString(val_10),
                ByteUtil.toHexString(
                        contractDetails_.get(new DataWordImpl(key_10).toWrapper()).getData()));

        assertEquals(
                ByteUtil.toHexString(val_11),
                ByteUtil.toHexString(
                        contractDetails_.get(new DataWordImpl(key_11).toWrapper()).getData()));

        assertEquals(
                ByteUtil.toHexString(val_12),
                ByteUtil.toHexString(
                        contractDetails_.get(new DataWordImpl(key_12).toWrapper()).getData()));

        assertEquals(
                ByteUtil.toHexString(val_13),
                ByteUtil.toHexString(
                        contractDetails_.get(new DataWordImpl(key_13).toWrapper()).getData()));
    }

    @Test
    public void testExternalStorageSerialization() {
        Address address = Address.wrap(RandomUtils.nextBytes(Address.SIZE));
        byte[] code = RandomUtils.nextBytes(512);
        Map<DataWordImpl, DataWordImpl> elements = new HashMap<>();

        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        ByteArrayKeyValueDatabase externalStorage = repository.getDetailsDatabase();

        AionContractDetailsImpl original = new AionContractDetailsImpl(0, 1000000);

        original.setExternalStorageDataSource(externalStorage);
        original.setAddress(address);
        original.setCode(code);
        original.setVmType(InternalVmType.FVM);
        original.externalStorage = true;

        for (int i = 0; i < IN_MEMORY_STORAGE_LIMIT / 64 + 10; i++) {
            DataWordImpl key = new DataWordImpl(RandomUtils.nextBytes(16));
            DataWordImpl value = new DataWordImpl(RandomUtils.nextBytes(16));

            elements.put(key, value);
            original.put(key.toWrapper(), wrapValueForPut(value));
        }

        original.syncStorage();

        byte[] rlp = original.getEncoded();

        AionContractDetailsImpl deserialized = new AionContractDetailsImpl();
        deserialized.setExternalStorageDataSource(externalStorage);
        deserialized.decode(rlp);

        assertEquals(deserialized.externalStorage, true);
        assertTrue(address.equals(deserialized.getAddress()));
        assertEquals(ByteUtil.toHexString(code), ByteUtil.toHexString(deserialized.getCode()));

        for (DataWordImpl key : elements.keySet()) {
            assertEquals(
                    elements.get(key).toWrapper(),
                    wrapValueFromGet(deserialized.get(key.toWrapper())));
        }

        DataWordImpl deletedKey = elements.keySet().iterator().next();

        deserialized.delete(deletedKey.toWrapper());
        deserialized.delete(new DataWordImpl(RandomUtils.nextBytes(16)).toWrapper());
    }

    @Test
    public void testContractStorageSwitch() {
        Address address = Address.wrap(RandomUtils.nextBytes(Address.SIZE));
        byte[] code = RandomUtils.nextBytes(512);
        Map<DataWordImpl, DataWordImpl> elements = new HashMap<>();

        int memstoragelimit = 512;
        AionContractDetailsImpl original = new AionContractDetailsImpl(0, memstoragelimit);

        // getting storage specific properties
        Properties sharedProps;
        sharedProps = repoConfig.getDatabaseConfig("storage");
        sharedProps.setProperty(DatabaseFactory.Props.ENABLE_LOCKING, "false");
        sharedProps.setProperty(DatabaseFactory.Props.DB_PATH, repoConfig.getDbPath());
        sharedProps.setProperty(DatabaseFactory.Props.DB_NAME, "storage");
        ByteArrayKeyValueDatabase storagedb = connectAndOpen(sharedProps, LOG);
        JournalPruneDataSource jpd = new JournalPruneDataSource(storagedb);
        original.setDataSource(jpd);
        original.setAddress(address);
        original.setCode(code);
        original.setVmType(InternalVmType.FVM);

        // the first 2 insertion use memory storage
        for (int i = 0; i < 2; i++) {
            DataWordImpl key = new DataWordImpl(RandomUtils.nextBytes(16));
            DataWordImpl value = new DataWordImpl(RandomUtils.nextBytes(16));

            elements.put(key, value);
            original.put(key.toWrapper(), wrapValueForPut(value));
        }

        original.decode(original.getEncoded());
        original.syncStorage();
        assertTrue(!original.externalStorage);

        // transfer to external storage since 3rd insert
        DataWordImpl key3rd = new DataWordImpl(RandomUtils.nextBytes(16));
        DataWordImpl value = new DataWordImpl(RandomUtils.nextBytes(16));
        elements.put(key3rd, value);
        original.put(key3rd.toWrapper(), wrapValueForPut(value));

        original.decode(original.getEncoded());
        original.syncStorage();
        assertTrue(original.externalStorage);

        byte[] rlp = original.getEncoded();

        AionContractDetailsImpl deserialized = new AionContractDetailsImpl(0, memstoragelimit);
        deserialized.setDataSource(jpd);
        deserialized.decode(rlp);

        assertTrue(deserialized.externalStorage);
        assertEquals(address, deserialized.getAddress());
        assertEquals(ByteUtil.toHexString(code), ByteUtil.toHexString(deserialized.getCode()));

        for (DataWordImpl key : elements.keySet()) {
            assertEquals(
                    elements.get(key).toWrapper(),
                    wrapValueFromGet(deserialized.get(key.toWrapper())));
        }

        DataWordImpl deletedKey = elements.keySet().iterator().next();

        deserialized.delete(deletedKey.toWrapper());
        deserialized.delete(new DataWordImpl(RandomUtils.nextBytes(16)).toWrapper());
    }

    @Test
    public void testExternalStorageTransition() {
        Address address = Address.wrap(RandomUtils.nextBytes(Address.SIZE));
        byte[] code = RandomUtils.nextBytes(512);
        Map<DataWordImpl, DataWordImpl> elements = new HashMap<>();

        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        ByteArrayKeyValueDatabase externalStorage = repository.getDetailsDatabase();

        AionContractDetailsImpl original = new AionContractDetailsImpl(0, 1000000);
        original.setExternalStorageDataSource(externalStorage);
        original.setAddress(address);
        original.setCode(code);
        original.setVmType(InternalVmType.FVM);

        for (int i = 0; i < IN_MEMORY_STORAGE_LIMIT / 64 + 10; i++) {
            DataWordImpl key = new DataWordImpl(RandomUtils.nextBytes(16));
            DataWordImpl value = new DataWordImpl(RandomUtils.nextBytes(16));

            elements.put(key, value);
            original.put(key.toWrapper(), wrapValueForPut(value));
        }

        original.syncStorage();
        assertTrue(!externalStorage.isEmpty());

        ContractDetails deserialized = deserialize(original.getEncoded(), externalStorage);

        // adds keys for in-memory storage limit overflow
        for (int i = 0; i < 10; i++) {
            DataWordImpl key = new DataWordImpl(RandomUtils.nextBytes(16));
            DataWordImpl value = new DataWordImpl(RandomUtils.nextBytes(16));

            elements.put(key, value);

            deserialized.put(key.toWrapper(), wrapValueForPut(value));
        }

        deserialized.syncStorage();
        assertTrue(!externalStorage.isEmpty());

        deserialized = deserialize(deserialized.getEncoded(), externalStorage);

        for (DataWordImpl key : elements.keySet()) {
            assertEquals(
                    elements.get(key).toWrapper(),
                    wrapValueFromGet(deserialized.get(key.toWrapper())));
        }
    }

    private static ByteArrayWrapper wrapValueForPut(DataWordImpl value) {
        return (value.isZero())
                ? new ByteArrayWrapper(value.getData())
                : new ByteArrayWrapper(value.getNoLeadZeroesData());
    }

    private static ByteArrayWrapper wrapValueFromGet(ByteArrayWrapper value) {
        return new ByteArrayWrapper(new DataWordImpl(value.getData()).getData());
    }

    @Test
    public void testEncodingSwitchWithTrie() throws Exception {
        Address address = Address.wrap(RandomUtils.nextBytes(Address.SIZE));
        byte[] code = RandomUtils.nextBytes(512);

        // create old encoding
        byte[] rlpAddress = RLP.encodeElement(address.toBytes());
        byte[] rlpIsExternalStorage = RLP.encodeByte((byte) 0);
        byte[] rlpStorageRoot = RLP.encodeElement(EMPTY_BYTE_ARRAY);
        byte[] rlpStorage = RLP.encodeElement(new SecureTrie(null).serialize());
        byte[] rlpCode = RLP.encodeList(RLP.encodeElement(code));

        byte[] oldEncoding =
                RLP.encodeList(
                        rlpAddress, rlpIsExternalStorage, rlpStorageRoot, rlpStorage, rlpCode);

        // create object using encoding
        AionContractDetailsImpl details = new AionContractDetailsImpl(oldEncoding);

        // check that the VM type is correct
        assertThat(details.getVmType()).isEqualTo(InternalVmType.FVM);

        // check that the encoding has been updated
        byte[] newEncoding =
                RLP.encodeList(
                        rlpAddress,
                        rlpIsExternalStorage,
                        rlpStorageRoot,
                        rlpStorage,
                        rlpCode,
                        RLP.encodeByte(InternalVmType.FVM.getCode()));
        assertThat(details.getEncoded()).isEqualTo(newEncoding);
    }

    @Test
    public void testEncodingSwitchWithRoot() throws Exception {
        Address address = Address.wrap(RandomUtils.nextBytes(Address.SIZE));
        byte[] code = RandomUtils.nextBytes(512);

        // create old encoding

        byte[] rlpAddress = RLP.encodeElement(address.toBytes());
        byte[] rlpIsExternalStorage = RLP.encodeByte((byte) 1);
        byte[] rlpStorageRoot = RLP.encodeElement(RandomUtils.nextBytes(32));
        byte[] rlpStorage = RLP.encodeElement(EMPTY_BYTE_ARRAY);
        byte[] rlpCode = RLP.encodeList(RLP.encodeElement(code));

        byte[] oldEncoding =
                RLP.encodeList(
                        rlpAddress, rlpIsExternalStorage, rlpStorageRoot, rlpStorage, rlpCode);

        // create object using encoding
        AionContractDetailsImpl details = new AionContractDetailsImpl(oldEncoding);

        // check that the VM type is correct
        assertThat(details.getVmType()).isEqualTo(InternalVmType.FVM);

        // check that the encoding has been updated
        byte[] newEncoding =
                RLP.encodeList(
                        rlpAddress,
                        rlpIsExternalStorage,
                        rlpStorageRoot,
                        rlpStorage,
                        rlpCode,
                        RLP.encodeByte(InternalVmType.FVM.getCode()));
        assertThat(details.getEncoded()).isEqualTo(newEncoding);
    }

    @Test
    public void testEncodingWithAVM() throws Exception {
        Address address = Address.wrap(RandomUtils.nextBytes(Address.SIZE));
        byte[] code = RandomUtils.nextBytes(512);

        // create old encoding
        byte[] rlpAddress = RLP.encodeElement(address.toBytes());
        byte[] rlpIsExternalStorage = RLP.encodeByte((byte) 1);
        byte[] rlpStorageRoot = RLP.encodeElement(RandomUtils.nextBytes(32));
        byte[] rlpStorage = RLP.encodeElement(EMPTY_BYTE_ARRAY);
        byte[] rlpCode = RLP.encodeList(RLP.encodeElement(code));

        byte[] oldEncoding =
                RLP.encodeList(
                        rlpAddress,
                        rlpIsExternalStorage,
                        rlpStorageRoot,
                        rlpStorage,
                        rlpCode,
                        RLP.encodeByte(InternalVmType.AVM.getCode()));

        // create object using encoding
        AionContractDetailsImpl details = new AionContractDetailsImpl(oldEncoding);

        // check that the VM type is correct
        assertThat(details.getVmType()).isEqualTo(InternalVmType.AVM);
    }

    @Test
    public void testEncodingSize() {
        Address address = Address.wrap(RandomUtils.nextBytes(Address.SIZE));
        byte[] code = RandomUtils.nextBytes(512);

        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        ByteArrayKeyValueDatabase externalStorage = repository.getDetailsDatabase();

        AionContractDetailsImpl details = new AionContractDetailsImpl(0, 1000000);
        details.setExternalStorageDataSource(externalStorage);
        details.setAddress(address);
        details.setCode(code);
        details.setVmType(InternalVmType.FVM);

        // ensure correct size after VM type is set
        RLPList data = (RLPList) RLP.decode2(details.getEncoded()).get(0);
        assertThat(data.size()).isEqualTo(6);

        // check that the VM type is correct
        assertThat(details.getVmType()).isEqualTo(InternalVmType.FVM);
    }

    @Test(expected = IllegalStateException.class)
    public void testEncodingWithoutVM() {
        Address address = Address.wrap(RandomUtils.nextBytes(Address.SIZE));
        byte[] code = RandomUtils.nextBytes(512);

        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        ByteArrayKeyValueDatabase externalStorage = repository.getDetailsDatabase();

        AionContractDetailsImpl details = new AionContractDetailsImpl(0, 1000000);
        details.setExternalStorageDataSource(externalStorage);
        details.setAddress(address);
        details.setCode(code);

        // if the VM type is not set the encoding has 5 parameters
        RLPList data = (RLPList) RLP.decode2(details.getEncoded()).get(0);
    }
}
