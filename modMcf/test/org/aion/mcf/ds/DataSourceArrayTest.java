///*******************************************************************************
// * Copyright (c) 2017-2018 Aion foundation.
// *
// *     This file is part of the aion network project.
// *
// *     The aion network project is free software: you can redistribute it
// *     and/or modify it under the terms of the GNU General Public License
// *     as published by the Free Software Foundation, either version 3 of
// *     the License, or any later version.
// *
// *     The aion network project is distributed in the hope that it will
// *     be useful, but WITHOUT ANY WARRANTY; without even the implied
// *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
// *     See the GNU General Public License for more details.
// *
// *     You should have received a copy of the GNU General Public License
// *     along with the aion network project source files.
// *     If not, see <https://www.gnu.org/licenses/>.
// *
// *
// * Contributors:
// *     Aion foundation.
// ******************************************************************************/
//package org.aion.mcf.ds;
//
//import junitparams.JUnitParamsRunner;
//import junitparams.Parameters;
//import org.aion.base.db.IByteArrayKeyValueDatabase;
//import org.aion.crypto.HashUtil;
//import org.aion.db.impl.mockdb.MockDB;
//import org.aion.zero.impl.db.AionBlockStore;
//import org.junit.After;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//
//import java.math.BigInteger;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Random;
//
//import static com.google.common.truth.Truth.assertThat;
//import static org.aion.zero.impl.db.AionBlockStore.BLOCK_INFO_SERIALIZER;
//
///**
// * Tests for {@link DataSourceArray}.
// *
// * @author Alexandra Roatis
// */
//@RunWith(JUnitParamsRunner.class)
//public class DataSourceArrayTest {
//
//    private static List<AionBlockStore.BlockInfo> infoList;
//
//    private static IByteArrayKeyValueDatabase db;
//    private static DataSourceArray<List<AionBlockStore.BlockInfo>> testIndex;
//
//    private static final Random random = new Random();
//
//    static {
//        AionBlockStore.BlockInfo info = new AionBlockStore.BlockInfo();
//        info.setHash(HashUtil.EMPTY_DATA_HASH);
//        info.setCummDifficulty(BigInteger.TEN);
//        info.setMainChain(true);
//
//        infoList = new ArrayList<>();
//        infoList.add(info);
//    }
//
//    @Before
//    public void beforeTest() {
//        db = new MockDB("test_database");
//        db.open();
//        testIndex = new DataSourceArray<>(new ObjectDataSource<>(db, BLOCK_INFO_SERIALIZER));
//    }
//
//    @After
//    public void afterTest() {
//        db.close();
//    }
//
//    /**
//     * @return input values for {@link #testWithInt(int)}
//     */
//    @SuppressWarnings("unused")
//    private Object intValues() {
//
//        List<Object> parameters = new ArrayList<>();
//
//        // integer values
//        parameters.add(0);
//        parameters.add(1);
//        parameters.add(10);
//        parameters.add(random.nextInt(Integer.MAX_VALUE));
//        parameters.add(Integer.MAX_VALUE);
//
//        return parameters.toArray();
//    }
//
//    /**
//     * Checks correct {@link DataSourceArray#set(long, Object)}, {@link DataSourceArray#size()},
//     * {@link DataSourceArray#get(long)} and {@link DataSourceArray#remove(long)}
//     *
//     * @param value
//     *         int values from {@link #intValues()}
//     */
//    @Test
//    @Parameters(method = "intValues")
//    public void testWithInt(int value) {
//        // checking set & size functionality
//        testIndex.set(value, infoList);
//        assertThat(testIndex.size()).isEqualTo(value + 1L);
//
//        // checking get & size functionality
//        assertThat(testIndex.get(value)).isNotNull();
//        assertThat(testIndex.get(value).size()).isEqualTo(1);
//        assertThat(testIndex.size()).isEqualTo(value + 1L);
//
//        // checking remove & size functionality
//        testIndex.remove(value);
//        assertThat(testIndex.size()).isEqualTo((long) value);
//    }
//
//    /**
//     * @return input values for {@link #testWithLong(long)}
//     */
//    @SuppressWarnings("unused")
//    private Object longValues() {
//
//        List<Object> parameters = new ArrayList<>();
//
//        // longs similar to integer values
//        parameters.add(0L);
//        parameters.add(1L);
//        parameters.add(10L);
//        parameters.add((long) random.nextInt(Integer.MAX_VALUE));
//        parameters.add((long) Integer.MAX_VALUE);
//
//        // additional long values
//        parameters.add((long) Integer.MAX_VALUE + random.nextInt(Integer.MAX_VALUE));
//        parameters.add(10L * (long) Integer.MAX_VALUE);
//        parameters.add(Long.MAX_VALUE - 1L);
//
//        return parameters.toArray();
//    }
//
//    /**
//     * Checks correct {@link DataSourceArray#set(long, Object)}, {@link DataSourceArray#size()},
//     * {@link DataSourceArray#get(long)} and {@link DataSourceArray#remove(long)}
//     *
//     * @param value
//     *         long values from {@link #longValues()}
//     */
//    @Test
//    @Parameters(method = "longValues")
//    public void testWithLong(long value) {
//        // checking set & size functionality
//        testIndex.set(value, infoList);
//        assertThat(testIndex.size()).isEqualTo(value + 1L);
//
//        // checking get & size functionality
//        assertThat(testIndex.get(value)).isNotNull();
//        assertThat(testIndex.get(value).size()).isEqualTo(1);
//        assertThat(testIndex.size()).isEqualTo(value + 1L);
//
//        // checking remove & size functionality
//        testIndex.remove(value);
//        assertThat(testIndex.size()).isEqualTo(value);
//    }
//
//    /**
//     * @return input values for {@link #testSetWithNegativeValues(long)}
//     */
//    @SuppressWarnings("unused")
//    private Object negativeAndLargeValues() {
//
//        List<Object> parameters = new ArrayList<>();
//
//        // negative values similar to integer values
//        parameters.add(-1L);
//        parameters.add(-10L);
//        parameters.add(-(long) random.nextInt(Integer.MAX_VALUE));
//        parameters.add(-(long) Integer.MAX_VALUE);
//
//        // negative values similar to long values
//        parameters.add(-(long) Integer.MAX_VALUE + random.nextInt(Integer.MAX_VALUE));
//        parameters.add(-10L * (long) Integer.MAX_VALUE);
//        parameters.add(-(Long.MAX_VALUE - 1L));
//
//        // additional negative values
//        parameters.add(Integer.MIN_VALUE);
//        parameters.add(Long.MIN_VALUE);
//
//        // max long value resulting in negative size
//        parameters.add(Long.MAX_VALUE);
//
//        return parameters.toArray();
//    }
//
//    @Test
//    @Parameters(method = "negativeAndLargeValues")
//    public void testSetWithNegativeValues(long value) {
//        // setting negative or large long value
//        testIndex.set(value, infoList);
//
//        // getting size
//        long currentSize = testIndex.size();
//
//        // checking correct size
//        assertThat(currentSize).isEqualTo(0L);
//    }
//
//    /**
//     * @return input values for {@link #testSetWithIncreasingValues(long, long, long)}
//     */
//    @SuppressWarnings("unused")
//    private Object increasingSets() {
//
//        List<Object> parameters = new ArrayList<>();
//
//        long intMax = (long) Integer.MAX_VALUE;
//
//        // small (int) values
//        parameters.add(new Object[] { -10L, -1L, 0L });
//        parameters.add(new Object[] { -1L, 0L, 1L });
//        parameters.add(new Object[] { 0L, 1L, 2L });
//        parameters.add(new Object[] { 0L, 10L, 100L });
//
//        // small to large transition
//        parameters.add(new Object[] { intMax - 1L, intMax, intMax + 1L });
//        parameters.add(new Object[] { (long) random.nextInt(Integer.MAX_VALUE), intMax, 2 * intMax });
//
//        // large (long) values
//        parameters.add(new Object[] { Long.MAX_VALUE / 6, Long.MAX_VALUE / 4, Long.MAX_VALUE / 2 });
//        parameters.add(new Object[] { Long.MAX_VALUE - 3L, Long.MAX_VALUE - 2L, Long.MAX_VALUE - 1L });
//
//        return parameters.toArray();
//    }
//
//    @Test
//    @Parameters(method = "increasingSets")
//    public void testSetWithIncreasingValues(long value1, long value2, long value3) {
//        // setting values consecutively
//        testIndex.set(value1, infoList);
//        testIndex.set(value2, infoList);
//        testIndex.set(value3, infoList);
//
//        // getting size
//        long currentSize = testIndex.size();
//
//        // checking correct size
//        assertThat(currentSize).isEqualTo(value3 + 1L);
//
//        // checking get & size functionality for last value
//        assertThat(testIndex.get(value3)).isNotNull();
//        assertThat(testIndex.get(value3).size()).isEqualTo(1);
//        assertThat(testIndex.size()).isEqualTo(value3 + 1L);
//
//        // checking remove & size functionality for last value
//        testIndex.remove(value3);
//        assertThat(testIndex.size()).isEqualTo(value3);
//    }
//
//    /**
//     * @return input values for {@link #testSetWithDecreasingValues(long, long)} and
//     *         {@link #testGetWithIndexOutOfBoundsException(long, long)}
//     */
//    @SuppressWarnings("unused")
//    private Object decreasingSets() {
//
//        List<Object> parameters = new ArrayList<>();
//
//        long intMax = (long) Integer.MAX_VALUE;
//
//        // small (int) values
//        parameters.add(new Object[] { 0L, -1L });
//        parameters.add(new Object[] { 1L, 0L });
//        parameters.add(new Object[] { 2L, 1L });
//        parameters.add(new Object[] { 100L, 10L });
//        parameters.add(new Object[] { intMax - 2L, intMax - 3L });
//
//        // values around transition point
//        parameters.add(new Object[] { intMax + 1L, intMax });
//        parameters.add(new Object[] { intMax, intMax - 1L });
//        parameters.add(new Object[] { intMax - 1L, intMax - 2L });
//        parameters.add(new Object[] { intMax, (long) random.nextInt(Integer.MAX_VALUE) });
//        parameters.add(new Object[] { 2 * intMax, intMax });
//
//        // large (long) values
//        parameters.add(new Object[] { Long.MAX_VALUE / 2, Long.MAX_VALUE / 4 });
//        parameters.add(new Object[] { Long.MAX_VALUE / 4, Long.MAX_VALUE / 6 });
//        parameters.add(new Object[] { Long.MAX_VALUE - 1L, Long.MAX_VALUE - 2L });
//        parameters.add(new Object[] { Long.MAX_VALUE - 2L, Long.MAX_VALUE - 3L });
//
//        return parameters.toArray();
//    }
//
//    @Test
//    @Parameters(method = "decreasingSets")
//    public void testSetWithDecreasingValues(long value1, long value2) {
//        // setting values consecutively
//        testIndex.set(value1, infoList);
//        testIndex.set(value2, infoList);
//
//        // getting size
//        long currentSize = testIndex.size();
//
//        // checking correct size
//        assertThat(currentSize).isEqualTo(value1 + 1L);
//
//        // checking get & size functionality for first value
//        assertThat(testIndex.get(value1)).isNotNull();
//        assertThat(testIndex.get(value1).size()).isEqualTo(1);
//        assertThat(testIndex.size()).isEqualTo(value1 + 1L);
//
//        // checking remove & size functionality for first value
//        testIndex.remove(value1);
//        assertThat(testIndex.size()).isEqualTo(value1);
//    }
//
//    @Test(expected = IndexOutOfBoundsException.class)
//    @Parameters(method = "decreasingSets")
//    public void testGetWithIndexOutOfBoundsException(long value1, long value2) {
//        // setting larger value
//        testIndex.set(value2, infoList);
//
//        // get smaller value
//        testIndex.get(value1);
//    }
//}
