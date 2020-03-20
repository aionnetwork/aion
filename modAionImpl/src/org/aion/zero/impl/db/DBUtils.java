package org.aion.zero.impl.db;

import java.util.Map;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.mcf.blockchain.Block;
import org.aion.base.AccountState;
import org.aion.mcf.db.Repository;
import org.aion.types.AionAddress;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.config.CfgAion;
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
