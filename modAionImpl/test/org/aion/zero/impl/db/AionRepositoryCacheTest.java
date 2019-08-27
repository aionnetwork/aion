package org.aion.zero.impl.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.db.ContractDetails;
import org.aion.mcf.db.PruneConfig;
import org.aion.util.types.DataWord;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AionRepositoryCacheTest {
    private AionRepositoryCache cache;
    private static final int SINGLE_BYTES = 16;
    private static final int DOUBLE_BYTES = 32;
    private static final ByteArrayWrapper ZERO_WRAPPED_32 = ByteArrayWrapper.wrap(new byte[32]);

    @Before
    public void setup() {
        RepositoryConfig repoConfig =
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
        cache = new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
    }

    @After
    public void tearDown() {
        cache = null;
    }

    @Test
    public void testGetStorageValueNoSuchAddress() {
        assertNull(
                cache.getStorageValue(
                        getNewAddress(),
                        new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper()));
    }

    @Test
    public void testGetStorageValueIsSingleZero() {
        AionAddress address = getNewAddress();
        ByteArrayWrapper key = ByteArrayWrapper.wrap(RandomUtils.nextBytes(SINGLE_BYTES));
        cache.removeStorageRow(address, key);
        assertNull(cache.getStorageValue(address, key));

        key = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));
        cache.removeStorageRow(address, key);
        assertNull(cache.getStorageValue(address, key));
    }

    @Test
    public void testGetStorageValueIsDoubleZero() {
        AionAddress address = getNewAddress();
        ByteArrayWrapper key = ByteArrayWrapper.wrap(RandomUtils.nextBytes(16));
        cache.removeStorageRow(address, key);
        assertNull(cache.getStorageValue(address, key));

        key = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));
        cache.removeStorageRow(address, key);
        assertNull(cache.getStorageValue(address, key));
    }

    @Test
    public void testGetStorageValueWithSingleZeroKey() {
        AionAddress address = getNewAddress();
        ByteArrayWrapper value =
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        cache.addStorageRow(address, DataWord.ZERO.toWrapper(), value);
        assertEquals(value, cache.getStorageValue(address, DataWord.ZERO.toWrapper()));

        value = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));
        cache.addStorageRow(address, DataWord.ZERO.toWrapper(), value);
        assertEquals(value, cache.getStorageValue(address, DataWord.ZERO.toWrapper()));
    }

    @Test
    public void testGetStorageValueWithDoubleZeroKey() {
        AionAddress address = getNewAddress();
        ByteArrayWrapper value =
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        cache.addStorageRow(address, ZERO_WRAPPED_32, value);
        assertEquals(value, cache.getStorageValue(address, ZERO_WRAPPED_32));

        value = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));
        cache.addStorageRow(address, ZERO_WRAPPED_32, value);
        assertEquals(value, cache.getStorageValue(address, ZERO_WRAPPED_32));
    }

    @Test
    public void testGetStorageValueWithZeroKeyAndValue() {
        AionAddress address = getNewAddress();

        // single-single
        cache.removeStorageRow(address, DataWord.ZERO.toWrapper());
        assertNull(cache.getStorageValue(address, DataWord.ZERO.toWrapper()));

        // single-double
        cache.removeStorageRow(address, DataWord.ZERO.toWrapper());
        assertNull(cache.getStorageValue(address, DataWord.ZERO.toWrapper()));

        // double-single
        cache.removeStorageRow(address, ZERO_WRAPPED_32);
        assertNull(cache.getStorageValue(address, ZERO_WRAPPED_32));

        // double-double
        cache.removeStorageRow(address, ZERO_WRAPPED_32);
        assertNull(cache.getStorageValue(address, ZERO_WRAPPED_32));
    }

    @Test
    public void testOverwriteValueWithSingleZero() {
        AionAddress address = getNewAddress();
        ByteArrayWrapper key =
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        ByteArrayWrapper value = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));

        cache.addStorageRow(address, key, value);
        assertEquals(value, cache.getStorageValue(address, key));
        cache.removeStorageRow(address, key);
        assertNull(cache.getStorageValue(address, key));
    }

    @Test
    public void testOverwriteValueWithDoubleZero() {
        AionAddress address = getNewAddress();
        ByteArrayWrapper key = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));

        ByteArrayWrapper value =
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        cache.addStorageRow(address, key, value);
        assertEquals(value, cache.getStorageValue(address, key));
        cache.removeStorageRow(address, key);
        assertNull(cache.getStorageValue(address, key));
    }

    @Test
    public void testGetStorageValueEnMass() {
        int numEntries = RandomUtils.nextInt(300, 700);
        int deleteOdds = 5;
        int numAddrs = 8;
        List<AionAddress> addresses = getAddressesInBulk(numAddrs);
        List<ByteArrayWrapper> keys = getKeysInBulk(numEntries);
        List<ByteArrayWrapper> values = getValuesInBulk(numEntries);

        for (AionAddress address : addresses) {
            massAddToCache(address, keys, values);
            deleteEveryNthEntry(address, keys, deleteOdds);
        }

        for (AionAddress address : addresses) {
            checkStorage(address, keys, values, deleteOdds);
        }
    }

    // <-----------------------------------------HELPERS-------------------------------------------->

    /** Returns a new random address. */
    private AionAddress getNewAddress() {
        return new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
    }

    private List<AionAddress> getAddressesInBulk(int num) {
        List<AionAddress> addresses = new ArrayList<>(num);
        for (int i = 0; i < num; i++) {
            addresses.add(getNewAddress());
        }
        return addresses;
    }

    /**
     * Checks that cache's storage, given by cache.getStorage(), contains all key-value pairs in
     * keys and values, where it is assumed every n'th pair was deleted.
     */
    private void checkStorage(
            AionAddress address,
            List<ByteArrayWrapper> keys,
            List<ByteArrayWrapper> values,
            int n) {
        Map<ByteArrayWrapper, ByteArrayWrapper> storage = cache.getStorage(address, keys);
        int count = 1;
        for (ByteArrayWrapper key : keys) {
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
    private void deleteEveryNthEntry(AionAddress address, List<ByteArrayWrapper> keys, int n) {
        int count = 1;
        for (ByteArrayWrapper key : keys) {
            if (count % n == 0) {
                cache.removeStorageRow(address, key);
            }
            count++;
        }
    }

    /** Puts all of the key-value pairs in keys and values into cache under address. */
    private void massAddToCache(
            AionAddress address, List<ByteArrayWrapper> keys, List<ByteArrayWrapper> values) {
        int size = keys.size();
        assertEquals(size, values.size());
        for (int i = 0; i < size; i++) {
            cache.addStorageRow(address, keys.get(i), values.get(i));
        }
    }

    /** Returns a list of numKeys keys, every other one is single and then double. */
    private List<ByteArrayWrapper> getKeysInBulk(int numKeys) {
        List<ByteArrayWrapper> keys = new ArrayList<>(numKeys);
        boolean isSingleKey = true;
        for (int i = 0; i < numKeys; i++) {
            keys.add(getRandomWord(isSingleKey));
            isSingleKey = !isSingleKey;
        }
        return keys;
    }

    /** Returns a list of numValues values, every other one is single and then double. */
    private List<ByteArrayWrapper> getValuesInBulk(int numValues) {
        List<ByteArrayWrapper> values = new ArrayList<>(numValues);
        boolean isSingleValue = true;
        for (int i = 0; i < numValues; i++) {
            values.add(getRandomWord(isSingleValue));
            isSingleValue = !isSingleValue;
        }
        return values;
    }

    /** Returns a random byte array of length 16 if isSingleWord is true, otherwise length 32. */
    private ByteArrayWrapper getRandomWord(boolean isSingleWord) {
        return (isSingleWord)
                ? ByteArrayWrapper.wrap(RandomUtils.nextBytes(SINGLE_BYTES))
                : ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));
    }
}
