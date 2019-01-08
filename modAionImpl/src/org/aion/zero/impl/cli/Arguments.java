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
import picocli.CommandLine.Command;
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
            names = {"ac", "-a create", "--account create"},
            description = "create a new account")
    private boolean createAccount = false;

    @Option(
            names = {"al", "-a list", "--account list"},
            description = "list all existing accounts")
    private boolean listAccounts = false;

    @Option(
            names = {"ae", "-a export", "--account export"},
            paramLabel = "<account>",
            description = "export private key of an account")
    private String exportAccount = null;

    @Option(
            names = {"ai", "-a import", "--account import"},
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
                    "execute kernel with selected network\noptions: mainnet, conquest, mastery")
    private String network = null;

    @Option(
            names = {"-d", "--datadir"},
            description = "execute kernel with selected database directory")
    private String directory = null;

    @Option(
            names = {"-p", "--port"},
            description = "execute kernel with selected port")
    private String port = null;

    @Option(
            names = {"--compact"},
            arity = "1..2",
            paramLabel = "<enabled> <slow_import> <frequency>",
            description =
                    "enable/disable compact during sync when one boolean parameter is given, or "
                            + "enable when two values are provided for slow_import and frequency in milliseconds")
    private String[] compact = null;

    // offline database query and update
    @Option(
            names = {"ps", "--state"},
            paramLabel = "<prune_mode>",
            description = "reorganize the state storage\noptions: FULL, TOP, SPREAD")
    private String pruntStateOption = null;

    // print info from db
    @Option(
            names = {"--dump-blocks"},
            arity = "0..1",
            paramLabel = "<block_count>",
            description = "print top blocks from database")
    private String dumpBlocksCount = null;

    @Option(
            names = {"--dump-state-size"},
            arity = "0..1",
            paramLabel = "<block_count>",
            description = "retrieves the state size (node count) for the top blocks")
    private String dumpStateSizeCount = null;

    @Option(
            names = {"--dump-state"},
            arity = "0..1",
            paramLabel = "<block_count>",
            description = "retrieves the state for the top main chain blocks")
    private String dumpStateCount = null;

    @Option(
            names = {"--db-compact"},
            description = "if using leveldb, it triggers its database compaction processes")
    private boolean dbCompact;

    /** Compacts the account options into specific commands. */
    public static String[] preProcess(String[] arguments) {
        List<String> list = new ArrayList<>();

        int i = 0;
        while (i < arguments.length) {
            if (arguments[i].equals("-a")
                    || arguments[i].equals("--account")
                    || arguments[i].equals("-s")) {
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

    public String getPort() {
        return port;
    }

    public String[] getCompact() {
        return compact;
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
}
