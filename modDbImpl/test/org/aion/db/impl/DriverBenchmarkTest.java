/*******************************************************************************
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
 *     H2 Group.
 ******************************************************************************/
package org.aion.db.impl;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.db.impl.h2.H2MVMap;
import org.aion.db.impl.leveldb.LevelDB;
import org.aion.db.impl.rocksdb.RocksDBConstants;
import org.aion.db.impl.rocksdb.RocksDBWrapper;
import org.aion.db.utils.FileUtils;
import org.aion.db.utils.repeat.Repeat;
import org.aion.db.utils.repeat.RepeatRule;
import org.aion.db.utils.slices.Slice;
import org.aion.db.utils.slices.SliceOutput;
import org.aion.db.utils.slices.Slices;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Charsets.UTF_8;
import static org.junit.Assert.*;

@Ignore
@RunWith(Parameterized.class)
public class DriverBenchmarkTest {

    @Rule
    public TestName name = new TestName();

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    public static File testDir = new File(System.getProperty("user.dir"), "tmp");

    @Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "H2MVMap", new H2MVMap("H2MVMapTest", testDir.getAbsolutePath(), false, false) },
                { "LevelDB", new LevelDB("LevelDBTest", testDir.getAbsolutePath(), false, false) },
                { "RocksDb", new RocksDBWrapper("RocksDb", testDir.getAbsolutePath(), false,false , RocksDBConstants.MAX_OPEN_FILES, RocksDBConstants.BLOCK_SIZE, RocksDBConstants.WRITE_BUFFER_SIZE, RocksDBConstants.READ_BUFFER_SIZE, RocksDBConstants.CACHE_SIZE )}
        });
    }

    public IByteArrayKeyValueDatabase db;
    public String testName;

    private final RandomGenerator generator;
    private final Random random;

    // Every test invocation instantiates a new IByteArrayKeyValueDB
    public DriverBenchmarkTest(String testName, IByteArrayKeyValueDatabase db) {
        this.db = db;
        this.testName = testName;

        generator = new RandomGenerator(compressionRatio);
        random = new Random(301);

    }

    @BeforeClass
    public static void setup() {
        // clean out the tmp directory
        if (testDir.exists()) { assertTrue(FileUtils.deleteRecursively(testDir)); }
        assertTrue(testDir.mkdirs());

        printHeader();
    }

    @AfterClass
    public static void teardown() {
        assertTrue(testDir.delete());
    }

    @Before
    public void open() {
        assertNotNull(db);
        assertFalse(db.isOpen());
        assertTrue(db.isClosed());

        assertTrue(db.open());

        assertTrue(db.isOpen());
        assertFalse(db.isClosed());
        assertTrue(db.isEmpty());
    }

    @After
    public void close() {
        assertNotNull(db);
        assertTrue(db.isOpen());
        assertFalse(db.isClosed());

        db.close();

        assertFalse(db.isOpen());
        assertTrue(db.isClosed());

        // for non-persistant DB's, close() should wipe the DB
        if (db.isPersistent()) {
            File dbDir = new File(db.getPath().get());
            if (dbDir.exists()) { assertTrue(FileUtils.deleteRecursively(dbDir)); }
        }
    }

    // ---------------------------------------------------------------
    // ========================= Unit Tests ==========================
    // ---------------------------------------------------------------

    // Arrange to generate values that shrink to this fraction
    // of their original size after compression
    private static final double compressionRatio = 0.5d;

    private static final int keyCount = (int) 1e6;
    private static final int valueSizeBytes = 100;
    private static final int keySizeBytes = 16;

    @Ignore
    @Repeat(10)
    @Test
    public void fillSequentialKeys() {
        int batchSizeBytes = 1;
        int keyCount = DriverBenchmarkTest.keyCount;
        int valueSizeBytes = DriverBenchmarkTest.valueSizeBytes;

        start();
        write(Order.SEQUENTIAL, keyCount, valueSizeBytes, batchSizeBytes);
        stop(name.getMethodName(), keyCount, valueSizeBytes, batchSizeBytes);
    }

    @Ignore
    @Repeat(10)
    @Test
    public void fillSequentialKeysBatch1K() {
        int batchSizeBytes = 1000;
        int keyCount = DriverBenchmarkTest.keyCount;
        int valueSizeBytes = DriverBenchmarkTest.valueSizeBytes;

        start();
        write(Order.SEQUENTIAL, keyCount, valueSizeBytes, batchSizeBytes);
        stop(name.getMethodName(), keyCount, valueSizeBytes, batchSizeBytes);
    }

    @Ignore
    @Repeat(10)
    @Test
    public void fillRandom() {
        int batchSizeBytes = 1;
        int keyCount = DriverBenchmarkTest.keyCount;
        int valueSizeBytes = DriverBenchmarkTest.valueSizeBytes;

        start();
        write(Order.RANDOM, keyCount, valueSizeBytes, batchSizeBytes);
        stop(name.getMethodName(), keyCount, valueSizeBytes, batchSizeBytes);
    }

    @Ignore
    @Repeat(10)
    @Test
    public void fillRandomBatch1K() {
        int batchSizeBytes = 1000;
        int keyCount = DriverBenchmarkTest.keyCount;
        int valueSizeBytes = DriverBenchmarkTest.valueSizeBytes;

        start();
        write(Order.RANDOM, keyCount, valueSizeBytes, batchSizeBytes);
        stop(name.getMethodName(), keyCount, valueSizeBytes, batchSizeBytes);
    }

    @Ignore
    @Repeat(10)
    @Test
    public void fillRandomValue10K() {
        int batchSizeBytes = 1;
        int keyCount = (int) 1e4;
        int valueSizeBytes = (int) 1e5;

        start();
        write(Order.RANDOM, keyCount, valueSizeBytes, batchSizeBytes);
        stop(name.getMethodName(), keyCount, valueSizeBytes, batchSizeBytes);
    }

    @Ignore
    @Repeat(10)
    @Test
    public void overwriteRandom() {
        // fill DB values, unmeasured
        write(Order.SEQUENTIAL, DriverBenchmarkTest.keyCount, DriverBenchmarkTest.valueSizeBytes, 1);

        db.close();
        assertTrue(db.isClosed());
        long fileSizeInitial = FileUtils.getDirectorySizeBytes(db.getPath().get());
        assertTrue(db.open());

        // now we are always over-writing the values
        int batchSizeBytes = 1;
        int keyCount = DriverBenchmarkTest.keyCount;
        int valueSizeBytes = DriverBenchmarkTest.valueSizeBytes;

        start();
        overwrite(Order.RANDOM, keyCount, valueSizeBytes, batchSizeBytes);
        stop(name.getMethodName(), keyCount, valueSizeBytes, batchSizeBytes);

        db.close();
        assertTrue(db.isClosed());
        long fileSizeFinal = FileUtils.getDirectorySizeBytes(db.getPath().get());
        assertTrue(db.open());

        // make sure the delta in file-size after overwrite operation
        // is within 10% of original file size
        float fileSizeDelta = (float) Math.abs(fileSizeFinal - fileSizeInitial) / (float) fileSizeInitial;
        // System.out.printf("fileSizeDelta: %.5f", fileSizeDelta);
        assertTrue(fileSizeDelta < 0.1f);
    }

    @Ignore
    @Repeat(10)
    @Test
    public void readSequential() { // fill DB values, unmeasured
        write(Order.SEQUENTIAL, keyCount, valueSizeBytes, 1);

        int keyCount = (int) 1e6;
        int valueSizeBytes = DriverBenchmarkTest.valueSizeBytes;
        int batchSizeBytes = 1;

        start();
        for (long k = 0; k < keyCount; k++) {
            byte[] key = formatNumber(k);
            byte[] value = db.get(formatNumber(k)).get();
            byteCount += key.length + value.length;
            finishedSingleOp();
        }
        stop(name.getMethodName(), keyCount, valueSizeBytes, batchSizeBytes);
    }

    @Ignore
    @Repeat(10)
    @Test
    public void readRandom() { // fill DB values, unmeasured
        write(Order.SEQUENTIAL, keyCount, valueSizeBytes, 1);

        int keyCount = (int) 1e6;
        int valueSizeBytes = DriverBenchmarkTest.valueSizeBytes;
        int batchSizeBytes = 1;

        start();
        for (int i = 0; i < keyCount; i++) {
            byte[] key = formatNumber(random.nextInt(keyCount));
            byte[] value = db.get(key).get();
            byteCount += key.length + value.length;
            finishedSingleOp();
        }
        stop(name.getMethodName(), keyCount, valueSizeBytes, batchSizeBytes);
    }
    // ---------------------------------------------------------------
    // ========================= Test Cases ==========================
    // ---------------------------------------------------------------

    enum Order {
        SEQUENTIAL, RANDOM
    }

    // same as write, except with assertion that every write has to override an
    // existing value
    private void overwrite(Order order, int numEntries, int valueSize, int entriesPerBatch) {

        if (entriesPerBatch < 1) {
            System.out.println("invalid entriesPerBatch");
            return;
        }

        // non batch insert
        if (entriesPerBatch == 1) {
            for (long i = 0; i < numEntries; i++) {
                long k = (order == Order.SEQUENTIAL) ? i : random.nextInt(numEntries);
                byte[] key = formatNumber(k);
                assertTrue(db.get(key).isPresent());
                byteCount += valueSize + key.length;
                finishedSingleOp();
            }
        } else // batch insert
        {
            for (int i = 0; i < numEntries; i += entriesPerBatch) {
                Map<byte[], byte[]> batch = new HashMap<>();
                for (int j = 0; j < entriesPerBatch; j++) {
                    int k = (order == Order.SEQUENTIAL) ? i + j : random.nextInt(numEntries);
                    byte[] key = formatNumber(k);
                    assertTrue(db.get(key).isPresent());
                    batch.put(key, generator.generate(valueSize));
                    byteCount += valueSize + key.length;
                    finishedSingleOp();
                }
                db.putBatch(batch);
            }
        }
    }

    private void write(Order order, int numEntries, int valueSize, int entriesPerBatch) {

        if (entriesPerBatch < 1) {
            System.out.println("invalid entriesPerBatch");
            return;
        }

        // non batch insert
        if (entriesPerBatch == 1) {
            for (long i = 0; i < numEntries; i++) {
                long k = (order == Order.SEQUENTIAL) ? i : random.nextInt(numEntries);
                byte[] key = formatNumber(k);
                db.put(key, generator.generate(valueSize));
                byteCount += valueSize + key.length;
                finishedSingleOp();
            }
        } else // batch insert
        {
            for (int i = 0; i < numEntries; i += entriesPerBatch) {
                Map<byte[], byte[]> batch = new HashMap<>();
                for (int j = 0; j < entriesPerBatch; j++) {
                    int k = (order == Order.SEQUENTIAL) ? i + j : random.nextInt(numEntries);
                    byte[] key = formatNumber(k);
                    batch.put(key, generator.generate(valueSize));
                    byteCount += valueSize + key.length;
                    finishedSingleOp();
                }
                db.putBatch(batch);
            }
        }
    }

    public static byte[] formatNumber(long k) {
        Preconditions.checkArgument(k >= 0, "number must be positive");

        byte[] slice = new byte[16];

        int i = 15;
        while (k > 0) {
            slice[i--] = (byte) ((long) '0' + (k % 10));
            k /= 10;
        }
        while (i >= 0) {
            slice[i--] = '0';
        }
        return slice;
    }

    // ---------------------------------------------------------------
    // ====================== Timer Utilities ========================
    // ---------------------------------------------------------------

    private void finishedSingleOp() {
        opCount++;
        if (opCount >= nextReport) {
            if (nextReport < 1000) {
                nextReport += 100;
            } else if (nextReport < 5000) {
                nextReport += 500;
            } else if (nextReport < 10000) {
                nextReport += 1000;
            } else if (nextReport < 50000) {
                nextReport += 5000;
            } else if (nextReport < 100000) {
                nextReport += 10000;
            } else if (nextReport < 500000) {
                nextReport += 50000;
            } else {
                nextReport += 100000;
            }
            // System.out.printf("... finished %d ops%30s\r", opCount, "");
        }
    }

    private long startTime;
    private long byteCount;
    private int opCount;
    private int nextReport;

    private void start() {
        startTime = System.nanoTime();
        byteCount = 0;
        opCount = 0;
        nextReport = 100;
    }

    private void stop(String benchmark, int keyCount, long valueSizeBytes, int batchSizeBytes) {
        long endTime = System.nanoTime();
        double elapsedSeconds = 1.0d * (endTime - startTime) / TimeUnit.SECONDS.toNanos(1);

        if (opCount < 1) {
            opCount = 1;
        }

        /*-
        System.out.printf("%-40s : %8.5f s; %8.5f us/op; filesize: %8.5f MB, %8.1f MB/s, \n", benchmark, elapsedSeconds,
                elapsedSeconds * 1.0e6 / opCount, db.approximateSize() / (1024F * 1024F),
                (byteCount / 1048576F) / elapsedSeconds);
        */

        // close the DB to get the right file size
        db.close();
        assertTrue(db.isClosed());
        long fileSize = FileUtils.getDirectorySizeBytes(db.getPath().get());
        assertTrue(db.open());

        System.out.printf("%s, %s, %d, %d, %d, %.5f, %d, %.5f, %.5f \n", testName, benchmark, keyCount, valueSizeBytes,
                batchSizeBytes, elapsedSeconds, opCount, fileSize / (1024F * 1024F), byteCount / (1024F * 1024F));
    }

    private static void printHeader() {
        printEnvironment();

        System.out.printf("Keys:       %d bytes each\n", keySizeBytes);
        System.out.printf("Values:     %d bytes each (%d bytes after compression)\n", valueSizeBytes,
                (int) (valueSizeBytes * compressionRatio + 0.5));
        System.out.printf("Entries:    %d\n", keyCount);
        System.out.printf("Compression Ration:    %.1f\n", compressionRatio);
        System.out.printf("RawSize:    %.1f MB (estimated)\n",
                ((float) (keySizeBytes + valueSizeBytes) * (float) keyCount) / (1024F * 1024F));
        System.out.printf("CompressedSize:   %.1f MB (estimated)\n",
                (((keySizeBytes + valueSizeBytes * (float) compressionRatio) * (float) keyCount) / (1024F * 1024F)));

        System.out.printf("------------------------------------------------\n\n");

        System.out
                .printf("db, benchmark, keyCount, valueSizeBytes, batchSizeBytes, elapsed_s, opCount, disk_mb, raw_mb\n");
    }

    private static void printEnvironment() {
        System.out.printf("Date:       %tc\n", new Date());

        File cpuInfo = new File("/proc/cpuinfo");
        if (cpuInfo.canRead()) {
            int numberOfCpus = 0;
            String cpuType = null;
            String cacheSize = null;
            try {
                for (String line : CharStreams.readLines(Files.newReader(cpuInfo, UTF_8))) {
                    ImmutableList<String> parts = ImmutableList
                            .copyOf(Splitter.on(':').omitEmptyStrings().trimResults().limit(2).split(line));
                    if (parts.size() != 2) {
                        continue;
                    }
                    String key = parts.get(0);
                    String value = parts.get(1);

                    if (key.equals("model name")) {
                        numberOfCpus++;
                        cpuType = value;
                    } else if (key.equals("cache size")) {
                        cacheSize = value;
                    }
                }
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            System.out.printf("CPU:        %d * %s\n", numberOfCpus, cpuType);
            System.out.printf("CPUCache:   %s\n", cacheSize);
        }
    }

    // ---------------------------------------------------------------
    // =================== Byte Array Generation =====================
    // ---------------------------------------------------------------

    private static class RandomGenerator {
        private final Slice data;
        private int position;

        private RandomGenerator(double compressionRatio) {
            // We use a limited amount of data over and over again and ensure
            // that it is larger than the compression window (32KB), and also
            // large enough to serve all typical value sizes we want to write.
            Random rnd = new Random(301);
            data = Slices.allocate(1048576 + 100);
            SliceOutput sliceOutput = data.output();
            while (sliceOutput.size() < 1048576) {
                // Add a short fragment that is as compressible as specified
                // by FLAGS_compression_ratio.
                sliceOutput.writeBytes(compressibleString(rnd, compressionRatio, 100));
            }
        }

        private byte[] generate(int length) {
            if (position + length > data.length()) {
                position = 0;
                assert (length < data.length());
            }
            Slice slice = data.slice(position, length);
            position += length;
            return slice.getBytes();
        }

        // Utility methods

        private static Slice compressibleString(Random rnd, double compressionRatio, int len) {
            int raw = (int) (len * compressionRatio);
            if (raw < 1) {
                raw = 1;
            }
            Slice rawData = generateRandomSlice(rnd, raw);

            // Duplicate the random data until we have filled "len" bytes
            Slice dst = Slices.allocate(len);
            SliceOutput sliceOutput = dst.output();
            while (sliceOutput.size() < len) {
                sliceOutput.writeBytes(rawData, 0, Math.min(rawData.length(), sliceOutput.writableBytes()));
            }
            return dst;
        }

        private static Slice generateRandomSlice(Random random, int length) {
            Slice rawData = Slices.allocate(length);
            SliceOutput sliceOutput = rawData.output();
            while (sliceOutput.isWritable()) {
                sliceOutput.writeByte((byte) ((int) ' ' + random.nextInt(95)));
            }
            return rawData;
        }
    }
}
