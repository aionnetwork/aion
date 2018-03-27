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
 ******************************************************************************/
package org.aion.db;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.db.impl.leveldb.LevelDBConstants;
import org.aion.log.AionLoggerFactory;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.types.AionBlock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigInteger;
import java.util.*;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.db.impl.DatabaseTestUtils.assertConcurrent;

/**
 * @author Alexandra Roatis
 */
public class BlockStoreConcurrencyTest {

    private static final int CONCURRENT_THREADS = 200;
    private static final int TIME_OUT = 100; // in seconds
    private static final boolean DISPLAY_MESSAGES = false;

    private static final IByteArrayKeyValueDatabase indexDatabase = DatabaseFactory.connect("index");
    private static final IByteArrayKeyValueDatabase blockDatabase = DatabaseFactory.connect("blocks");
    private static final AionBlockStore blockStore = new AionBlockStore(indexDatabase, blockDatabase);

    private static AionBlockStore sourceBlockstore;

    @BeforeClass
    public static void setup() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("DB", "DEBUG");

        AionLoggerFactory.init(cfg);

        Properties props = new Properties();
        props.setProperty(DatabaseFactory.PROP_DB_TYPE, DBVendor.LEVELDB.toValue());
        props.setProperty(DatabaseFactory.PROP_DB_PATH, "resources/test_blockstore");
        props.setProperty(DatabaseFactory.PROP_ENABLE_LOCKING, "false");
        props.setProperty(DatabaseFactory.PROP_ENABLE_AUTO_COMMIT, "true");
        props.setProperty(DatabaseFactory.PROP_ENABLE_DB_CACHE, "true");
        props.setProperty(DatabaseFactory.PROP_ENABLE_DB_COMPRESSION, "true");
        props.setProperty(DatabaseFactory.PROP_ENABLE_HEAP_CACHE, "false");
        props.setProperty(DatabaseFactory.PROP_MAX_HEAP_CACHE_SIZE, "0");
        props.setProperty(DatabaseFactory.PROP_ENABLE_HEAP_CACHE_STATS, "false");
        props.setProperty(DatabaseFactory.PROP_MAX_FD_ALLOC, String.valueOf(LevelDBConstants.MAX_OPEN_FILES));
        props.setProperty(DatabaseFactory.PROP_BLOCK_SIZE, String.valueOf(LevelDBConstants.BLOCK_SIZE));
        props.setProperty(DatabaseFactory.PROP_WRITE_BUFFER_SIZE, String.valueOf(LevelDBConstants.WRITE_BUFFER_SIZE));
        props.setProperty(DatabaseFactory.PROP_CACHE_SIZE, String.valueOf(LevelDBConstants.CACHE_SIZE));


        // opening index db
        props.setProperty(DatabaseFactory.PROP_DB_NAME, "index");
        IByteArrayKeyValueDatabase index = DatabaseFactory.connect(props);
        index.open();

        // opening blocks db
        props.setProperty(DatabaseFactory.PROP_DB_NAME, "block");
        IByteArrayKeyValueDatabase block = DatabaseFactory.connect(props);
        block.open();

        sourceBlockstore = new AionBlockStore(index, block);
    }

    @AfterClass
    public static void teardown() {
        sourceBlockstore.close();
        blockStore.close();
    }

    private void addThread_saveBlock(List<Runnable> _threads, AionBlock _block, BigInteger _totalDiff) {
        _threads.add(() -> {
            blockStore.saveBlock(_block, _totalDiff, true);
            if (DISPLAY_MESSAGES) {
                System.out.println(
                        Thread.currentThread().getName() + ": saveBlock(" + _block + ", " + _totalDiff + ", true)");
            }
        });
    }

    @Test
    public void testConcurrent_saveBlock() throws InterruptedException {
        indexDatabase.open();
        blockDatabase.open();

        // create distinct threads with
        List<Runnable> threads = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            AionBlock blk = sourceBlockstore.getChainBlockByNumber(i + 1L);
            addThread_saveBlock(threads, blk, sourceBlockstore.getTotalDifficultyForHash(blk.getHash()));
        }

        // run threads
        assertConcurrent("Testing saveBlock(...) ", threads, TIME_OUT);
        blockStore.flush();

        // check that all values were added
        assertThat(blockDatabase.keys().size()).isEqualTo(CONCURRENT_THREADS);
        assertThat(indexDatabase.keys().size()).isEqualTo(CONCURRENT_THREADS + 1);

        indexDatabase.close();
        blockDatabase.close();

    }

    @Test
    public void testConcurrentUpdate() throws InterruptedException {
        //        dbDef.setProperty(DatabaseFactory.PROP_DB_NAME, DatabaseTestUtils.dbName + getNext());
        //        dbDef.setProperty(DatabaseFactory.PROP_ENABLE_LOCKING, "true");
        //        // open database
        //        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        //        assertThat(db.open()).isTrue();
        //
        //        // create distinct threads with
        //        List<Runnable> threads = new ArrayList<>();
        //
        //        int threadSetCount = CONCURRENT_THREADS / 4;
        //        if (threadSetCount < 3) { threadSetCount = 3; }
        //
        //        for (int i = 0; i < threadSetCount; i++) {
        //            String keyStr = "key-" + i + ".";
        //
        //            // 1. thread that puts entry
        //            addThread4Put(threads, db, keyStr);
        //
        //            // 2. thread that deletes entry
        //            addThread4Delete(threads, db, keyStr);
        //
        //            // 3. thread that puts entries
        //            addThread4PutBatch(threads, db, keyStr);
        //
        //            // 4. thread that deletes entry
        //            addThread4DeleteBatch(threads, db, keyStr);
        //        }
        //
        //        // run threads and check for exceptions
        //        assertConcurrent("Testing concurrent updates. ", threads, TIME_OUT);
        //
        //        // check that db is unlocked after updates
        //        assertThat(db.isLocked()).isFalse();
        //
        //        // ensuring close
        //        db.close();
        //        assertThat(db.isClosed()).isTrue();
    }

}

