package org.aion.zero.impl.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.aion.base.AccountState;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.mcf.blockchain.Block;
import org.aion.base.db.Repository;
import org.aion.base.db.RepositoryCache;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlockSummary;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "dev",
        aliases = {"d"},
        description =
                "This command is for testing and development use only. Note only one option can be specified"
                        + " and this command must be added after all other options or subcommands."
                        + " Use -h to view available options.",
        abbreviateSynopsis = true)
public class DevCLI {

    @CommandLine.ArgGroup(multiplicity = "1") private Composite args;

    /*
     * Creates an instance of the DevCli class and prints the usage information to the specified stream
     */
    public static void printUsage(PrintStream stream, DevCLI instance) {
        CommandLine.usage(instance, stream);
    }

    public static Cli.ReturnType stopAt(long blockNumber) {
        if (blockNumber <= 0) {
            System.out.println("Invalid parameter value for --xx-stop-at.");
            return Cli.ReturnType.ERROR;
        }
        System.out.println("Shutdown hook set at block " + blockNumber);
        CfgAion.inst().getConsensus().setMining(false);
        AionBlockchainImpl.shutdownHook = blockNumber;
        return Cli.ReturnType.RUN;
    }

    public static Cli.ReturnType fullSync() {
        CfgAion.inst().getConsensus().setMining(false);
        AionBlockchainImpl.enableFullSyncCheck = true;
        System.out.println("Shutdown hook set to fully sync.");
        return Cli.ReturnType.RUN;
    }

    public static Cli.ReturnType printAccountDetails(String strAddress) {
        // TODO AKI-681: more parameters would be useful, e.g. get account X at block Y

        // read database configuration
        CfgAion.inst().dbFromXML();

        AionLoggerFactory.initAll(Map.of(LogEnum.GEN, LogLevel.INFO));
        final Logger log = AionLoggerFactory.getLogger(LogEnum.GEN.name());

        AionAddress address;

        try {
            address = new AionAddress(ByteUtil.hexStringToBytes(strAddress));
        } catch (NumberFormatException e) {
            log.error("The given argument «" + strAddress + "» cannot be converted to a valid account address.");
            return Cli.ReturnType.ERROR;
        }

        // get the current repository
        AionRepositoryImpl repository = AionRepositoryImpl.inst();
        try {
            Block bestBlock = repository.getBestBlock();
            Repository snapshot = repository.getSnapshotTo(bestBlock.getStateRoot()).startTracking();

            AccountState account = snapshot.getAccountState(address);
            log.info(account.toString());
            log.info(snapshot.getContractDetails(address).toString());

            return Cli.ReturnType.EXIT;
        } catch (Exception e) {
            log.error("Error encountered while attempting to retrieve the account data.", e);
            return Cli.ReturnType.ERROR;
        } finally {
            repository.close();
        }
    }

    public static Cli.ReturnType printTxDetails(String txHash) {
        // read database configuration
        CfgAion.inst().dbFromXML();

        AionLoggerFactory.initAll(Map.of(LogEnum.GEN, LogLevel.INFO));
        final Logger log = AionLoggerFactory.getLogger(LogEnum.GEN.name());

        byte[] hash;
        boolean isSuccessful;
        try {
            hash = ByteUtil.hexStringToBytes(txHash);
        } catch (NumberFormatException e) {
            log.error("The given argument «" + txHash + "» cannot be converted to a valid transaction hash.");
            return Cli.ReturnType.ERROR;
        }

        // get the current repository
        AionRepositoryImpl repository = AionRepositoryImpl.inst();
        isSuccessful = repository.queryTransaction(hash, log);
        repository.close();

        if (isSuccessful) {
            return Cli.ReturnType.EXIT;
        } else {
            return Cli.ReturnType.ERROR;
        }
    }

    public static Cli.ReturnType printBlockDetails(long nbBlock) {
        // ensure mining is disabled
        CfgAion localCfg = CfgAion.inst();
        localCfg.dbFromXML();
        localCfg.getConsensus().setMining(false);

        AionLoggerFactory.initAll(Map.of(LogEnum.GEN, LogLevel.INFO));
        final Logger log = AionLoggerFactory.getLogger(LogEnum.GEN.name());

        // get the current blockchain
        AionBlockchainImpl blockchain = new AionBlockchainImpl(localCfg, null, false);

        try {
            List<Block> blocks = blockchain.getRepository().getAllChainBlockByNumber(nbBlock, log);

            if (blocks == null || blocks.isEmpty()) {
                log.error("Cannot find the block with given block height.");
                return Cli.ReturnType.ERROR;
            }

            for (Block b : blocks) {
                log.info(b.toString());
            }

            // Now print the transaction state. Only for the mainchain.
            // TODO: the worldstate can not read the data after the stateRoot has been setup, need to fix the issue first then the tooling can print the states between the block.

            Block mainChainBlock = blockchain.getBlockByNumber(nbBlock);
            if (mainChainBlock == null) {
                log.error("Cannot find the main chain block with given block height.");
                return Cli.ReturnType.ERROR;
            }

            Block parentBlock = blockchain.getBlockByHash(mainChainBlock.getParentHash());
            if (parentBlock == null) {
                log.error("Cannot find the parent block with given block height.");
                return Cli.ReturnType.ERROR;
            }

            blockchain.setBestBlock(parentBlock);
            Pair<AionBlockSummary, RepositoryCache> result = blockchain.tryImportWithoutFlush(mainChainBlock);
            log.info("Import result: " + (result == null ? ImportResult.INVALID_BLOCK : ImportResult.IMPORTED_BEST));
            if (result != null) {
                log.info("Block summary:\n" + result.getLeft() + "\n");
                log.info("RepoCacheDetails:\n" + result.getRight());
            }

            return Cli.ReturnType.EXIT;
        } catch (Exception e) {
            log.error("Error encountered while attempting to retrieve the block data.", e);
            return Cli.ReturnType.ERROR;
        } finally {
            blockchain.close();
        }
    }

    public static Cli.ReturnType writeBlocks(long count) {
        // read database configuration
        CfgAion.inst().dbFromXML();

        AionLoggerFactory.initAll(Map.of(LogEnum.GEN, LogLevel.INFO));
        final Logger log = AionLoggerFactory.getLogger(LogEnum.GEN.name());

        if (count < 1) {
            log.error("The given argument «" + count + "» is not valid.");
            count = 10L;
        }
        log.info("Printing top " + count + " blocks from database.");

        // get the current repository
        AionRepositoryImpl repository = AionRepositoryImpl.inst();

        try {
            String file = repository.dumpPastBlocks(count, CfgAion.inst().getBasePath());
            if (file == null) {
                log.error("The database is empty. Cannot print block information.");
            } else {
                log.info("Block information stored in " + file);
            }
        } catch (IOException e) {
            log.error("Exception encountered while writing blocks to file.", e);
        }

        repository.close();
        return Cli.ReturnType.EXIT;
    }

    public static Cli.ReturnType writeState(long level) {
        // read database configuration
        CfgAion.inst().dbFromXML();

        AionLoggerFactory.initAll(Map.of(LogEnum.GEN, LogLevel.INFO));
        final Logger log = AionLoggerFactory.getLogger(LogEnum.GEN.name());

        if (level == -1L) {
            log.info("Retrieving state for top main chain block...");
        } else if (level >= 0) {
            log.info("Retrieving state for main chain block at level " + level + "...");
        } else {
            log.info("Invalid hight: " + level + ". Cannot retrieve state.");
            return Cli.ReturnType.ERROR;
        }

        // get the current blockchain
        AionRepositoryImpl repository = AionRepositoryImpl.inst();
        repository.printStateTrieDump(level, log);
        repository.close();
        return Cli.ReturnType.EXIT;
    }

    public static Cli.ReturnType writeStateSize(long blockCount) {
        // read database configuration
        CfgAion.inst().dbFromXML();

        AionLoggerFactory.initAll(Map.of(LogEnum.GEN, LogLevel.INFO));
        final Logger log = AionLoggerFactory.getLogger(LogEnum.GEN.name());

        if (blockCount < 1) {
            log.error("The given argument «" + blockCount + "» is not valid.");
            blockCount = 2L;
        }

        log.error("Retrieving state size for top " + blockCount + " blocks.");

        // get the current repository
        AionRepositoryImpl repository = AionRepositoryImpl.inst();
        repository.printStateTrieSize(blockCount, log);
        repository.close();

        return Cli.ReturnType.EXIT;
    }

    public static Cli.ReturnType writeForTest(String[] parameters) {
        // read database configuration
        CfgAion.inst().dbFromXML();

        AionLoggerFactory.initAll(Map.of(LogEnum.GEN, LogLevel.INFO));
        final Logger log = AionLoggerFactory.getLogger(LogEnum.GEN.name());
        long blockNumber;

        try {
            blockNumber = Long.parseLong(parameters[0]);
        } catch (NumberFormatException e) {
            log.error("The given argument «" + parameters[0] + "» cannot be converted to a number.");
            return Cli.ReturnType.ERROR;
        }
        if (blockNumber < 2) { // requires a parent and grandparent
            log.error("Negative block values are not legal input values for this functionality.");
            return Cli.ReturnType.ERROR;
        } else {
            log.error("Retrieving consensus data for unit tests for the main chain block at level " + blockNumber + "...");

            // get the current blockchain
            AionRepositoryImpl repository = AionRepositoryImpl.inst();
            repository.dumpTestData(blockNumber, parameters, CfgAion.inst().getBasePath(), log);
            repository.close();

            return Cli.ReturnType.EXIT;
        }
    }

    public Composite getArgs() {
        return args;
    }

    public void setArgs(Composite args) {
        this.args = args;
    }

    public Cli.ReturnType runCommand() {
        try {
            args.checkOptions();
            if (args.help != null && args.help) {
                printUsage(System.out, this);
                return Cli.ReturnType.EXIT;
            } else if (args.dumpBlocksParam != null) {
                return writeBlocks(
                        Optional.of(args.dumpBlocksParam)
                                .filter(s -> !s.isEmpty())
                                .map(Long::parseLong)
                                .orElse(10L));
            } else if (args.dumpStateParam != null) {
                return writeState(
                        Optional.of(args.dumpStateParam)
                                .filter(s -> !s.isEmpty())
                                .map(Long::parseLong)
                                .orElse(-1L));
            } else if (args.dumpStateSizeParam != null) {
                return writeStateSize(
                        Optional.of(args.dumpStateSizeParam)
                                .filter(s -> !s.isEmpty())
                                .map(Long::parseLong)
                                .orElse(2L));
            } else if (args.dumpForTestParams != null) {
                return writeForTest(args.dumpForTestParams);
            } else if (args.queryBlockParams != null) {
                return printBlockDetails(args.queryBlockParams);
            } else if (args.queryTxParams != null) {
                return printTxDetails(args.queryTxParams);
            } else if (args.queryAccountParams != null) {
                return printAccountDetails(args.queryAccountParams);
            } else if (args.stopAtParam != null) {
                return stopAt(args.stopAtParam);
            } else if (args.fullSync != null) {
                return fullSync();
            }
        } catch (IllegalArgumentException e) {
            printUsage(System.out, this);
            return Cli.ReturnType.ERROR;
        }
        return Cli.ReturnType.ERROR;
    }

    public static class Composite {

        @CommandLine.Option(names = {"-h", "--help"})
        private Boolean help = null;

        @CommandLine.Option(
                names = {"write-blocks", "wb"},
                paramLabel = "<block_count>",
                description = "print top blocks from database",
                arity = "0..1")
        private String dumpBlocksParam = null;

        @CommandLine.Option(
                names = {"print-state-size", "cs"},
                paramLabel = "<block_count>",
                description = "retrieves the state size (node count) for the top block",
                arity = "0..1")
        private String dumpStateSizeParam = null;

        @CommandLine.Option(
                names = {"print-state", "ps"},
                paramLabel = "<block_count>",
                description = "retrieves the state for the top main chain blocks",
                arity = "0..1")
        private String dumpStateParam = null;

        @CommandLine.Option(
                names = {"print-for-test", "pf"},
                paramLabel = "<block_to_import>[skip-state][<contr_1>] [<contr_2>]...",
                description =
                        "retrieves block and multiple contract data from the blockchain to be used for"
                                + " consensus unit tests; the skip-state option can be used to"
                                + " retrieve the data only for contracts",
                arity = "1..*")
        private String[] dumpForTestParams = null;

        @CommandLine.Option(
                names = {"pb", "print-block"},
                paramLabel = "<block_number>",
                description = "retrieve block information",
                arity = "1")
        private Long queryBlockParams = null;

        @CommandLine.Option(
                names = {"pt", "print-tx"},
                paramLabel = "<transaction_hash>",
                description = "retrieve transaction information",
                arity = "1")
        private String queryTxParams = null;

        @CommandLine.Option(
                names = {"pa", "print-account"},
                paramLabel = "<account_address>",
                description = "retrieve account information",
                arity = "1")
        private String queryAccountParams = null;

        @CommandLine.Option(
                names = {"xs", "stop-at"},
                paramLabel = "<block_number>",
                description = "dumps the heap and shuts down after the specified block is imported",
                arity = "1")

        private Long stopAtParam = null;

        @CommandLine.Option(
            names = {"fs", "full-sync"},
            description = "Fully sync blocks from the given network and shutdown the kernel")
        private Boolean fullSync = null;

        void checkOptions() {
            if (stopAtParam == null
                    && queryAccountParams == null
                    && queryTxParams == null
                    && queryBlockParams == null
                    && dumpForTestParams == null
                    && dumpBlocksParam == null
                    && help == null
                    && dumpStateParam == null
                    && dumpStateSizeParam == null
                    && fullSync == null) {
                throw new IllegalArgumentException("Expected at least one argument");
            }
        }

        void setHelp(Boolean help) {
            this.help = help;
        }

        void setDumpBlocksParam(Long dumpBlocksParam) {
            this.dumpBlocksParam = String.valueOf(dumpBlocksParam);
        }

        void setDumpStateSizeParam(Long dumpStateSizeParam) {
            this.dumpStateSizeParam = String.valueOf(dumpStateSizeParam);
        }

        void setDumpStateParam(Long dumpStateParam) {
            this.dumpStateParam = String.valueOf(dumpStateParam);
        }

        void setDumpForTestParams(String[] dumpForTestParams) {
            this.dumpForTestParams = dumpForTestParams;
        }

        void setQueryBlockParams(Long queryBlockParams) {
            this.queryBlockParams = queryBlockParams;
        }

        void setQueryTxParams(String queryTxParams) {
            this.queryTxParams = queryTxParams;
        }

        void setQueryAccountParams(String queryAccountParams) {
            this.queryAccountParams = queryAccountParams;
        }

        void setStopAtParam(Long stopAtParam) {
            this.stopAtParam = stopAtParam;
        }

        void setFullSync(Boolean fullSync) {
            this.fullSync = fullSync;
        }
    }
}
