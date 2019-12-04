package org.aion.zero.impl.cli;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command line arguments for the Aion kernel.
 *
 * @author Alexandra Roatis
 */
@Command(
    name = "./aion.sh",
    separator = " ",
    sortOptions = false,
    abbreviateSynopsis = true,
    subcommands = {DevCLI.class, AccountCli.class})
public class Arguments {

    // usage information
    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "display help information")
    private boolean help = false;

    // config generation
    @Option(
            names = {"-c", "--config"},
            arity = "0..1",
            paramLabel = "<network>",
            description =
                    "create config for the selected network\noptions: mainnet, amity, custom")
    private String config = null;

    // get information and version
    @Option(
            names = {"-i", "--info"},
            description = "display information")
    private boolean info = false;

    @Option(
            names = {"-v"},
            description = "display version")
    private boolean version = false;

    @Option(
            names = {"--version"},
            description = "display version tag")
    private boolean versionTag = false;

    // create ssl certificate
    @Option(
            names = {"sc", "-s create"},
            arity = "0..2",
            paramLabel = "<hostname> <ip>",
            description =
                    "create a ssl certificate for:\n - localhost (when no parameters are given), or"
                            + "\n - the given hostname and ip")
    private String[] ssl = null;

    // offline block management
    @Option(
            names = {"pb", "--prune-blocks"},
            description = "remove blocks on side chains and update block info")
    private boolean rebuildBlockInfo = false;

    @Option(
            names = {"-r", "--revert"},
            arity = "1",
            paramLabel = "<block_number>",
            description = "revert database state to given block number")
    private String revertToBlock = null;

    // network and directory setup
    @Option(
            names = {"-n", "--network"},
            description =
                    "execute kernel with selected network\noptions: mainnet, amity, custom")
    private String network = null;

    @Option(
            names = {"-d", "--datadir"},
            description = "execute kernel with selected database directory")
    private String directory = null;

    @Option(
            names = {"-p", "--port"},
            description = "execute kernel with selected port")
    private String port = null;

    // offline database query and update
    @Option(
            names = {"ps", "--state"},
            paramLabel = "<prune_mode>",
            description = "reorganize the state storage\noptions: FULL, TOP, SPREAD")
    private String pruntStateOption = null;

    @Option(
            names = {"--db-compact"},
            description = "if using leveldb, it triggers its database compaction processes")
    private boolean dbCompact;

    @Option(
            names = {"--redo-import"},
            arity = "0..1",
            paramLabel = "<start_height>",
            description =
                    "drops all databases except for block and index when not given a parameter or starting from 0 and redoes import of all known main chain blocks")
    private String redoImport = null;

    /** Compacts the account options into specific commands. */
    public static String[] preProcess(String[] arguments) {
        List<String> list = new ArrayList<>();

        int i = 0;
        while (i < arguments.length) {
            if (arguments[i].equals("-s")) {
                if (i + 1 < arguments.length) {
                    list.add(arguments[i] + " " + arguments[i + 1]);
                } else {
                    list.add(arguments[i]);
                }
                i++;
            } else {
                list.add(arguments[i]);
            }
            i++;
        }

        return list.toArray(new String[list.size()]);
    }

    public boolean isHelp() {
        return help;
    }

    public String getConfig() {
        return config;
    }

    public boolean isInfo() {
        return info;
    }

    public boolean isVersion() {
        return version;
    }

    public boolean isVersionTag() {
        return versionTag;
    }

    public String[] getSsl() {
        return ssl;
    }

    public boolean isRebuildBlockInfo() {
        return rebuildBlockInfo;
    }

    public String getRevertToBlock() {
        return revertToBlock;
    }

    public String getNetwork() {
        return network;
    }

    public String getDirectory() {
        return directory;
    }

    public String getPort() {
        return port;
    }

    public String getPruneStateOption() {
        return pruntStateOption;
    }

    public boolean isDbCompact() {
        return dbCompact;
    }

    public String isRedoImport() {
        return redoImport;
    }
}
