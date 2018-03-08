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
 * Contributors:
 *     Aion foundation.
 *
 ******************************************************************************/

package org.aion.zero.impl.db;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.type.IBlock;
import org.aion.base.util.Hex;
import org.aion.db.impl.DatabaseFactory;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.ds.DataSourceArray;
import org.aion.mcf.ds.DataSourceLongArray;
import org.aion.mcf.ds.ObjectDataSource;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.config.CfgAion;
import org.slf4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.aion.zero.impl.db.AionBlockStore.BLOCK_INFO_SERIALIZER;

public class RecoveryUtils {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.name());

    public enum Status {
        SUCCESS, FAILURE, ILLEGAL_ARGUMENT
    }

    /**
     * Used by the CLI call.
     */
    public static Status indexToLong() {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("DB", "ERROR");

        AionLoggerFactory.init(cfgLog);

        Properties info = new Properties();
        info.setProperty("db_name", "index");
        info.setProperty("db_type", cfg.getDb().getVendor());
        info.setProperty("db_path", new File(cfg.getBasePath(), cfg.getDb().getPath()).getAbsolutePath());
        info.setProperty("enable_auto_commit", String.valueOf(cfg.getDb().isAutoCommitEnabled()));
        info.setProperty("enable_db_cache", String.valueOf(cfg.getDb().isDbCacheEnabled()));
        info.setProperty("enable_db_compression", String.valueOf(cfg.getDb().isDbCompressionEnabled()));
        info.setProperty("enable_heap_cache", String.valueOf(cfg.getDb().isHeapCacheEnabled()));
        info.setProperty("max_heap_cache_size", cfg.getDb().getMaxHeapCacheSize());
        info.setProperty("enable_heap_cache_stats", String.valueOf(cfg.getDb().isHeapCacheStatsEnabled()));

        IByteArrayKeyValueDatabase indexDatabase = DatabaseFactory.connect(info);

        // open the database connection
        indexDatabase.open();

        // check object status
        if (indexDatabase == null) {
            System.out.println(
                    "Database <" + info.getProperty("db_type") + "> connection could not be established for <" + info
                            .getProperty("db_name") + ">.");
            return Status.FAILURE;
        }

        // check persistence status
        if (!indexDatabase.isCreatedOnDisk()) {
            System.out.println("Database <" + info.getProperty("db_type") + "> cannot be saved to disk for <" + info
                    .getProperty("db_name") + ">.");
            return Status.FAILURE;
        }

        indexToLong(indexDatabase);

        // ok if we managed to get down to the expected block
        return Status.SUCCESS;
    }

    /**
     * Used by internal recovery method.
     */
    public static void indexToLong(IByteArrayKeyValueDatabase indexDatabase) {
        byte[] oldSizeKey = Hex.decode("FFFFFFFFFFFFFFFF");

        if (indexDatabase.get(oldSizeKey).isPresent()) {
            LOG.info("Database using old version of block indexing. Updating ...");

            DataSourceArray<List<AionBlockStore.BlockInfo>> oldIndex = new DataSourceArray<>(
                    new ObjectDataSource<>(indexDatabase, BLOCK_INFO_SERIALIZER));
            DataSourceLongArray<List<AionBlockStore.BlockInfo>> newIndex = new DataSourceLongArray<>(
                    new ObjectDataSource<>(indexDatabase, BLOCK_INFO_SERIALIZER));

            int size = oldIndex.size();

            // converting data storage to use long
            for (int i = 0; i < size; i++) {
                newIndex.set((long) i, oldIndex.get(i));
            }
            newIndex.flush();

            for (int i = size - 1; i >= 0; i--) {
                oldIndex.remove(i);
            }
            oldIndex.flush();

            // deleting old size key
            indexDatabase.delete(oldSizeKey);

            if (!indexDatabase.isAutoCommitEnabled()) {
                indexDatabase.commit();
            }
            LOG.info("Update complete.");
        }
    }

    /**
     * Used by the CLI call.
     */
    public static Status revertTo(long nbBlock) {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("DB", "INFO");
        cfgLog.put("GEN", "INFO");

        AionLoggerFactory.init(cfgLog);

        // get the current blockchain
        AionBlockchainImpl blockchain = AionBlockchainImpl.inst();

        Status status = revertTo(blockchain, nbBlock);

        blockchain.getRepository().close();

        // ok if we managed to get down to the expected block
        return status;
    }

    /**
     * Used by the CLI call.
     */
    public static void pruneAndCorrect() {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("DB", "INFO");
        cfgLog.put("GEN", "INFO");

        AionLoggerFactory.init(cfgLog);

        // get the current blockchain
        AionBlockchainImpl blockchain = AionBlockchainImpl.inst();

        IBlockStoreBase store = blockchain.getBlockStore();

        IBlock bestBlock = store.getBestBlock();
        if (bestBlock == null) {
            System.out.println("Empty database. Nothing to do.");
            return;
        }

        // revert to block number and flush changes
        store.pruneAndCorrect();
        store.flush();

        blockchain.getRepository().close();
    }

    /**
     * Used by internal world state recovery method.
     */
    public static Status revertTo(AionBlockchainImpl blockchain, long nbBlock) {
        IBlockStoreBase store = blockchain.getBlockStore();

        IBlock bestBlock = store.getBestBlock();
        if (bestBlock == null) {
            System.out.println("Empty database. Nothing to do.");
            return Status.ILLEGAL_ARGUMENT;
        }

        long nbBestBlock = bestBlock.getNumber();

        System.out.println("Attempting to revert best block from " + nbBestBlock + " to " + nbBlock + " ...");

        // exit with warning if the given block is larger or negative
        if (nbBlock < 0) {
            System.out.println(
                    "Negative values <" + nbBlock + "> cannot be interpreted as block numbers. Nothing to do.");
            return Status.ILLEGAL_ARGUMENT;
        }
        if (nbBestBlock == 0) {
            System.out.println("Only genesis block in database. Nothing to do.");
            return Status.ILLEGAL_ARGUMENT;
        }
        if (nbBlock == nbBestBlock) {
            System.out.println(
                    "The block " + nbBlock + " is the current best block stored in the database. Nothing to do.");
            return Status.ILLEGAL_ARGUMENT;
        }
        if (nbBlock > nbBestBlock) {
            System.out.println("The block #" + nbBlock + " is greater than the current best block #" + nbBestBlock
                    + " stored in the database. "
                    + "Cannot move to that block without synchronizing with peers. Start Aion instance to sync.");
            return Status.ILLEGAL_ARGUMENT;
        }

        // revert to block number and flush changes
        store.revert(nbBlock);
        store.flush();

        nbBestBlock = store.getBestBlock().getNumber();

        // ok if we managed to get down to the expected block
        return (nbBestBlock == nbBlock) ? Status.SUCCESS : Status.FAILURE;
    }
}
