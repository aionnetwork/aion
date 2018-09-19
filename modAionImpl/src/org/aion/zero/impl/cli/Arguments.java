/*
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
 */
package org.aion.zero.impl.cli;

import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi.Style;
import picocli.CommandLine.Help.ColorScheme;
import picocli.CommandLine.Option;

/**
 * Command line arguments for the Aion kernel.
 *
 * @author Alexandra Roatis
 */
@Command(name = "./aion.sh", separator = " ", sortOptions = false, abbreviateSynopsis = true)
public class Arguments {

    // usage information
    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "display help information")
    private boolean help = false;

    // account management
    @Option(
            names = {"ac", "-a create"},
            description = "create a new account")
    private boolean createAccount = false;

    @Option(
            names = {"al", "-a list"},
            description = "list all existing accounts")
    private boolean listAccounts = false;

    @Option(
            names = {"ae", "-a export"},
            paramLabel = "<account>",
            description = "export private key of an account")
    private String exportAccount = null;

    @Option(
            names = {"ai", "-a import"},
            paramLabel = "<key>",
            description = "import private key")
    private String importAccount = null;

    // config generation
    @Option(
            names = {"-c", "--config"},
            arity = "0..1",
            paramLabel = "<network>",
            description =
                    "create config for the selected network\noptions: mainnet, conquest, mastery")
    public String config = null;

    // get information and version
    @Option(
            names = {"-i", "--info"},
            description = "display information")
    public boolean info = false;

    @Option(
            names = {"-v"},
            description = "display version")
    public boolean version = false;

    @Option(
            names = {"--version"},
            description = "display version tag")
    public boolean versionTag = false;

    // create ssl certificate
    @Option(
            names = {"sc", "-s create"},
            arity = "0..2",
            paramLabel = "<hostname> <ip>",
            description =
                    "create a ssl certificate for:\n - localhost (when no parameters are given), or"
                            + "\n - the given hostname and ip")
    public String[] ssl = null;

    // offline block management
    @Option(
            names = {"pb", "--prune-blocks"},
            description = "remove blocks on side chains and update block info")
    public boolean rebuildBlockInfo = false;

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
                    "execute kernel with selected network\noptions: mainnet, conquest, mastery")
    public String network = null;

    @Option(
            names = {"-d", "--datadir"},
            description = "execute kernel with selected database directory")
    public String directory = null;

    // offline database query and update
    @Option(
            names = {"ps", "--state"},
            paramLabel = "<prune_mode>",
            description = "reorganize the state storage\noptions: FULL, TOP, SPREAD")
    public String pruntStateOption = null;

    // print info from db
    @Option(
            names = {"--dump-blocks"},
            arity = "0..1",
            paramLabel = "<block_count>",
            description = "print top blocks from database")
    public String dumpBlocksCount = null;

    @Option(
            names = {"--dump-state-size"},
            arity = "0..1",
            paramLabel = "<block_count>",
            description = "retrieves the state size (node count) for the top blocks")
    public String dumpStateSizeCount = null;

    @Option(
            names = {"--dump-state"},
            arity = "0..1",
            paramLabel = "<block_count>",
            description = "retrieves the state for the top main chain blocks")
    public String dumpStateCount = null;

    @Option(
            names = {"--db-compact"},
            description = "if using leveldb, it triggers its database compaction processes")
    public boolean dbCompact;

    /** Compacts the account options into specific commands. */
    public static String[] preprocess(String[] arguments) {
        List<String> list = new ArrayList<>();

        int i = 0;
        while (i < arguments.length) {
            if (arguments[i].equals("-a")
                    || arguments[i].equals("--account")
                    || arguments[i].equals("-s")) {
                list.add(arguments[i] + " " + arguments[i + 1]);
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

    public boolean isCreateAccount() {
        return createAccount;
    }

    public boolean isListAccounts() {
        return listAccounts;
    }

    public String getExportAccount() {
        return exportAccount;
    }

    public String getImportAccount() {
        return importAccount;
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

    public String getPruneStateOption() {
        return pruntStateOption;
    }

    public String getDumpBlocksCount() {
        return dumpBlocksCount;
    }

    public String getDumpStateSizeCount() {
        return dumpStateSizeCount;
    }

    public String getDumpStateCount() {
        return dumpStateCount;
    }

    public boolean isDbCompact() {
        return dbCompact;
    }

    public static void main(String... args) {
        Arguments params = new Arguments();
        String[] argv = {"-a create", "-a list", "-a export", "0x123"};

        CommandLine commandLine = new CommandLine(params);
        ColorScheme colorScheme =
                new ColorScheme()
                        .commands(Style.bold, Style.underline) // combine multiple styles
                        .options(Style.fg_yellow) // yellow foreground color
                        .parameters(Style.fg_yellow)
                        .optionParams(Style.italic);
        commandLine.usage(System.out, colorScheme);
        // commandLine.usage(System.out);

        commandLine.parse(argv);
        System.out.println(params.createAccount);
        System.out.println(params.listAccounts);
        System.out.println(params.exportAccount.toString());

        System.out.println(commandLine.getUsageMessage(colorScheme));
    }
}
