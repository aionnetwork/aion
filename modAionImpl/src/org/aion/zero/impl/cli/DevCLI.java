package org.aion.zero.impl.cli;

import java.io.PrintStream;
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

    @CommandLine.ArgGroup() private Composite args;

    /*
     * Creates an instance of the DevCli class and prints the usage information to the specified stream
     */
    public static void printUsage(PrintStream stream, DevCLI instance) {
        CommandLine.usage(instance, stream);
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
        } catch (IllegalArgumentException e) {
            printUsage(System.out, this);
            return Cli.ReturnType.ERROR;
        }

        return Cli.ReturnType.EXIT;
    }

    public static class Composite {

        @CommandLine.Option(names = {"-h", "--help"})
        private Boolean help = null;

        @CommandLine.Option(
                names = {"write-blocks", "wb"},
                paramLabel = "<block_count>",
                description = "print top blocks from database",
                arity = "1")
        private Long dumpBlocksParam = null;

        @CommandLine.Option(
                names = {"print-state-size", "cs"},
                paramLabel = "<block_count>",
                description = "retrieves the state size (node count) for the top block",
                arity = "1")
        private Long dumpStateSizeParam = null;

        @CommandLine.Option(
                names = {"print-state", "ps"},
                paramLabel = "<block_count>",
                description = "retrieves the state for the top main chain blocks",
                arity = "1")
        private Long dumpStateParam = null;

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
            this.dumpBlocksParam = dumpBlocksParam;
        }

        void setDumpStateSizeParam(Long dumpStateSizeParam) {
            this.dumpStateSizeParam = dumpStateSizeParam;
        }

        void setDumpStateParam(Long dumpStateParam) {
            this.dumpStateParam = dumpStateParam;
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
