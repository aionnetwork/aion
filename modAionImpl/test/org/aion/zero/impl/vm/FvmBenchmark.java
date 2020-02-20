package org.aion.zero.impl.vm;

import org.aion.fastvm.FvmDataWord;
import org.aion.base.AccountState;
import org.aion.mcf.db.RepositoryCache;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Ignore;
import org.junit.Test;

public class FvmBenchmark {

    /**
     * The following test case benchmarks database performance. It maximizes database read/write and
     * minimizes cache usage.
     *
     * <p>It simulate the situation where there are <code>X</code> blocks, each of which contains
     * <code>Y</code> transactions. Each transaction reads/writes one storage entry of an unique
     * account. This whole process is repeated <code>Z</code> time.
     *
     * <p>There will be <code>X * Y</code> accounts created. Trie serialization/deserialization is
     * expected to happen during the test.
     *
     * <p>NOTE: Before you run this test, make sure the database is empty, to get consistent
     * results.
     */
    @Test
    @Ignore
    public void testDB() {
        AionRepositoryImpl db = AionRepositoryImpl.inst();
        byte[] zeros28 = new byte[28];

        int repeat = 1000;
        int blocks = 32;
        int transactions = 1024;

        long totalWrite = 0;
        long totalRead = 0;
        for (int r = 1; r <= repeat; r++) {

            long t1 = System.nanoTime();
            for (int i = 0; i < blocks; i++) {
                RepositoryCache<AccountState> repo = db.startTracking();
                for (int j = 0; j < transactions; j++) {
                    AionAddress address =
                        new AionAddress(
                            ByteUtil.merge(zeros28, ByteUtil.intToBytes(i * 1024 + j)));
                    repo.addStorageRow(
                        address,
                        ByteArrayWrapper.wrap(FvmDataWord.fromBytes(RandomUtils.nextBytes(16)).copyOfData()),
                        ByteArrayWrapper.wrap(
                            ByteUtil.stripLeadingZeroes(FvmDataWord.fromBytes(RandomUtils.nextBytes(16)).copyOfData())));
                }
                repo.flushTo(db, true);
                db.flush();
            }
            long t2 = System.nanoTime();

            long t3 = System.nanoTime();
            for (int i = 0; i < blocks; i++) {
                RepositoryCache<AccountState> repo = db.startTracking();
                for (int j = 0; j < transactions; j++) {
                    AionAddress address =
                        new AionAddress(
                            ByteUtil.merge(zeros28, ByteUtil.intToBytes(i * 1024 + j)));
                    FvmDataWord.fromBytes(
                        repo.getStorageValue(
                            address,
                            ByteArrayWrapper.wrap(FvmDataWord.fromBytes(RandomUtils.nextBytes(16)).copyOfData()))
                            .toBytes());
                }
                repo.flushTo(db, true);
                db.flush();
            }
            long t4 = System.nanoTime();

            totalWrite += (t2 - t1);
            totalRead += (t4 - t3);
            System.out.printf(
                "write = %7d,  read = %7d,  avg. write = %7d,  avg. read = %7d\n",
                (t2 - t1) / (blocks * transactions),
                (t4 - t3) / (blocks * transactions),
                totalWrite / (r * blocks * transactions),
                totalRead / (r * blocks * transactions));
        }
    }
}
