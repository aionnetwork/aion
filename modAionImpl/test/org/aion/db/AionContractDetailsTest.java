/* ******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
package org.aion.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IPruneConfig;
import org.aion.base.db.IRepositoryConfig;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.vm.types.DataWord;
import org.aion.zero.db.AionContractDetailsImpl;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

public class AionContractDetailsTest {

    private static final int IN_MEMORY_STORAGE_LIMIT =
            1000000; // CfgAion.inst().getDb().getDetailsInMemoryStorageLimit();

    protected IRepositoryConfig repoConfig =
            new IRepositoryConfig() {
                @Override
                public String getDbPath() {
                    return "";
                }

                @Override
                public IPruneConfig getPruneConfig() {
                    return new CfgPrune(false);
                }

                @Override
                public IContractDetails contractDetailsImpl() {
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

    private static IContractDetails deserialize(
            byte[] rlp, IByteArrayKeyValueDatabase externalStorage) {
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
        contractDetails.put(new DataWord(key_1), new DataWord(val_1));
        contractDetails.put(new DataWord(key_2), new DataWord(val_2));

        byte[] data = contractDetails.getEncoded();

        AionContractDetailsImpl contractDetails_ = new AionContractDetailsImpl(data);

        assertEquals(ByteUtil.toHexString(code), ByteUtil.toHexString(contractDetails_.getCode()));

        assertEquals(
                ByteUtil.toHexString(val_1),
                ByteUtil.toHexString(
                        contractDetails_.get(new DataWord(key_1)).getNoLeadZeroesData()));

        assertEquals(
                ByteUtil.toHexString(val_2),
                ByteUtil.toHexString(
                        contractDetails_.get(new DataWord(key_2)).getNoLeadZeroesData()));
    }

    @Test
    public void test_2() throws Exception {

        byte[] code =
                ByteUtil.hexStringToBytes(
                        "7c0100000000000000000000000000000000000000000000000000000000600035046333d546748114610065578063430fe5f01461007c5780634d432c1d1461008d578063501385b2146100b857806357eb3b30146100e9578063dbc7df61146100fb57005b6100766004356024356044356102f0565b60006000f35b61008760043561039e565b60006000f35b610098600435610178565b8073ffffffffffffffffffffffffffffffffffffffff1660005260206000f35b6100c96004356024356044356101a0565b8073ffffffffffffffffffffffffffffffffffffffff1660005260206000f35b6100f1610171565b8060005260206000f35b610106600435610133565b8360005282602052816040528073ffffffffffffffffffffffffffffffffffffffff1660605260806000f35b5b60006020819052908152604090208054600182015460028301546003909301549192909173ffffffffffffffffffffffffffffffffffffffff1684565b5b60015481565b5b60026020526000908152604090205473ffffffffffffffffffffffffffffffffffffffff1681565b73ffffffffffffffffffffffffffffffffffffffff831660009081526020819052604081206002015481908302341080156101fe575073ffffffffffffffffffffffffffffffffffffffff8516600090815260208190526040812054145b8015610232575073ffffffffffffffffffffffffffffffffffffffff85166000908152602081905260409020600101548390105b61023b57610243565b3391506102e8565b6101966103ca60003973ffffffffffffffffffffffffffffffffffffffff3381166101965285166101b68190526000908152602081905260408120600201546101d6526101f68490526102169080f073ffffffffffffffffffffffffffffffffffffffff8616600090815260208190526040902060030180547fffffffffffffffffffffffff0000000000000000000000000000000000000000168217905591508190505b509392505050565b73ffffffffffffffffffffffffffffffffffffffff33166000908152602081905260408120548190821461032357610364565b60018054808201909155600090815260026020526040902080547fffffffffffffffffffffffff000000000000000000000000000000000000000016331790555b50503373ffffffffffffffffffffffffffffffffffffffff1660009081526020819052604090209081556001810192909255600290910155565b3373ffffffffffffffffffffffffffffffffffffffff166000908152602081905260409020600201555600608061019660043960048051602451604451606451600080547fffffffffffffffffffffffff0000000000000000000000000000000000000000908116909517815560018054909516909317909355600355915561013390819061006390396000f3007c0100000000000000000000000000000000000000000000000000000000600035046347810fe381146100445780637e4a1aa81461005557806383d2421b1461006957005b61004f6004356100ab565b60006000f35b6100636004356024356100fc565b60006000f35b61007460043561007a565b60006000f35b6001543373ffffffffffffffffffffffffffffffffffffffff9081169116146100a2576100a8565b60078190555b50565b73ffffffffffffffffffffffffffffffffffffffff8116600090815260026020526040902080547fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff0016600117905550565b6001543373ffffffffffffffffffffffffffffffffffffffff9081169116146101245761012f565b600582905560068190555b505056");
        Address address = Address.wrap(RandomUtils.nextBytes(Address.ADDRESS_LEN));

        byte[] key_0 = ByteUtil.hexStringToBytes("18d63b70aa690ad37cb50908746c9a55");
        byte[] val_0 = ByteUtil.hexStringToBytes("00000000000000000000000000000064");

        byte[] key_1 = ByteUtil.hexStringToBytes("18d63b70aa690ad37cb50908746c9a56");
        byte[] val_1 = ByteUtil.hexStringToBytes("0000000000000000000000000000000c");

        byte[] key_2 = ByteUtil.hexStringToBytes("5a448d1967513482947d1d3f6104316f");
        byte[] val_2 = ByteUtil.hexStringToBytes("00000000000000000000000000000000");

        byte[] key_3 = ByteUtil.hexStringToBytes("5a448d1967513482947d1d3f61043171");
        byte[] val_3 = ByteUtil.hexStringToBytes("00000000000000000000000000000014");

        byte[] key_4 = ByteUtil.hexStringToBytes("18d63b70aa690ad37cb50908746c9a54");
        byte[] val_4 = ByteUtil.hexStringToBytes("00000000000000000000000000000000");

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
        contractDetails.setAddress(address);
        contractDetails.put(new DataWord(key_0), new DataWord(val_0));
        contractDetails.put(new DataWord(key_1), new DataWord(val_1));
        contractDetails.put(new DataWord(key_2), new DataWord(val_2));
        contractDetails.put(new DataWord(key_3), new DataWord(val_3));
        contractDetails.put(new DataWord(key_4), new DataWord(val_4));
        contractDetails.put(new DataWord(key_5), new DataWord(val_5));
        contractDetails.put(new DataWord(key_6), new DataWord(val_6));
        contractDetails.put(new DataWord(key_7), new DataWord(val_7));
        contractDetails.put(new DataWord(key_8), new DataWord(val_8));
        contractDetails.put(new DataWord(key_9), new DataWord(val_9));
        contractDetails.put(new DataWord(key_10), new DataWord(val_10));
        contractDetails.put(new DataWord(key_11), new DataWord(val_11));
        contractDetails.put(new DataWord(key_12), new DataWord(val_12));
        contractDetails.put(new DataWord(key_13), new DataWord(val_13));

        byte[] data = contractDetails.getEncoded();

        AionContractDetailsImpl contractDetails_ = new AionContractDetailsImpl(data);

        assertEquals(ByteUtil.toHexString(code), ByteUtil.toHexString(contractDetails_.getCode()));

        assertTrue(address.equals(contractDetails_.getAddress()));

        assertEquals(
                ByteUtil.toHexString(val_1),
                ByteUtil.toHexString(contractDetails_.get(new DataWord(key_1)).getData()));

        assertEquals(
                ByteUtil.toHexString(val_2),
                ByteUtil.toHexString(contractDetails_.get(new DataWord(key_2)).getData()));

        assertEquals(
                ByteUtil.toHexString(val_3),
                ByteUtil.toHexString(contractDetails_.get(new DataWord(key_3)).getData()));

        assertEquals(
                ByteUtil.toHexString(val_4),
                ByteUtil.toHexString(contractDetails_.get(new DataWord(key_4)).getData()));

        assertEquals(
                ByteUtil.toHexString(val_5),
                ByteUtil.toHexString(contractDetails_.get(new DataWord(key_5)).getData()));

        assertEquals(
                ByteUtil.toHexString(val_6),
                ByteUtil.toHexString(contractDetails_.get(new DataWord(key_6)).getData()));

        assertEquals(
                ByteUtil.toHexString(val_7),
                ByteUtil.toHexString(contractDetails_.get(new DataWord(key_7)).getData()));

        assertEquals(
                ByteUtil.toHexString(val_8),
                ByteUtil.toHexString(contractDetails_.get(new DataWord(key_8)).getData()));

        assertEquals(
                ByteUtil.toHexString(val_9),
                ByteUtil.toHexString(contractDetails_.get(new DataWord(key_9)).getData()));

        assertEquals(
                ByteUtil.toHexString(val_10),
                ByteUtil.toHexString(contractDetails_.get(new DataWord(key_10)).getData()));

        assertEquals(
                ByteUtil.toHexString(val_11),
                ByteUtil.toHexString(contractDetails_.get(new DataWord(key_11)).getData()));

        assertEquals(
                ByteUtil.toHexString(val_12),
                ByteUtil.toHexString(contractDetails_.get(new DataWord(key_12)).getData()));

        assertEquals(
                ByteUtil.toHexString(val_13),
                ByteUtil.toHexString(contractDetails_.get(new DataWord(key_13)).getData()));
    }

    @Test
    public void testExternalStorageSerialization() {
        Address address = Address.wrap(RandomUtils.nextBytes(Address.ADDRESS_LEN));
        byte[] code = RandomUtils.nextBytes(512);
        Map<DataWord, DataWord> elements = new HashMap<>();

        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        IByteArrayKeyValueDatabase externalStorage = repository.getDetailsDatabase();

        AionContractDetailsImpl original = new AionContractDetailsImpl(0, 1000000);

        original.setExternalStorageDataSource(externalStorage);
        original.setAddress(address);
        original.setCode(code);
        original.externalStorage = true;

        for (int i = 0; i < IN_MEMORY_STORAGE_LIMIT / 64 + 10; i++) {
            DataWord key = new DataWord(RandomUtils.nextBytes(16));
            DataWord value = new DataWord(RandomUtils.nextBytes(16));

            elements.put(key, value);
            original.put(key, value);
        }

        original.syncStorage();

        byte[] rlp = original.getEncoded();

        AionContractDetailsImpl deserialized = new AionContractDetailsImpl();
        deserialized.setExternalStorageDataSource(externalStorage);
        deserialized.decode(rlp);

        assertEquals(deserialized.externalStorage, true);
        assertTrue(address.equals(deserialized.getAddress()));
        assertEquals(ByteUtil.toHexString(code), ByteUtil.toHexString(deserialized.getCode()));

        for (DataWord key : elements.keySet()) {
            assertEquals(elements.get(key), deserialized.get(key));
        }

        DataWord deletedKey = elements.keySet().iterator().next();

        deserialized.put(deletedKey, DataWord.ZERO);
        deserialized.put(new DataWord(RandomUtils.nextBytes(16)), DataWord.ZERO);
    }

    @Test
    public void testExternalStorageTransition() {
        Address address = Address.wrap(RandomUtils.nextBytes(Address.ADDRESS_LEN));
        byte[] code = RandomUtils.nextBytes(512);
        Map<DataWord, DataWord> elements = new HashMap<>();

        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        IByteArrayKeyValueDatabase externalStorage = repository.getDetailsDatabase();

        AionContractDetailsImpl original = new AionContractDetailsImpl(0, 1000000);
        original.setExternalStorageDataSource(externalStorage);
        original.setAddress(address);
        original.setCode(code);

        for (int i = 0; i < IN_MEMORY_STORAGE_LIMIT / 64 + 10; i++) {
            DataWord key = new DataWord(RandomUtils.nextBytes(16));
            DataWord value = new DataWord(RandomUtils.nextBytes(16));

            elements.put(key, value);
            original.put(key, value);
        }

        original.syncStorage();
        assertTrue(!externalStorage.isEmpty());

        IContractDetails deserialized = deserialize(original.getEncoded(), externalStorage);

        // adds keys for in-memory storage limit overflow
        for (int i = 0; i < 10; i++) {
            DataWord key = new DataWord(RandomUtils.nextBytes(16));
            DataWord value = new DataWord(RandomUtils.nextBytes(16));

            elements.put(key, value);

            deserialized.put(key, value);
        }

        deserialized.syncStorage();
        assertTrue(!externalStorage.isEmpty());

        deserialized = deserialize(deserialized.getEncoded(), externalStorage);

        for (DataWord key : elements.keySet()) {
            assertEquals(elements.get(key), deserialized.get(key));
        }
    }
}
