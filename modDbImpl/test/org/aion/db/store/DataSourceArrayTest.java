package org.aion.db.store;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.db.impl.mockdb.PersistentMockDB;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for {@link DataSourceArray}.
 *
 * @author Alexandra Roatis
 */
@RunWith(JUnitParamsRunner.class)
public class DataSourceArrayTest {

    public static final Logger log = LoggerFactory.getLogger("DB");

    // test serializer
    private static final Serializer<String> STRING_SERIALIZER =
            new Serializer<>() {

                @Override
                public byte[] serialize(String value) {
                    return value.getBytes();
                }

                @Override
                public String deserialize(byte[] bytes) {
                    return new String(bytes);
                }
            };

    private static ByteArrayKeyValueDatabase db;
    private static ArrayStore<String> testStore;
    private static final Random random = new Random();

    @Before
    public void beforeTest() {
        db = new MockDB("test_database", log);
        db.open();
        testStore = Stores.newArrayStore(db, STRING_SERIALIZER);
    }

    @After
    public void afterTest() {
        db.close();
    }

    /** @return input values for {@link #testWithInt(int)} */
    @SuppressWarnings("unused")
    private Object intValues() {

        List<Object> parameters = new ArrayList<>();

        // integer values
        parameters.add(0);
        parameters.add(1);
        parameters.add(10);
        parameters.add(random.nextInt(Integer.MAX_VALUE));
        parameters.add(Integer.MAX_VALUE);

        return parameters.toArray();
    }

    /**
     * Checks correct {@link ArrayStore#set(long, Object)}, {@link ArrayStore#size()}, {@link
     * ArrayStore#get(long)} and {@link ArrayStore#remove(long)}
     *
     * @param index int values from {@link #intValues()}
     */
    @Test
    @Parameters(method = "intValues")
    public void testWithInt(int index) {
        String value = "stored data";

        // checking set & size functionality
        testStore.set(index, value);
        assertThat(testStore.size()).isEqualTo(index + 1L);

        // checking get & size functionality
        assertThat(testStore.get(index)).isEqualTo(value);
        assertThat(testStore.size()).isEqualTo(index + 1L);

        // checking remove & size functionality
        testStore.remove(index);
        assertThat(testStore.size()).isEqualTo((long) index);
    }

    /** @return input values for {@link #testWithLong(long)} */
    @SuppressWarnings("unused")
    private Object longValues() {

        List<Object> parameters = new ArrayList<>();

        // longs similar to integer values
        parameters.add(0L);
        parameters.add(1L);
        parameters.add(10L);
        parameters.add((long) random.nextInt(Integer.MAX_VALUE));
        parameters.add((long) Integer.MAX_VALUE);

        // additional long values
        parameters.add((long) Integer.MAX_VALUE + random.nextInt(Integer.MAX_VALUE));
        parameters.add(10L * (long) Integer.MAX_VALUE);
        parameters.add(Long.MAX_VALUE - 1L);

        return parameters.toArray();
    }

    /**
     * Checks correct {@link ArrayStore#set(long, Object)}, {@link ArrayStore#size()}, {@link
     * ArrayStore#get(long)} and {@link ArrayStore#remove(long)}
     *
     * @param index long values from {@link #longValues()}
     */
    @Test
    @Parameters(method = "longValues")
    public void testWithLong(long index) {
        String value = "stored data";

        // checking set & size functionality
        testStore.set(index, value);
        assertThat(testStore.size()).isEqualTo(index + 1L);

        // checking get & size functionality
        assertThat(testStore.get(index)).isEqualTo(value);
        assertThat(testStore.size()).isEqualTo(index + 1L);

        // checking remove & size functionality
        testStore.remove(index);
        assertThat(testStore.size()).isEqualTo(index);
    }

    /** @return input values for {@link #testSetWithNegativeValues(long)} */
    @SuppressWarnings("unused")
    private Object negativeAndLargeValues() {

        List<Object> parameters = new ArrayList<>();

        // negative values similar to integer values
        parameters.add(-1L);
        parameters.add(-10L);
        parameters.add(-(long) random.nextInt(Integer.MAX_VALUE));
        parameters.add(-(long) Integer.MAX_VALUE);

        // negative values similar to long values
        parameters.add(-(long) Integer.MAX_VALUE + random.nextInt(Integer.MAX_VALUE));
        parameters.add(-10L * (long) Integer.MAX_VALUE);
        parameters.add(-(Long.MAX_VALUE - 1L));

        // additional negative values
        parameters.add(Integer.MIN_VALUE);
        parameters.add(Long.MIN_VALUE);

        // max long value resulting in negative size
        parameters.add(Long.MAX_VALUE);

        return parameters.toArray();
    }

    @Test
    @Parameters(method = "negativeAndLargeValues")
    public void testSetWithNegativeValues(long index) {
        String value = "stored data";

        // setting negative or large long value
        testStore.set(index, value);

        // getting size
        long currentSize = testStore.size();

        // checking correct size
        assertThat(currentSize).isEqualTo(0L);
    }

    /** @return input values for {@link #testSetWithIncreasingValues(long, long, long)} */
    @SuppressWarnings("unused")
    private Object increasingSets() {

        List<Object> parameters = new ArrayList<>();

        long intMax = Integer.MAX_VALUE;

        // small (int) values
        parameters.add(new Object[] {-10L, -1L, 0L});
        parameters.add(new Object[] {-1L, 0L, 1L});
        parameters.add(new Object[] {0L, 1L, 2L});
        parameters.add(new Object[] {0L, 10L, 100L});

        // small to large transition
        parameters.add(new Object[] {intMax - 1L, intMax, intMax + 1L});
        parameters.add(new Object[] {(long) random.nextInt(Integer.MAX_VALUE), intMax, 2 * intMax});

        // large (long) values
        parameters.add(new Object[] {Long.MAX_VALUE / 6, Long.MAX_VALUE / 4, Long.MAX_VALUE / 2});
        parameters.add(
                new Object[] {Long.MAX_VALUE - 3L, Long.MAX_VALUE - 2L, Long.MAX_VALUE - 1L});

        return parameters.toArray();
    }

    @Test
    @Parameters(method = "increasingSets")
    public void testSetWithIncreasingValues(long index1, long index2, long index3) {
        String value1 = "stored data 1";
        String value2 = "stored data 2";
        String value3 = "stored data 3";

        // setting values consecutively
        testStore.set(index1, value1);
        testStore.set(index2, value2);
        testStore.set(index3, value3);

        long expectedSize = index3 + 1L;

        // getting size
        long currentSize = testStore.size();

        // checking correct size
        assertThat(currentSize).isEqualTo(expectedSize);

        // checking get & size functionality for last value
        // note that some sample sets have negative values
        if (index1 >= 0) {
            assertThat(testStore.get(index1)).isEqualTo(value1);
        }
        if (index2 >= 0) {
            assertThat(testStore.get(index2)).isEqualTo(value2);
        }
        assertThat(testStore.get(index3)).isEqualTo(value3);
        assertThat(testStore.size()).isEqualTo(expectedSize);

        // checking remove & size functionality for value > last value
        testStore.remove(expectedSize);
        assertThat(testStore.size()).isEqualTo(expectedSize);

        // checking remove & size functionality for last value
        testStore.remove(index3);
        assertThat(testStore.size()).isEqualTo(index3);
    }

    /**
     * @return input values for {@link #testSetWithDecreasingValues(long, long)} and {@link
     *     #testGetWithIndexOutOfBoundsException(long, long)}
     */
    @SuppressWarnings("unused")
    private Object decreasingSets() {

        List<Object> parameters = new ArrayList<>();

        long intMax = Integer.MAX_VALUE;

        // small (int) values
        parameters.add(new Object[] {0L, -1L});
        parameters.add(new Object[] {1L, 0L});
        parameters.add(new Object[] {2L, 1L});
        parameters.add(new Object[] {100L, 10L});
        parameters.add(new Object[] {intMax - 2L, intMax - 3L});

        // values around transition point
        parameters.add(new Object[] {intMax + 1L, intMax});
        parameters.add(new Object[] {intMax, intMax - 1L});
        parameters.add(new Object[] {intMax - 1L, intMax - 2L});
        parameters.add(new Object[] {intMax, (long) random.nextInt(Integer.MAX_VALUE)});
        parameters.add(new Object[] {2 * intMax, intMax});

        // large (long) values
        parameters.add(new Object[] {Long.MAX_VALUE / 2, Long.MAX_VALUE / 4});
        parameters.add(new Object[] {Long.MAX_VALUE / 4, Long.MAX_VALUE / 6});
        parameters.add(new Object[] {Long.MAX_VALUE - 1L, Long.MAX_VALUE - 2L});
        parameters.add(new Object[] {Long.MAX_VALUE - 2L, Long.MAX_VALUE - 3L});

        return parameters.toArray();
    }

    @Test
    @Parameters(method = "decreasingSets")
    public void testSetWithDecreasingValues(long index1, long index2) {
        String value1 = "stored data 1";
        String value2 = "stored data 2";

        // setting values consecutively
        testStore.set(index1, value1);
        testStore.set(index2, value2);
        long expectedSize = index1 + 1L;

        // getting size
        long currentSize = testStore.size();

        // checking correct size
        assertThat(currentSize).isEqualTo(expectedSize);

        // checking get & size functionality for first value
        assertThat(testStore.get(index1)).isEqualTo(value1);
        // note that some sample sets have negative values
        if (index2 >= 0) {
            assertThat(testStore.get(index2)).isEqualTo(value2);
        }
        assertThat(testStore.size()).isEqualTo(expectedSize);

        // checking remove & size functionality for first value
        testStore.remove(index1);
        assertThat(testStore.size()).isEqualTo(index1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    @Parameters(method = "decreasingSets")
    public void testGetWithIndexOutOfBoundsException(long index1, long index2) {
        String value = "stored data";

        // setting larger value
        testStore.set(index2, value);

        // get smaller value
        testStore.get(index1);
    }

    @Rule public TemporaryFolder testFolder = new TemporaryFolder();

    @Test
    public void testMissingSizeSetAtClose() throws IOException {
        File tempFolder = testFolder.newFolder("folder");
        db = new PersistentMockDB("test_database", tempFolder.getAbsolutePath(), log);
        db.open();
        testStore = Stores.newArrayStore(db, STRING_SERIALIZER);

        // set data
        String value = "stored data";
        testStore.set(10L, value);

        // drop key to imply db failure
        db.delete(DataSourceArray.sizeKey);
        assertThat(db.get(DataSourceArray.sizeKey).isPresent()).isFalse();

        // close should save the key
        testStore.close();

        // reopen db to check
        db.open();
        assertThat(db.get(DataSourceArray.sizeKey).isPresent()).isTrue();
    }

    @Test
    public void testMissingSizeSetAtUpdate() throws IOException {
        File tempFolder = testFolder.newFolder("folder");
        db = new PersistentMockDB("test_database", tempFolder.getAbsolutePath(), log);
        db.open();
        testStore = Stores.newArrayStore(db, STRING_SERIALIZER);

        // set data
        String value = "stored data";
        testStore.set(10L, value);

        // drop key to imply db failure
        db.delete(DataSourceArray.sizeKey);
        assertThat(db.get(DataSourceArray.sizeKey).isPresent()).isFalse();

        // adding another entry should save the key
        testStore.set(11L, value);

        // reopen db to check
        assertThat(db.get(DataSourceArray.sizeKey).isPresent()).isTrue();
    }
}
