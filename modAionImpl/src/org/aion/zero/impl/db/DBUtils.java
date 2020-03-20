package org.aion.zero.impl.db;

import java.util.List;
import java.util.Map;
import org.aion.base.AionTransaction;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.mcf.blockchain.Block;
import org.aion.base.AccountState;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.core.ImportResult;
import org.aion.mcf.db.Repository;
import org.aion.mcf.db.RepositoryCache;
import org.aion.types.AionAddress;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.types.AionTxInfo;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

/**
 * Methods used by CLI calls for debugging the local blockchain data.
 *
 * @author Alexandra Roatis
 * @implNote This class started off with helper functions for data recovery at runtime. It evolved
 *     into a diverse set of CLI calls for displaying or manipulating the data. It would benefit
 *     from refactoring to separate the different use cases.
 */
public class DBUtils {

    public enum Status {
        SUCCESS,
        FAILURE,
        ILLEGAL_ARGUMENT
    }

    /** @implNote Used by the CLI call. */
    public static Status queryTransaction(byte[] txHash) {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        AionLoggerFactory.initAll(Map.of(LogEnum.GEN, LogLevel.INFO));
        final Logger log = AionLoggerFactory.getLogger(LogEnum.GEN.name());

        // get the current blockchain
        AionBlockchainImpl blockchain = new AionBlockchainImpl(cfg, null, false);

        try {
            Map<ByteArrayWrapper, AionTxInfo> txInfoList = blockchain.getTransactionStore().getTxInfo(txHash);

            if (txInfoList == null || txInfoList.isEmpty()) {
                log.error("Can not find the transaction with given hash.");
                return Status.FAILURE;
            }

            for (Map.Entry<ByteArrayWrapper, AionTxInfo> entry : txInfoList.entrySet()) {

                Block block = blockchain.getBlockByHash(entry.getKey().toBytes());
                if (block == null) {
                    log.error("Cannot find the block data for the block hash from the transaction info. The database might be corrupted. Please consider reimporting the database by running ./aion.sh -n <network> --redo-import");
                    return Status.FAILURE;
                }

                AionTransaction tx = block.getTransactionsList().get(entry.getValue().getIndex());

                if (tx == null) {
                    log.error("Cannot find the transaction data for the given hash. The database might be corrupted. Please consider reimporting the database by running ./aion.sh -n <network> --redo-import");
                    return Status.FAILURE;
                }

                log.info(tx.toString());
                log.info(entry.getValue().toString());
            }

            return Status.SUCCESS;
        } catch (Exception e) {
            log.error("Error encountered while attempting to retrieve the transaction data.", e);
            return Status.FAILURE;
        }
    }

    /** @implNote Used by the CLI call. */
    public static Status queryBlock(long nbBlock) {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        // TODO: add this log inside methods of interest
        // AionLoggerFactory.initAll(Map.of(LogEnum.QBCLI, LogLevel.DEBUG));
        AionLoggerFactory.initAll(Map.of(LogEnum.GEN, LogLevel.INFO));
        final Logger log = AionLoggerFactory.getLogger(LogEnum.GEN.name());

        // get the current blockchain
        AionBlockchainImpl blockchain = new AionBlockchainImpl(cfg, null, false);

        try {
            List<Block> blocks = blockchain.getRepository().getBlockStore().getAllChainBlockByNumber(nbBlock);

            if (blocks == null || blocks.isEmpty()) {
                log.error("Cannot find the block with given block height.");
                return Status.FAILURE;
            }

            for (Block b : blocks) {
                log.info(b.toString());
            }

            // Now print the transaction state. Only for the mainchain.

            // TODO: the worldstate can not read the data after the stateroot has been setup, need
            // to fix the issue first then the tooling can print the states between the block.

            Block mainChainBlock = blockchain.getBlockByNumber(nbBlock);
            if (mainChainBlock == null) {
                log.error("Cannot find the main chain block with given block height.");
                return Status.FAILURE;
            }

            Block parentBlock = blockchain.getBlockByHash(mainChainBlock.getParentHash());
            if (parentBlock == null) {
                log.error("Cannot find the parent block with given block height.");
                return Status.FAILURE;
            }

            blockchain.setBestBlock(parentBlock);
            // TODO: log to QBCLI info that we want printed out
            Pair<AionBlockSummary, RepositoryCache> result =
                    blockchain.tryImportWithoutFlush(mainChainBlock);
            log.info(
                    "Import result: "
                            + (result == null
                                    ? ImportResult.INVALID_BLOCK
                                    : ImportResult.IMPORTED_BEST));
            if (result != null) {
                log.info("Block summary:\n" + result.getLeft() + "\n");
                log.info("RepoCacheDetails:\n" + result.getRight());
            }
            // TODO: alternative to logging is to use the TrieImpl.scanTreeDiffLoop

            return Status.SUCCESS;
        } catch (Exception e) {
            log.error("Error encountered while attempting to retrieve the block data.", e);
            return Status.FAILURE;
        }
    }

    /** @implNote Used by the CLI call. */
    // TODO: more parameters would be useful, e.g. get account X at block Y
    public static Status queryAccount(AionAddress address) {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        AionLoggerFactory.initAll(Map.of(LogEnum.GEN, LogLevel.INFO));
        final Logger log = AionLoggerFactory.getLogger(LogEnum.GEN.name());

        // get the current blockchain
        AionBlockchainImpl blockchain = new AionBlockchainImpl(cfg, null, false);

        try {
            Block bestBlock = blockchain.getRepository().getBestBlock();

            Repository<AccountState> repository =
                    blockchain
                            .getRepository()
                            .getSnapshotTo((bestBlock).getStateRoot())
                            .startTracking();

            AccountState account = repository.getAccountState(address);
            log.info(account.toString());

            log.info(repository.getContractDetails(address).toString());

            return Status.SUCCESS;
        } catch (Exception e) {
            log.error("Error encountered while attempting to retrieve the account data.", e);
            return Status.FAILURE;
        }
    }
}
