package org.aion.mcf.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.base.db.IContractDetails;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.zero.db.AionContractDetailsImpl;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IContractDetailsTest {
    // The two ways of instantiating the cache.
    private IContractDetails<IDataWord> cache1, cache2;

    @Before
    public void setup() {
        IContractDetails<IDataWord> details = new AionContractDetailsImpl();
        cache1 = new ContractDetailsCacheImpl(details);
        details = new ContractDetailsCacheImpl(details);
        cache2 = new ContractDetailsCacheImpl(details);
    }

    @After
    public void tearDown() {
        cache1 = null;
        cache2 = null;
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
        IDataWord key = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        doPutSingleZeroValueTest(cache1, key);
        doPutSingleZeroValueTest(cache2, key);

        key = new DoubleDataWord(RandomUtils.nextBytes(DoubleDataWord.BYTES));
        doPutSingleZeroValueTest(cache1, key);
        doPutSingleZeroValueTest(cache2, key);
    }

    @Test
    public void testPutDoubleZeroValue() {
        IDataWord key = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        doPutDoubleZeroValueTest(cache1, key);
        doPutDoubleZeroValueTest(cache2, key);

        key = new DoubleDataWord(RandomUtils.nextBytes(DoubleDataWord.BYTES));
        doPutDoubleZeroValueTest(cache1, key);
        doPutDoubleZeroValueTest(cache2, key);
    }

    @Test
    public void testPutSingleZeroKey() {
        IDataWord value = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        doPutSingleZeroKeyTest(cache1, value);
        doPutSingleZeroKeyTest(cache2, value);

        value = new DoubleDataWord(RandomUtils.nextBytes(DoubleDataWord.BYTES));
        doPutSingleZeroKeyTest(cache1, value);
        doPutSingleZeroKeyTest(cache2, value);
    }

    @Test
    public void testPutDoubleZeroKey() {
        IDataWord value = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        doPutDoubleZeroKeyTest(cache1, value);
        doPutDoubleZeroKeyTest(cache2, value);

        value = new DoubleDataWord(RandomUtils.nextBytes(DoubleDataWord.BYTES));
        doPutDoubleZeroKeyTest(cache1, value);
        doPutDoubleZeroKeyTest(cache2, value);
    }

    @Test
    public void testPutZeroKeyAndValue() {
        // Try single-single
        cache1.put(DataWord.ZERO, DataWord.ZERO);
        assertNull(cache1.get(DataWord.ZERO));
        cache2.put(DataWord.ZERO, DataWord.ZERO);
        assertNull(cache2.get(DataWord.ZERO));

        // Try single-double
        cache1.put(DataWord.ZERO, DoubleDataWord.ZERO);
        assertNull(cache1.get(DataWord.ZERO));
        cache2.put(DataWord.ZERO, DoubleDataWord.ZERO);
        assertNull(cache2.get(DataWord.ZERO));

        // Try double-single
        cache1.put(DoubleDataWord.ZERO, DataWord.ZERO);
        assertNull(cache1.get(DoubleDataWord.ZERO));
        cache2.put(DoubleDataWord.ZERO, DataWord.ZERO);
        assertNull(cache2.get(DoubleDataWord.ZERO));

        // Try double-double
        cache1.put(DoubleDataWord.ZERO, DoubleDataWord.ZERO);
        assertNull(cache1.get(DoubleDataWord.ZERO));
        cache2.put(DoubleDataWord.ZERO, DoubleDataWord.ZERO);
        assertNull(cache2.get(DoubleDataWord.ZERO));
    }

    @Test
    public void testPutKeyValueThenOverwriteValueWithZero() {
        // single-single
        IDataWord key = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        IDataWord value = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        doPutKeyValueThenOverwriteValueWithZero(cache1, key, value);
        doPutKeyValueThenOverwriteValueWithZero(cache2, key, value);

        // single-double
        value = new DoubleDataWord(RandomUtils.nextBytes(DataWord.BYTES));
        doPutKeyValueThenOverwriteValueWithZero(cache1, key, value);
        doPutKeyValueThenOverwriteValueWithZero(cache2, key, value);

        // double-single
        key = new DoubleDataWord(RandomUtils.nextBytes(DataWord.BYTES));
        value = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        doPutKeyValueThenOverwriteValueWithZero(cache1, key, value);
        doPutKeyValueThenOverwriteValueWithZero(cache2, key, value);

        // double-double
        key = new DoubleDataWord(RandomUtils.nextBytes(DataWord.BYTES));
        value = new DoubleDataWord(RandomUtils.nextBytes(DataWord.BYTES));
        doPutKeyValueThenOverwriteValueWithZero(cache1, key, value);
        doPutKeyValueThenOverwriteValueWithZero(cache2, key, value);
    }

    @Test
    public void testPutAndGetEnMass() {
        int numEntries = RandomUtils.nextInt(1_000, 5_000);
        int deleteOdds = 4;
        List<IDataWord> keys = getKeysInBulk(numEntries);
        List<IDataWord> values = getValuesInBulk(numEntries);
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
        List<IDataWord> keys = getKeysInBulk(numEntries);
        List<IDataWord> values = getValuesInBulk(numEntries);
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
        Map<IDataWord, IDataWord> storage = getKeyValueMappingInBulk(numEntries, deleteOdds);
        cache1.setStorage(storage);
        checkKeyValueMapping(cache1, storage);

        cache2.setStorage(storage);
        checkKeyValueMapping(cache2, storage);
    }

    //<------------------------------------------HELPERS------------------------------------------->

    /**
     * Calls cache.get() under assumption that cache has no key-values stored in it.
     */
    private void doGetNoSuchSingleKeyTest(IContractDetails<IDataWord> cache) {
        // First try out the zero data word.
        assertNull(cache.get(DataWord.ZERO));

        // Try a random data word.
        assertNull(cache.get(new DataWord(RandomUtils.nextBytes(DataWord.BYTES))));
    }

    /**
     * Calls cache.get() under assumption that cache has no key-values stored in it.
     */
    private void doGetNoSuchDoubleKeyTest(IContractDetails<IDataWord> cache) {
        // First try out the zero data word.
        assertNull(cache.get(DoubleDataWord.ZERO));

        // Try a random data word.
        assertNull(cache.get(new DoubleDataWord(RandomUtils.nextBytes(DoubleDataWord.BYTES))));
    }

    /**
     * Puts a zero data word into cache with key and ensures the entry does not exist.
     */
    private void doPutSingleZeroValueTest(IContractDetails<IDataWord> cache, IDataWord key) {
        cache.put(key, DataWord.ZERO);
        assertNull(cache.get(key));
    }

    /**
     * Puts a zero data word into cache with key and ensures the entry does not exist.
     */
    private void doPutDoubleZeroValueTest(IContractDetails<IDataWord> cache, IDataWord key) {
        cache.put(key, DoubleDataWord.ZERO);
        assertNull(cache.get(key));
    }

    /**
     * Puts value into cache with a zero data word key and ensures the entry does not exist.
     */
    private void doPutSingleZeroKeyTest(IContractDetails<IDataWord> cache, IDataWord value) {
        cache.put(DataWord.ZERO, value);
        assertEquals(value, cache.get(DataWord.ZERO));
    }

    /**
     * Puts value into cache with a zero data word key and ensures the entry does not exist.
     */
    private void doPutDoubleZeroKeyTest(IContractDetails<IDataWord> cache, IDataWord value) {
        cache.put(DoubleDataWord.ZERO, value);
        assertEquals(value, cache.get(DoubleDataWord.ZERO));
    }

    /**
     * Puts the key-value pair key and value into cache and then adds a zero word to the key and
     * ensures that key-value pair is deleted.
     */
    private void doPutKeyValueThenOverwriteValueWithZero(IContractDetails<IDataWord> cache,
        IDataWord key, IDataWord value) {

        cache.put(key, value);
        assertEquals(value, cache.get(key));
        cache.put(key, DataWord.ZERO);
        assertNull(cache.get(key));
        cache.put(key, value);
        assertEquals(value, cache.get(key));
        cache.put(key, DoubleDataWord.ZERO);
        assertNull(cache.get(key));
    }

    /**
     * Checks that cache contains all key-value pairs in keys and values, where it is assumed every
     * n'th pair was deleted.
     */
    private void checkAllPairs(IContractDetails<IDataWord> cache, List<IDataWord> keys,
        List<IDataWord> values, int n) {

        int size = keys.size();
        assertEquals(size, values.size());
        int count = 1;
        for (IDataWord key : keys) {
            if (count % n == 0) {
                assertNull(cache.get(key));
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
    private void checkStorage(IContractDetails<IDataWord> cache, List<IDataWord> keys,
        List<IDataWord> values, int n) {

        Map<IDataWord, IDataWord> storage = cache.getStorage(keys);
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
    private void deleteEveryNthEntry(IContractDetails<IDataWord> cache, List<IDataWord> keys, int n) {
        int count = 1;
        for (IDataWord key : keys) {
            if (count % n == 0) {
                cache.put(key, DataWord.ZERO);
            }
            count++;
        }
    }

    /**
     * Puts all of the key-value pairs in keys and values into cache.
     */
    private void massPutIntoCache(IContractDetails<IDataWord> cache, List<IDataWord> keys,
        List<IDataWord> values) {

        int size = keys.size();
        assertEquals(size, values.size());
        for (int i = 0; i < size; i++) {
            cache.put(keys.get(i), values.get(i));
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

    /**
     * Sets a key-value pair with a zero value via cache.setStorage() and ensures that null is
     * returned when called on that same key.
     */
    private void doSetZeroValueViaStorageTest(IContractDetails<IDataWord> cache) {
        Map<IDataWord, IDataWord> storage = new HashMap<>();
        IDataWord key = new DataWord(RandomUtils.nextBytes(DataWord.BYTES));
        storage.put(key, DataWord.ZERO);
        cache.setStorage(storage);
        assertNull(cache.get(key));
    }

    /**
     * Checks cache returns the expected values given its storage is storage.
     */
    private void checkKeyValueMapping(IContractDetails<IDataWord> cache,
        Map<IDataWord, IDataWord> storage) {

        for (IDataWord key : storage.keySet()) {
            IDataWord value = storage.get(key);
            if (value.isZero()) {
                assertNull(cache.get(key));
            } else {
                assertEquals(value, cache.get(key));
            }
        }
    }

    /**
     * Returns a key-value mapping with numEntries mappings, where every n'th mapping has a zero
     * value.
     */
    private Map<IDataWord, IDataWord> getKeyValueMappingInBulk(int numEntries, int n) {
        Map<IDataWord, IDataWord> storage = new HashMap<>(numEntries);
        List<IDataWord> keys = getKeysInBulk(numEntries);
        List<IDataWord> values = getValuesInBulk(numEntries);
        int size = keys.size();
        assertEquals(size, values.size());
        for (int i = 0; i < size; i++) {
            if ((i + 1) % n == 0) {
                storage.put(keys.get(i), DoubleDataWord.ZERO);
            } else {
                storage.put(keys.get(i), values.get(i));
            }
        }
        return storage;
    }

}
