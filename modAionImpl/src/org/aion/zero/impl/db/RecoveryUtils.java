/* ******************************************************************************
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
 ******************************************************************************/
package org.aion.zero.impl.db;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.aion.base.type.IBlock;
import org.aion.log.AionLoggerFactory;
import org.aion.mcf.config.CfgDb;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.AionGenesis;
import org.aion.zero.impl.AionHubUtils;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.types.AionBlock;

public class RecoveryUtils {

    public enum Status {
        SUCCESS,
        FAILURE,
        ILLEGAL_ARGUMENT
    }

    /** Used by the CLI call. */
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

    /** Used by the CLI call. */
    public static void pruneAndCorrect() {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        cfg.getDb().setHeapCacheEnabled(false);

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

    /** Used by the CLI call. */
    public static void dbCompact() {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        cfg.getDb().setHeapCacheEnabled(false);

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("DB", "INFO");
        cfgLog.put("GEN", "INFO");

        AionLoggerFactory.init(cfgLog);

        // get the current blockchain
        AionRepositoryImpl repository = AionRepositoryImpl.inst();

        // compact database after the changes were applied
        repository.compact();
        repository.close();
    }

    /** Used by the CLI call. */
    public static void dumpBlocks(long count) {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        cfg.getDb().setHeapCacheEnabled(false);

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("DB", "ERROR");
        cfgLog.put("GEN", "ERROR");

        AionLoggerFactory.init(cfgLog);

        // get the current blockchain
        AionRepositoryImpl repository = AionRepositoryImpl.inst();

        AionBlockStore store = repository.getBlockStore();
        try {
            String file = store.dumpPastBlocks(count, cfg.getBasePath());
            if (file == null) {
                System.out.println("The database is empty. Cannot print block information.");
            } else {
                System.out.println("Block information stored in " + file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        repository.close();
    }

    /** Used by internal world state recovery method. */
    public static Status revertTo(IAionBlockchain blockchain, long nbBlock) {
        IBlockStoreBase store = blockchain.getBlockStore();

        IBlock bestBlock = store.getBestBlock();
        if (bestBlock == null) {
            System.out.println("Empty database. Nothing to do.");
            return Status.ILLEGAL_ARGUMENT;
        }

        long nbBestBlock = bestBlock.getNumber();

        System.out.println(
                "Attempting to revert best block from " + nbBestBlock + " to " + nbBlock + " ...");

        // exit with warning if the given block is larger or negative
        if (nbBlock < 0) {
            System.out.println(
                    "Negative values <"
                            + nbBlock
                            + "> cannot be interpreted as block numbers. Nothing to do.");
            return Status.ILLEGAL_ARGUMENT;
        }
        if (nbBestBlock == 0) {
            System.out.println("Only genesis block in database. Nothing to do.");
            return Status.ILLEGAL_ARGUMENT;
        }
        if (nbBlock == nbBestBlock) {
            System.out.println(
                    "The block "
                            + nbBlock
                            + " is the current best block stored in the database. Nothing to do.");
            return Status.ILLEGAL_ARGUMENT;
        }
        if (nbBlock > nbBestBlock) {
            System.out.println(
                    "The block #"
                            + nbBlock
                            + " is greater than the current best block #"
                            + nbBestBlock
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

    public static void printStateTrieSize(long blockNumber) {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("DB", "ERROR");

        AionLoggerFactory.init(cfgLog);

        // get the current blockchain
        AionRepositoryImpl repository = AionRepositoryImpl.inst();
        AionBlockStore store = repository.getBlockStore();

        long topBlock = store.getBestBlock().getNumber();
        if (topBlock < 0) {
            System.out.println("The database is empty. Cannot print block information.");
            return;
        }

        long targetBlock = topBlock - blockNumber + 1;
        if (targetBlock < 0) {
            targetBlock = 0;
        }

        AionBlock block;
        byte[] stateRoot;

        while (targetBlock <= topBlock) {
            block = store.getChainBlockByNumber(targetBlock);
            if (block != null) {
                stateRoot = block.getStateRoot();
                try {
                    System.out.println(
                            "Block hash: "
                                    + block.getShortHash()
                                    + ", number: "
                                    + block.getNumber()
                                    + ", tx count: "
                                    + block.getTransactionsList().size()
                                    + ", state trie kv count = "
                                    + repository.getWorldState().getTrieSize(stateRoot));
                } catch (RuntimeException e) {
                    System.out.println(
                            "Block hash: "
                                    + block.getShortHash()
                                    + ", number: "
                                    + block.getNumber()
                                    + ", tx count: "
                                    + block.getTransactionsList().size()
                                    + ", state trie kv count threw exception: "
                                    + e.getMessage());
                }
            } else {
                long count = store.getBlocksByNumber(targetBlock).size();
                System.out.println(
                        "Null block found at level "
                                + targetBlock
                                + ". There "
                                + (count == 1 ? "is 1 block" : "are " + count + " blocks")
                                + " at this level. No main chain block found.");
            }
            targetBlock++;
        }

        repository.close();
    }

    public static void printStateTrieDump(long blockNumber) {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("DB", "ERROR");

        AionLoggerFactory.init(cfgLog);

        // get the current blockchain
        AionRepositoryImpl repository = AionRepositoryImpl.inst();

        AionBlockStore store = repository.getBlockStore();

        AionBlock block;

        if (blockNumber == -1L) {
            block = store.getBestBlock();
            if (block == null) {
                System.out.println("The requested block does not exist in the database.");
                return;
            }
            blockNumber = block.getNumber();
        } else {
            block = store.getChainBlockByNumber(blockNumber);
            if (block == null) {
                System.out.println("The requested block does not exist in the database.");
                return;
            }
        }

        byte[] stateRoot = block.getStateRoot();
        System.out.println(
                "\nBlock hash: "
                        + block.getShortHash()
                        + ", number: "
                        + blockNumber
                        + ", tx count: "
                        + block.getTransactionsList().size()
                        + "\n\n"
                        + repository.getWorldState().getTrieDump(stateRoot));

        repository.close();
    }

    public static void pruneOrRecoverState(String pruning_type) {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.fromXML();
        cfg.getConsensus().setMining(false);

        // setting pruning to the version requested
        CfgDb.PruneOption option = CfgDb.PruneOption.fromValue(pruning_type);
        cfg.getDb().setPrune(option.toString());

        System.out.println("Reorganizing the state storage to " + option + " mode ...");

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("DB", "ERROR");
        cfgLog.put("CONS", "ERROR");

        AionLoggerFactory.init(cfgLog);

        AionBlockchainImpl chain = AionBlockchainImpl.inst();
        AionRepositoryImpl repo = (AionRepositoryImpl) chain.getRepository();
        AionBlockStore store = repo.getBlockStore();

        // dropping old state database
        System.out.println("Deleting old data ...");
        repo.getStateDatabase().drop();
        if (pruning_type.equals("spread")) {
            repo.getStateArchiveDatabase().drop();
        }

        // recover genesis
        System.out.println("Rebuilding genesis block ...");
        AionGenesis genesis = cfg.getGenesis();
        AionHubUtils.buildGenesis(genesis, repo);

        // recover all blocks
        AionBlock block = store.getBestBlock();
        System.out.println(
                "Rebuilding the main chain "
                        + block.getNumber()
                        + " blocks (may take a while) ...");

        long topBlockNumber = block.getNumber();
        long blockNumber = 1000;

        // recover in increments of 1k blocks
        while (blockNumber < topBlockNumber) {
            block = store.getChainBlockByNumber(blockNumber);
            chain.recoverWorldState(repo, block);
            System.out.println("Finished with blocks up to " + blockNumber + ".");
            blockNumber += 1000;
        }

        block = store.getBestBlock();
        chain.recoverWorldState(repo, block);

        repo.close();
        System.out.println("Reorganizing the state storage COMPLETE.");
    }
}
