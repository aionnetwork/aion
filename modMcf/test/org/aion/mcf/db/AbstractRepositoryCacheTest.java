package org.aion.mcf.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IPruneConfig;
import org.aion.base.db.IRepositoryConfig;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.zero.db.AionRepositoryCache;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AbstractRepositoryCacheTest {
    private AionRepositoryCache cache;

    @Before
    public void setup() {
        IRepositoryConfig repoConfig =
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
        cache = new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
    }

    @After
    public void tearDown() {
        cache = null;
    }

    @Test
    public void testGetStorageValueNoSuchAddress() {
        assertNull(cache.getStorageValue(getNewAddress(), new DataWord(RandomUtils.nextBytes(DataWord.BYTES))));
    }

    @Test
    public void testGetStorageValueIsSingleZero() {
        Address address = getNewAddress();
        IDataWord key = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        cache.addStorageRow(address, key, DataWord.ZERO);
        assertNull(cache.getStorageValue(address, key));

        key = new DoubleDataWord(RandomUtils.nextBytes(DoubleDataWord.BYTES));
        cache.addStorageRow(address, key, DataWord.ZERO);
        assertNull(cache.getStorageValue(address, key));
    }

    @Test
    public void testGetStorageValueIsDoubleZero() {
        Address address = getNewAddress();
        IDataWord key = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        cache.addStorageRow(address, key, DoubleDataWord.ZERO);
        assertNull(cache.getStorageValue(address, key));

        key = new DoubleDataWord(RandomUtils.nextBytes(DoubleDataWord.BYTES));
        cache.addStorageRow(address, key, DoubleDataWord.ZERO);
        assertNull(cache.getStorageValue(address, key));
    }

    @Test
    public void testGetStorageValueWithSingleZeroKey() {
        Address address = getNewAddress();
        IDataWord value = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        cache.addStorageRow(address, DataWord.ZERO, value);
        assertEquals(value, cache.getStorageValue(address, DataWord.ZERO));

        value = new DoubleDataWord(RandomUtils.nextBytes(DoubleDataWord.BYTES));
        cache.addStorageRow(address, DataWord.ZERO, value);
        assertEquals(value, cache.getStorageValue(address, DataWord.ZERO));
    }

    @Test
    public void testGetStorageValueWithDoubleZeroKey() {
        Address address = getNewAddress();
        IDataWord value = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        cache.addStorageRow(address, DoubleDataWord.ZERO, value);
        assertEquals(value, cache.getStorageValue(address, DoubleDataWord.ZERO));

        value = new DoubleDataWord(RandomUtils.nextBytes(DoubleDataWord.BYTES));
        cache.addStorageRow(address, DoubleDataWord.ZERO, value);
        assertEquals(value, cache.getStorageValue(address, DoubleDataWord.ZERO));
    }

    @Test
    public void testGetStorageValueWithZeroKeyAndValue() {
        Address address = getNewAddress();

        // single-single
        cache.addStorageRow(address, DataWord.ZERO, DataWord.ZERO);
        assertNull(cache.getStorageValue(address, DataWord.ZERO));

        // single-double
        cache.addStorageRow(address, DataWord.ZERO, DoubleDataWord.ZERO);
        assertNull(cache.getStorageValue(address, DataWord.ZERO));

        // double-single
        cache.addStorageRow(address, DoubleDataWord.ZERO, DataWord.ZERO);
        assertNull(cache.getStorageValue(address, DoubleDataWord.ZERO));

        // double-double
        cache.addStorageRow(address, DoubleDataWord.ZERO, DoubleDataWord.ZERO);
        assertNull(cache.getStorageValue(address, DoubleDataWord.ZERO));
    }

    @Test
    public void testOverwriteValueWithSingleZero() {
        Address address = getNewAddress();
        IDataWord key = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        IDataWord value = new DoubleDataWord(RandomUtils.nextBytes(DoubleDataWord.BYTES));
        cache.addStorageRow(address, key, value);
        assertEquals(value, cache.getStorageValue(address, key));
        cache.addStorageRow(address, key, DataWord.ZERO);
        assertNull(cache.getStorageValue(address, key));
    }

    @Test
    public void testOverwriteValueWithDoubleZero() {
        Address address = getNewAddress();
        IDataWord key = new DoubleDataWord(RandomUtils.nextBytes(DoubleDataWord.BYTES));
        IDataWord value = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        cache.addStorageRow(address, key, value);
        assertEquals(value, cache.getStorageValue(address, key));
        cache.addStorageRow(address, key, DoubleDataWord.ZERO);
        assertNull(cache.getStorageValue(address, key));
    }

    @Test
    public void testGetStorageValueEnMass() {
        int numEntries = RandomUtils.nextInt(300, 700);
        int deleteOdds = 5;
        int numAddrs = 8;
        List<Address> addresses = getAddressesInBulk(numAddrs);
        List<IDataWord> keys = getKeysInBulk(numEntries);
        List<IDataWord> values = getValuesInBulk(numEntries);

        for (Address address : addresses) {
            massAddToCache(address, keys, values);
            deleteEveryNthEntry(address, keys, deleteOdds);
        }

        for (Address address : addresses) {
            checkStorage(address, keys, values, deleteOdds);
        }
    }

    //<-----------------------------------------HELPERS-------------------------------------------->

    /**
     * Returns a new random address.
     */
    private Address getNewAddress() {
        return new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN));
    }

    private List<Address> getAddressesInBulk(int num) {
        List<Address> addresses = new ArrayList<>(num);
        for (int i = 0; i < num; i++) {
            addresses.add(getNewAddress());
        }
        return addresses;
    }

    /**
     * Checks that cache's storage, given by cache.getStorage(), contains all key-value pairs in
     * keys and values, where it is assumed every n'th pair was deleted.
     */
    private void checkStorage(Address address, List<IDataWord> keys, List<IDataWord> values, int n) {
        Map<IDataWord, IDataWord> storage = cache.getStorage(address, keys);
        int count = 1;
        for (IDataWord key : keys) {
            if (count % n == 0) {
                assertNull(storage.get(key));
            } else {
                assertEquals(values.get(count - 1), storage.get(key));
            }
            count++;
        }
    }

    /**
     * Iterates over every key in keys -- which are assumed to exist in cache -- and then deletes
     * any key-value pair in cache for every n'th key in keys.
     */
    private void deleteEveryNthEntry(Address address, List<IDataWord> keys, int n) {
        int count = 1;
        for (IDataWord key : keys) {
            if (count % n == 0) {
                cache.addStorageRow(address, key, DataWord.ZERO);
            }
            count++;
        }
    }

    /**
     * Puts all of the key-value pairs in keys and values into cache under address.
     */
    private void massAddToCache(Address address, List<IDataWord> keys, List<IDataWord> values) {
        int size = keys.size();
        assertEquals(size, values.size());
        for (int i = 0; i < size; i++) {
            cache.addStorageRow(address, keys.get(i), values.get(i));
        }
    }

    /**
     * Returns a list of numKeys keys, every other one is single and then double.
     */
    private List<IDataWord> getKeysInBulk(int numKeys) {
        List<IDataWord> keys = new ArrayList<>(numKeys);
        boolean isSingleKey = true;
        for (int i = 0; i < numKeys; i++) {
            keys.add(getRandomWord(isSingleKey));
            isSingleKey = !isSingleKey;
        }
        return keys;
    }

    /**
     * Returns a list of numValues values, every other one is single and then double.
     */
    private List<IDataWord> getValuesInBulk(int numValues) {
        List<IDataWord> values = new ArrayList<>(numValues);
        boolean isSingleValue = true;
        for (int i = 0; i < numValues; i++) {
            values.add(getRandomWord(isSingleValue));
            isSingleValue = !isSingleValue;
        }
        return values;
    }

    /**
     * Returns a random DataWord if isSingleWord is true, otherwise a random DoubleDataWord.
     */
    private IDataWord getRandomWord(boolean isSingleWord) {
        return  (isSingleWord) ?
            new DataWord(RandomUtils.nextBytes(DataWord.BYTES)) :
            new DoubleDataWord(RandomUtils.nextBytes(DoubleDataWord.BYTES));
    }

}
