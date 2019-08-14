package org.aion.zero.impl.cli;

import java.io.PrintStream;
import java.util.Optional;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.db.DBUtils;
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
        AionBlockchainImpl.shutdownHook = blockNumber;
        return Cli.ReturnType.RUN;
    }

    public static Cli.ReturnType printAccountDetails(String strAddress) {
        AionAddress address;

        try {
            address = new AionAddress(ByteUtil.hexStringToBytes(strAddress));
        } catch (NumberFormatException e) {
            System.out.println(
                    "The given argument «"
                            + strAddress
                            + "» cannot be converted to a valid account address.");
            return Cli.ReturnType.ERROR;
        }

        DBUtils.Status status = DBUtils.queryAccount(address);

        if (status.equals(DBUtils.Status.SUCCESS)) {
            return Cli.ReturnType.EXIT;
        } else {
            return Cli.ReturnType.ERROR;
        }
    }

    public static Cli.ReturnType printTxDetails(String txHash) {
        byte[] hash;
        DBUtils.Status status;
        try {
            hash = ByteUtil.hexStringToBytes(txHash);
        } catch (NumberFormatException e) {
            System.out.println(
                    "The given argument «"
                            + txHash
                            + "» cannot be converted to a valid transaction hash.");
            return Cli.ReturnType.ERROR;
        }

        status = DBUtils.queryTransaction(hash);

        if (status == DBUtils.Status.SUCCESS) {
            return Cli.ReturnType.EXIT;
        } else {
            return Cli.ReturnType.ERROR;
        }
    }

    public static Cli.ReturnType printBlockDetails(long param) {
        DBUtils.Status status = DBUtils.queryBlock(param);

        if (status == DBUtils.Status.SUCCESS) {
            return Cli.ReturnType.EXIT;
        } else {
            System.out.println("Invalid block query! Please check your input argument.");
            return Cli.ReturnType.ERROR;
        }
    }

    public static Cli.ReturnType writeBlocks(long count) {
        if (count < 1) {
            System.out.println("The given argument «" + count + "» is not valid.");
            count = 10L;
        }
        System.out.println("Printing top " + count + " blocks from database.");
        DBUtils.dumpBlocks(count);
        return Cli.ReturnType.EXIT;
    }

    public static Cli.ReturnType writeState(long level) {
        if (level == -1L) {
            System.out.println("Retrieving state for top main chain block...");
        } else {
            System.out.println("Retrieving state for main chain block at level " + level + "...");
        }
        DBUtils.printStateTrieDump(level);
        return Cli.ReturnType.EXIT;
    }

    public static Cli.ReturnType writeStateSize(long blockCount) {
        if (blockCount < 1) {
            System.out.println("The given argument «" + blockCount + "» is not valid.");
            blockCount = 2L;
        }

        System.out.println("Retrieving state size for top " + blockCount + " blocks.");
        DBUtils.printStateTrieSize(blockCount);
        return Cli.ReturnType.EXIT;
    }

    public static Cli.ReturnType writeForTest(String[] parameters) {
        long level;

        try {
            level = Long.parseLong(parameters[0]);
        } catch (NumberFormatException e) {
            System.out.println(
                    "The given argument «" + parameters[0] + "» cannot be converted to a number.");
            return Cli.ReturnType.ERROR;
        }
        if (level < 2) { // requires a parent and grandparent
            System.out.println(
                    "Negative block values are not legal input values for this functionality.");
            return Cli.ReturnType.ERROR;
        } else {
            System.out.println(
                    "Retrieving consensus data for unit tests for the main chain block at level "
                            + level
                            + "...");

            DBUtils.dumpTestData(level, parameters);
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

        void checkOptions() {
            if (stopAtParam == null
                    && queryAccountParams == null
                    && queryTxParams == null
                    && queryBlockParams == null
                    && dumpForTestParams == null
                    && dumpBlocksParam == null
                    && help == null
                    && dumpStateParam == null
                    && dumpStateSizeParam == null) {
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
    }
}
