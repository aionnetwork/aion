package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.mcf.db.ContractDetails;
import org.aion.util.types.AddressUtils;
import org.aion.util.types.DataWord;
import org.aion.util.types.ByteArrayWrapper;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IContractDetailsTest {
    // The two ways of instantiating the cache.
    private ContractDetails cache1, cache2;

    private static final int SINGLE_BYTES = 16;
    private static final int DOUBLE_BYTES = 32;
    private static final ByteArrayWrapper ZERO_WRAPPED_32 = ByteArrayWrapper.wrap(new byte[32]);

    @Before
    public void setup() {
        cache1 = new FvmContractDetails(AddressUtils.ZERO_ADDRESS);
        cache2 = new ContractDetailsCacheImpl(new FvmContractDetails(AddressUtils.ZERO_ADDRESS));
    }

    @After
    public void tearDown() {
        cache1 = null;
        cache2 = null;
    }

    /**
     * Sets the storage to contain the specified key-value mappings.
     *
     * @param contractDetails the contract details that will store the data
     * @param storage the specified mappings
     * @implNote A {@code null} value is interpreted as deletion.
     */
    public void setStorage(ContractDetails contractDetails, Map<ByteArrayWrapper, ByteArrayWrapper> storage) {
        for (Map.Entry<ByteArrayWrapper, ByteArrayWrapper> entry : storage.entrySet()) {
            ByteArrayWrapper key = entry.getKey();
            ByteArrayWrapper value = entry.getValue();

            if (value != null) {
                contractDetails.put(key, value);
            } else {
                contractDetails.delete(key);
            }
        }
    }

    @Test
    public void testGetNoSuchSingleKey() {
        doGetNoSuchSingleKeyTest(cache1);
        doGetNoSuchSingleKeyTest(cache2);
    }

    @Test
    public void testGetNoSuchDoubleKey() {
        doGetNoSuchDoubleKeyTest(cache1);
        doGetNoSuchDoubleKeyTest(cache2);
    }

    @Test
    public void testPutSingleZeroValue() {
        ByteArrayWrapper key =
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        checkGetNonExistentPairing(cache1, key);
        checkGetNonExistentPairing(cache2, key);

        key = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));
        checkGetNonExistentPairing(cache1, key);
        checkGetNonExistentPairing(cache2, key);
    }

    @Test
    public void testPutDoubleZeroValue() {
        ByteArrayWrapper key =
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        checkGetNonExistentPairing(cache1, key);
        checkGetNonExistentPairing(cache2, key);

        key = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));
        checkGetNonExistentPairing(cache1, key);
        checkGetNonExistentPairing(cache2, key);
    }

    @Test
    public void testPutSingleZeroKey() {
        ByteArrayWrapper value =
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        doPutSingleZeroKeyTest(cache1, value);
        doPutSingleZeroKeyTest(cache2, value);

        value = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));
        doPutSingleZeroKeyTest(cache1, value);
        doPutSingleZeroKeyTest(cache2, value);
    }

    @Test
    public void testPutDoubleZeroKey() {
        ByteArrayWrapper value =
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        doPutDoubleZeroKeyTest(cache1, value);
        doPutDoubleZeroKeyTest(cache2, value);

        value = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));
        doPutDoubleZeroKeyTest(cache1, value);
        doPutDoubleZeroKeyTest(cache2, value);
    }

    @Test
    public void testPutZeroKeyAndValue() {
        // Try single-single
        cache1.delete(DataWord.ZERO.toWrapper());
        ByteArrayWrapper result = cache1.get(DataWord.ZERO.toWrapper());
        assertNull(result);
        cache2.delete(DataWord.ZERO.toWrapper());
        assertNull(cache2.get(DataWord.ZERO.toWrapper()));

        // Try single-double
        cache1.delete(DataWord.ZERO.toWrapper());
        result = cache1.get(DataWord.ZERO.toWrapper());
        assertNull(result);
        cache2.delete(DataWord.ZERO.toWrapper());
        assertNull(cache2.get(DataWord.ZERO.toWrapper()));

        // Try double-single
        cache1.delete(ZERO_WRAPPED_32);
        result = cache1.get(ZERO_WRAPPED_32);
        assertNull(result);
        cache2.delete(ZERO_WRAPPED_32);
        assertNull(cache2.get(ZERO_WRAPPED_32));

        // Try double-double
        cache1.delete(ZERO_WRAPPED_32);
        result = cache1.get(ZERO_WRAPPED_32);
        assertNull(result);
        cache2.delete(ZERO_WRAPPED_32);
        assertNull(cache2.get(ZERO_WRAPPED_32));
    }

    @Test
    public void testPutKeyValueThenOverwriteValueWithZero() {
        // single-single
        ByteArrayWrapper key =
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        ByteArrayWrapper value =
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        doPutKeyValueThenOverwriteValueWithZero(cache1, key, value);
        doPutKeyValueThenOverwriteValueWithZero(cache2, key, value);

        // single-double
        value = ByteArrayWrapper.wrap(sixteenToThirtyTwo(RandomUtils.nextBytes(SINGLE_BYTES)));
        doPutKeyValueThenOverwriteValueWithZero(cache1, key, value);
        doPutKeyValueThenOverwriteValueWithZero(cache2, key, value);

        // double-single
        key = ByteArrayWrapper.wrap(sixteenToThirtyTwo(RandomUtils.nextBytes(SINGLE_BYTES)));
        value = new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        doPutKeyValueThenOverwriteValueWithZero(cache1, key, value);
        doPutKeyValueThenOverwriteValueWithZero(cache2, key, value);

        // double-double
        key = ByteArrayWrapper.wrap(sixteenToThirtyTwo(RandomUtils.nextBytes(SINGLE_BYTES)));
        value = ByteArrayWrapper.wrap(sixteenToThirtyTwo(RandomUtils.nextBytes(SINGLE_BYTES)));
        doPutKeyValueThenOverwriteValueWithZero(cache1, key, value);
        doPutKeyValueThenOverwriteValueWithZero(cache2, key, value);
    }

    @Test
    public void testPutAndGetEnMass() {
        int numEntries = RandomUtils.nextInt(1_000, 5_000);
        int deleteOdds = 4;
        List<ByteArrayWrapper> keys = getKeysInBulk(numEntries);
        List<ByteArrayWrapper> values = getValuesInBulk(numEntries);
        massPutIntoCache(cache1, keys, values);
        deleteEveryNthEntry(cache1, keys, deleteOdds);
        checkAllPairs(cache1, keys, values, deleteOdds);

        massPutIntoCache(cache2, keys, values);
        deleteEveryNthEntry(cache2, keys, deleteOdds);
        checkAllPairs(cache2, keys, values, deleteOdds);
    }

    @Test
    public void testGetStorage() {
        int numEntries = RandomUtils.nextInt(1_000, 5_000);
        int deleteOdds = 6;
        List<ByteArrayWrapper> keys = getKeysInBulk(numEntries);
        List<ByteArrayWrapper> values = getValuesInBulk(numEntries);
        massPutIntoCache(cache1, keys, values);
        deleteEveryNthEntry(cache1, keys, deleteOdds);
        checkStorage(cache1, keys, values, deleteOdds);

        massPutIntoCache(cache2, keys, values);
        deleteEveryNthEntry(cache2, keys, deleteOdds);
        checkStorage(cache2, keys, values, deleteOdds);
    }

    @Test
    public void testSetZeroValueViaSetStorage() {
        doSetZeroValueViaStorageTest(cache1);
        doSetZeroValueViaStorageTest(cache2);
    }

    @Test
    public void testSetStorageEnMass() {
        int numEntries = RandomUtils.nextInt(1_000, 5_000);
        int deleteOdds = 7;
        Map<ByteArrayWrapper, ByteArrayWrapper> storage =
                getKeyValueMappingInBulk(numEntries, deleteOdds);
        setStorage(cache1, storage);
        checkKeyValueMapping(cache1, storage);

        setStorage(cache2, storage);
        checkKeyValueMapping(cache2, storage);
    }

    /**
     * This test is specific to the ContractDetailsCacheImpl class, which has a commit method. This
     * test class is not concerned with testing all of the functionality of this method, only with
     * how this method handles zero-byte values.
     */
    @Test
    public void testCommitEnMassOriginalIsAionContract() {
        ContractDetailsCacheImpl impl = new ContractDetailsCacheImpl(cache1);

        int numEntries = RandomUtils.nextInt(1_000, 5_000);
        int deleteOdds = 3;
        List<ByteArrayWrapper> keys = getKeysInBulk(numEntries);
        List<ByteArrayWrapper> values = getValuesInBulk(numEntries);
        massPutIntoCache(impl, keys, values);
        deleteEveryNthEntry(impl, keys, deleteOdds);

        Map<ByteArrayWrapper, ByteArrayWrapper> storage = impl.getStorage(keys);
        assertEquals(0, cache1.getStorage(keys).size());
        impl.commit();
        assertEquals(storage.size(), cache1.getStorage(keys).size());

        int count = 1;
        for (ByteArrayWrapper key : keys) {
            try {
                if (count % deleteOdds == 0) {
                    assertNull(impl.get(key));
                    assertNull(cache1.get(key));
                } else {
                    assertEquals(impl.get(key), cache1.get(key));
                }
            } catch (AssertionError e) {
                System.err.println("\nAssertion failed on key: " + key);
                e.printStackTrace();
            }
            count++;
        }
    }

    /**
     * This test is specific to the ContractDetailsCacheImpl class, which has a commit method. This
     * test class is not concerned with testing all of the functionality of this method, only with
     * how this method handles zero-byte values.
     */
    @Test
    public void testCommitEnMassOriginalIsContractDetails() {
        ContractDetailsCacheImpl impl = new ContractDetailsCacheImpl(cache2);

        int numEntries = RandomUtils.nextInt(1_000, 5_000);
        int deleteOdds = 3;
        List<ByteArrayWrapper> keys = getKeysInBulk(numEntries);
        List<ByteArrayWrapper> values = getValuesInBulk(numEntries);
        massPutIntoCache(impl, keys, values);
        deleteEveryNthEntry(impl, keys, deleteOdds);

        Map<ByteArrayWrapper, ByteArrayWrapper> storage = impl.getStorage(keys);
        assertEquals(0, cache2.getStorage(keys).size());
        impl.commit();
        assertEquals(storage.size(), cache2.getStorage(keys).size());

        int count = 1;
        for (ByteArrayWrapper key : keys) {
            try {
                if (count % deleteOdds == 0) {
                    assertNull(impl.get(key));
                    assertNull(cache2.get(key));
                } else {
                    assertEquals(impl.get(key), cache2.get(key));
                }
            } catch (AssertionError e) {
                System.err.println("\nAssertion failed on key: " + key);
                e.printStackTrace();
            }
            count++;
        }
    }

    /**
     * This test is specific to the ContractDetailsCacheImpl class, which at times holds a different
     * storage value for contracts. This test checks that after an update to the cache object, the
     * original value from the contract details is not returned for use.
     */
    @Test
    public void testCacheUpdatedAndGetWithOriginalAionContract() {

        ByteArrayWrapper key = getRandomWord(true);
        ByteArrayWrapper value1 = getRandomWord(true);
        ByteArrayWrapper value2 = getRandomWord(true);

        // ensure the second value is different
        // unlikely to be necessary
        while (value1.equals(value2)) {
            value2 = getRandomWord(true);
        }

        // ensure the initial cache has the value
        cache1.put(key, value1);

        ContractDetailsCacheImpl impl = new ContractDetailsCacheImpl(cache1);

        // check that original value is retrieved
        assertThat(impl.get(key)).isEqualTo(value1);

        // delete and check that value is missing
        impl.delete(key);
        assertThat(impl.get(key)).isEqualTo(null);

        // add new value and check correctness
        impl.put(key, value2);
        assertThat(impl.get(key)).isEqualTo(value2);

        // clean-up
        cache1.delete(key);
    }

    // <------------------------------------------HELPERS------------------------------------------->

    /**
     * Tests calling get() on a DataWordImpl key that is not in cache -- first on a zero-byte key
     * and then on a random key.
     */
    private void doGetNoSuchSingleKeyTest(ContractDetails cache) {
        checkGetNonExistentPairing(cache, DataWord.ZERO.toWrapper());
        checkGetNonExistentPairing(
                cache, new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper());
    }

    /**
     * Tests calling get() on a DoubleDataWord key that is not in cache -- first on a zero-byte key
     * and then on a random key.
     */
    private void doGetNoSuchDoubleKeyTest(ContractDetails cache) {
        checkGetNonExistentPairing(cache, ZERO_WRAPPED_32);
        checkGetNonExistentPairing(cache, ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES)));
    }

    /** Tests putting value into cache with a zero-byte DataWordImpl key. */
    private void doPutSingleZeroKeyTest(ContractDetails cache, ByteArrayWrapper value) {
        cache.put(DataWord.ZERO.toWrapper(), value);
        assertEquals(value, cache.get(DataWord.ZERO.toWrapper()));
    }

    /** Tests putting value into cache with a zero-byte DoubleDataWord key. */
    private void doPutDoubleZeroKeyTest(ContractDetails cache, ByteArrayWrapper value) {
        cache.put(ZERO_WRAPPED_32, value);
        assertEquals(value, cache.get(ZERO_WRAPPED_32));
    }

    /**
     * Tests putting key and value into cache and then putting a zero-byte data word into cache with
     * key and then calling get() on that key.
     */
    private void doPutKeyValueThenOverwriteValueWithZero(
            ContractDetails cache, ByteArrayWrapper key, ByteArrayWrapper value) {

        // Test DataWordImpl.
        cache.put(key, value);
        assertEquals(value, cache.get(key));
        cache.delete(key);
        checkGetNonExistentPairing(cache, key);

        // Test DoubleDataWord.
        cache.put(key, value);
        assertEquals(value, cache.get(key));
        cache.delete(key);
        checkGetNonExistentPairing(cache, key);
    }

    /**
     * Checks that cache contains all key-value pairs in keys and values, where it is assumed every
     * n'th pair was deleted.
     */
    private void checkAllPairs(
            ContractDetails cache,
            List<ByteArrayWrapper> keys,
            List<ByteArrayWrapper> values,
            int n) {

        int size = keys.size();
        assertEquals(size, values.size());
        int count = 1;
        for (ByteArrayWrapper key : keys) {
            if (count % n == 0) {
                checkGetNonExistentPairing(cache, key);
            } else {
                assertEquals(values.get(count - 1), cache.get(key));
            }
            count++;
        }
    }

    /**
     * Checks that cache's storage, given by cache.getStorage(), contains all key-value pairs in
     * keys and values, where it is assumed every n'th pair was deleted.
     */
    private void checkStorage(
            ContractDetails cache,
            List<ByteArrayWrapper> keys,
            List<ByteArrayWrapper> values,
            int n) {

        Map<ByteArrayWrapper, ByteArrayWrapper> storage = cache.getStorage(keys);
        int count = 1;
        for (ByteArrayWrapper key : keys) {
            if (count % n == 0) {
                try {
                    assertNull(storage.get(key));
                } catch (AssertionError e) {
                    System.err.println(
                            "\nAssertion failed on key: " + key);
                    e.printStackTrace();
                }
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
    private void deleteEveryNthEntry(ContractDetails cache, List<ByteArrayWrapper> keys, int n) {
        int count = 1;
        for (ByteArrayWrapper key : keys) {
            if (count % n == 0) {
                cache.delete(key);
            }
            count++;
        }
    }

    /** Puts all of the key-value pairs in keys and values into cache. */
    private void massPutIntoCache(
            ContractDetails cache, List<ByteArrayWrapper> keys, List<ByteArrayWrapper> values) {

        int size = keys.size();
        assertEquals(size, values.size());
        for (int i = 0; i < size; i++) {
            ByteArrayWrapper value = values.get(i);
            if (value == null || value.isZero()) {
                cache.delete(keys.get(i));
            } else {
                cache.put(keys.get(i), values.get(i));
            }
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

    /**
     * Sets a key-value pair with a zero value via cache.setStorage() and ensures that null is
     * returned when called on that same key.
     */
    private void doSetZeroValueViaStorageTest(ContractDetails cache) {
        Map<ByteArrayWrapper, ByteArrayWrapper> storage = new HashMap<>();
        ByteArrayWrapper key =
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        storage.put(key, null);
        setStorage(cache, storage);
        checkGetNonExistentPairing(cache, key);
    }

    /** Checks cache returns the expected values given its storage is storage. */
    private void checkKeyValueMapping(
            ContractDetails cache, Map<ByteArrayWrapper, ByteArrayWrapper> storage) {

        for (ByteArrayWrapper key : storage.keySet()) {
            ByteArrayWrapper value = storage.get(key);
            if (value == null) {
                checkGetNonExistentPairing(cache, key);
            } else {
                assertEquals(value, cache.get(key));
            }
        }
    }

    /**
     * Returns a key-value mapping with numEntries mappings, where every n'th mapping has a zero
     * value.
     */
    private Map<ByteArrayWrapper, ByteArrayWrapper> getKeyValueMappingInBulk(
            int numEntries, int n) {
        Map<ByteArrayWrapper, ByteArrayWrapper> storage = new HashMap<>(numEntries);
        List<ByteArrayWrapper> keys = getKeysInBulk(numEntries);
        List<ByteArrayWrapper> values = getValuesInBulk(numEntries);
        int size = keys.size();
        assertEquals(size, values.size());
        for (int i = 0; i < size; i++) {
            if ((i + 1) % n == 0) {
                storage.put(keys.get(i), null);
            } else {
                storage.put(keys.get(i), values.get(i));
            }
        }
        return storage;
    }

    /**
     * Assumption: key has no valid value mapping in cache. This method calls cache.get(key) and
     * checks its result.
     */
    private void checkGetNonExistentPairing(ContractDetails cache, ByteArrayWrapper key) {
        try {
            assertNull(cache.get(key));
        } catch (AssertionError e) {
            System.err.println("\nAssertion failed on key: " + key);
            e.printStackTrace();
        }
    }

    private byte[] sixteenToThirtyTwo(byte[] sixteen) {
        byte[] thirtyTwo = new byte[DOUBLE_BYTES];
        System.arraycopy(sixteen, 0, thirtyTwo, DOUBLE_BYTES - sixteen.length, sixteen.length);
        return thirtyTwo;
    }
}
