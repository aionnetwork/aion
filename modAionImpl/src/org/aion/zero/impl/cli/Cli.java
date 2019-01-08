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

import static org.aion.zero.impl.cli.Cli.ReturnType.ERROR;
import static org.aion.zero.impl.cli.Cli.ReturnType.EXIT;
import static org.aion.zero.impl.cli.Cli.ReturnType.RUN;
import static org.aion.zero.impl.config.Network.determineNetwork;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aion.base.util.Hex;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.account.Keystore;
import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.CfgSsl;
import org.aion.mcf.config.CfgSync;
import org.aion.zero.impl.Version;
import org.aion.zero.impl.config.Network;
import org.aion.zero.impl.db.RecoveryUtils;
import org.apache.commons.lang3.ArrayUtils;
import picocli.CommandLine;

/**
 * Command line interface.
 *
 * @author chris
 */
public class Cli {

    // TODO-Ale: consider using initial path from cfg
    private final String BASE_PATH = System.getProperty("user.dir");

    private final File keystoreDir =
            new File(System.getProperty("user.dir") + File.separator + CfgSsl.SSL_KEYSTORE_DIR);

    private final Arguments options = new Arguments();
    private final CommandLine parser = new CommandLine(options);

    public enum ReturnType {
        RUN(2),
        EXIT(0),
        ERROR(1);
        private final int value;

        ReturnType(int _value) {
            this.value = _value;
        }

        public int getValue() {
            return value;
        }
    }

    enum TaskPriority {
        NONE,
        HELP,
        VERSION,
        CONFIG,
        INFO,
        CREATE_ACCOUNT,
        LIST_ACCOUNTS,
        EXPORT_ACCOUNT,
        IMPORT_ACCOUNT,
        SSL,
        PRUNE_BLOCKS,
        REVERT,
        PRUNE_STATE,
        DUMP_STATE_SIZE,
        DUMP_STATE,
        DUMP_BLOCKS,
        DB_COMPACT
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public ReturnType call(final String[] args, Cfg cfg) {
        try {
            // the pre-process method handles arguments that are separated by space
            // parsing populates the options object
            parser.parse(Arguments.preProcess(args));
        } catch (Exception e) {
            System.out.println("Unable to parse the input arguments due to: ");
            if (e.getMessage() != null) {
                System.out.println(e.getMessage());
            } else {
                e.printStackTrace();
            }

            System.out.println();
            printHelp();
            return ERROR;
        }

        // make sure that there is no conflicting arguments; otherwise send warning
        checkArguments(options);

        try {
            // 1. the first set of options don't mix with -d and -n

            if (options.isHelp()) {
                printHelp();
                return EXIT;
            }

            if (options.isVersion() || options.isVersionTag()) {
                if (options.isVersion()) {
                    System.out.println("\nVersion");
                    System.out.println("--------------------------------------------");
                }
                System.out.println(Version.KERNEL_VERSION);
                return EXIT;
            }

            // 2. determine the network configuration

            if (options.getNetwork() != null
                    || (options.getConfig() != null && !options.getConfig().isEmpty())) {
                String strNet = options.getNetwork();
                // the network given in config overwrites the -n option
                if (options.getConfig() != null && !options.getConfig().isEmpty()) {
                    strNet = options.getConfig();
                }
                setNetwork(strNet, cfg);
                // no return -> allow for other parameters combined with -n
            }

            // 3. determine the execution folder path; influenced by --network

            if (options.getDirectory() != null) {
                if (!setDirectory(options.getDirectory(), cfg)) {
                    return ERROR;
                }
                // no return -> allow for other parameters combined with -d
            }

            // reading from correct config file
            File configFile = cfg.getExecConfigFile();
            if (!configFile.exists()) {
                configFile = cfg.getInitialConfigFile();
            } else {
                // marks that the files were read from the execution path
                File forkFile = cfg.getExecForkFile();
                if (forkFile.exists()) {
                    cfg.setReadConfigFiles(configFile, cfg.getExecGenesisFile(), forkFile);
                } else {
                    cfg.setReadConfigFiles(configFile, cfg.getExecGenesisFile());
                }
            }

            // reading from correct fork file
            File forkFile = cfg.getExecForkFile();
            if (forkFile == null || !forkFile.exists()) {
                forkFile = cfg.getInitialForkFile();
            }

            if (forkFile != null && forkFile.exists()) {
                cfg.setForkProperties(cfg.getNetwork(), forkFile);
            }
            // true means the UUID must be set
            boolean overwrite = cfg.fromXML(configFile);

            // determine the port configuration, can be combined with the -n, -d, -c, -i arguments

            if (options.getPort() != null) {

                int currentPort = cfg.getNet().getP2p().getPort();
                int portNumber = currentPort;
                boolean validPort = true;

                try {
                    portNumber = Integer.parseInt(options.getPort());
                } catch (NumberFormatException e) {
                    validPort = false;
                    System.out.println("Port must be a positive integer value");
                }

                if (portNumber < 0 || portNumber > 0xFFFF) {
                    validPort = false;
                    System.out.println("Port out of range: " + portNumber);
                }

                if (validPort && portNumber != currentPort) {
                    // update port in config
                    cfg.getNet().getP2p().setPort(portNumber);
                    overwrite = true;
                    System.out.println("Port set to: " + portNumber);
                } else {
                    System.out.println("Using the current port configuration: " + currentPort);
                }
                // no return, allow for other parameters combined with -p
            }

            // determine the sync compact configuration, can be combined with the -n, -d, -c, -p, -i
            // arguments

            if (options.getForceCompact() != null) {

                CfgSync cfgSync = cfg.getSync();
                String[] parameters = options.getForceCompact();

                if (parameters.length == 1) {

                    if (!parameters[0].equalsIgnoreCase("true")
                            && !parameters[0].equalsIgnoreCase("false")) {
                        System.out.println("enabled value must be true or false");
                    }
                    boolean compactEnabled = Boolean.parseBoolean(parameters[0]);
                    if (compactEnabled != cfgSync.getCompactEnabled()) {
                        cfgSync.setCompactEnabled(compactEnabled);
                        overwrite = true;
                    }
                    System.out.println("Compact enabled is set to: " + compactEnabled);

                } else if (options.getForceCompact().length == 2) {

                    int slowImportTime = cfgSync.getSlowImportTime();
                    int compactFrequency = cfgSync.getCompactFrequency();
                    boolean validCompact = true;

                    try {
                        slowImportTime = Integer.parseInt(parameters[0]);
                        compactFrequency = Integer.parseInt(parameters[1]);
                    } catch (NumberFormatException e) {
                        validCompact = false;
                    }

                    if (slowImportTime <= 0 || compactFrequency <= 0) {
                        validCompact = false;
                    }

                    if (!validCompact) {
                        System.out.println(
                                "slow_import and frequency values must be positive integers, compact disabled");
                        if (cfgSync.getCompactEnabled()) {
                            cfgSync.setCompactEnabled(false);
                            overwrite = true;
                        }
                    } else {
                        cfgSync.setCompactEnabled(true);
                        cfgSync.setSlowImportTime(slowImportTime);
                        cfgSync.setCompactFrequency(compactFrequency);
                        overwrite = true;
                        System.out.println(
                                "Compact enabled using the provided configuration: slow_import="
                                        + slowImportTime
                                        + " frequency="
                                        + compactFrequency);
                    }
                }
                // no return, allow for other parameters combined with --compact
            }

            // 4. can be influenced by the -n, -d, -p, --compact arguments above

            if (options.getConfig() != null) {
                // network was already set above

                // if the directory was set we generate a new file
                if (options.getDirectory() != null) {
                    configFile = cfg.getExecConfigFile();

                    // ensure path exists
                    File dir = cfg.getExecConfigDirectory();
                    if (!dir.exists()) {
                        if (!dir.mkdirs()) {
                            System.out.println(
                                    "ERROR: Unable to create directory: "
                                            + getRelativePath(dir.getAbsolutePath()));
                            return ERROR;
                        }
                    }
                    try {
                        configFile.createNewFile();
                    } catch (IOException e) {
                        System.out.println(
                                "ERROR: Unable to create file: "
                                        + getRelativePath(configFile.getAbsolutePath()));
                        return ERROR;
                    }
                }

                // save to disk
                cfg.toXML(null, configFile);

                System.out.println(
                        "\nNew config generated at: "
                                + getRelativePath(configFile.getAbsolutePath()));
                return ReturnType.EXIT;
            }

            // 5. options that can be influenced by the -d, -n, -p and --compact arguments

            if (options.isInfo()) {
                System.out.println(
                        "Reading config file from: "
                                + getRelativePath(configFile.getAbsolutePath()));
                if (overwrite) {
                    // updating the file in case the user id was not set; overwrite port
                    cfg.toXML(null, configFile);
                }
                printInfo(cfg);
                return ReturnType.EXIT;
            }

            // make directories for kernel execution
            makeDirs(configFile, forkFile, cfg);

            if (overwrite) {
                // updating the file in case the user id was not set; overwrite port
                cfg.toXML(null, cfg.getExecConfigFile());
            }

            // set correct keystore directory
            Keystore.setKeystorePath(cfg.getKeystoreDir().getAbsolutePath());

            if (options.isCreateAccount()) {
                if (!createAccount()) {
                    return ERROR;
                } else {
                    return EXIT;
                }
            }

            if (options.isListAccounts()) {
                if (!listAccounts()) {
                    return ERROR;
                } else {
                    return EXIT;
                }
            }

            if (options.getExportAccount() != null) {
                if (!exportPrivateKey(options.getExportAccount())) {
                    return ERROR;
                } else {
                    return EXIT;
                }
            }

            if (options.getImportAccount() != null) {
                if (!importPrivateKey(options.getImportAccount())) {
                    return ERROR;
                } else {
                    return EXIT;
                }
            }

            if (options.getSsl() != null) {
                String[] parameters = options.getSsl();

                if (parameters.length == 0 || parameters.length == 2) {
                    createKeystoreDirIfMissing();
                    Console console = System.console();
                    checkConsoleExists(console);

                    List<String> scriptArgs = new ArrayList<>();
                    scriptArgs.add("/bin/bash");
                    scriptArgs.add("script/generateSslCert.sh");
                    scriptArgs.add(getCertName(console));
                    scriptArgs.add(getCertPass(console));
                    // add the hostname and ip optionally passed in as cli args
                    scriptArgs.addAll(Arrays.asList(parameters));
                    new ProcessBuilder(scriptArgs).inheritIO().start().waitFor();
                    return EXIT;
                } else {
                    System.out.println(
                            "Incorrect usage of -s create command.\n"
                                    + "Command must enter both hostname AND ip or else neither one.");
                    return ERROR;
                }
            }

            if (options.isRebuildBlockInfo()) {
                System.out.println("Starting database clean-up.");
                RecoveryUtils.pruneAndCorrect();
                System.out.println("Finished database clean-up.");
                return EXIT;
            }

            if (options.getRevertToBlock() != null) {
                String block = options.getRevertToBlock();
                switch (revertTo(block)) {
                    case SUCCESS:
                        {
                            System.out.println(
                                    "Blockchain successfully reverted to block number "
                                            + block
                                            + ".");
                            return EXIT;
                        }
                    case FAILURE:
                        {
                            System.out.println("Unable to revert to block number " + block + ".");
                            return ERROR;
                        }
                    case ILLEGAL_ARGUMENT:
                    default:
                        {
                            return ERROR;
                        }
                }
            }

            if (options.getPruneStateOption() != null) {
                String pruning_type = options.getPruneStateOption();
                try {
                    RecoveryUtils.pruneOrRecoverState(pruning_type);
                    return EXIT;
                } catch (Exception e) {
                    System.out.println("Reorganizing the state storage FAILED due to:");
                    e.printStackTrace();
                    return ERROR;
                }
            }

            if (options.getDumpStateSizeCount() != null) {
                long block_count = 2L;
                String parameter = options.getDumpStateSizeCount();

                if (parameter.isEmpty()) {
                    System.out.println("Retrieving state size for top " + block_count + " blocks.");
                    RecoveryUtils.printStateTrieSize(block_count);
                    return EXIT;
                } else {
                    try {
                        block_count = Long.parseLong(parameter);
                    } catch (NumberFormatException e) {
                        System.out.println(
                                "The given argument «"
                                        + parameter
                                        + "» cannot be converted to a number.");
                        return ERROR;
                    }
                    if (block_count < 1) {
                        System.out.println("The given argument «" + parameter + "» is not valid.");
                        block_count = 2L;
                    }

                    System.out.println("Retrieving state size for top " + block_count + " blocks.");
                    RecoveryUtils.printStateTrieSize(block_count);
                    return EXIT;
                }
            }

            if (options.getDumpStateCount() != null) {
                long level = -1L;
                String parameter = options.getDumpStateCount();

                if (parameter.isEmpty()) {
                    System.out.println("Retrieving state for top main chain block...");
                    RecoveryUtils.printStateTrieDump(level);
                    return EXIT;
                } else {
                    try {
                        level = Long.parseLong(parameter);
                    } catch (NumberFormatException e) {
                        System.out.println(
                                "The given argument «"
                                        + parameter
                                        + "» cannot be converted to a number.");
                        return ERROR;
                    }
                    if (level == -1L) {
                        System.out.println("Retrieving state for top main chain block...");
                    } else {
                        System.out.println(
                                "Retrieving state for main chain block at level " + level + "...");
                    }
                    RecoveryUtils.printStateTrieDump(level);
                    return EXIT;
                }
            }

            if (options.getDumpBlocksCount() != null) {
                long count = 10L;
                String parameter = options.getDumpBlocksCount();

                if (parameter.isEmpty()) {
                    System.out.println("Printing top " + count + " blocks from database.");
                    RecoveryUtils.dumpBlocks(count);
                    return EXIT;
                } else {
                    try {
                        count = Long.parseLong(parameter);
                    } catch (NumberFormatException e) {
                        System.out.println(
                                "The given argument «"
                                        + parameter
                                        + "» cannot be converted to a number.");
                        return ERROR;
                    }
                    if (count < 1) {
                        System.out.println("The given argument «" + parameter + "» is not valid.");
                        count = 10L;
                    }

                    System.out.println("Printing top " + count + " blocks from database.");
                    RecoveryUtils.dumpBlocks(count);
                    return EXIT;
                }
            }

            if (options.isDbCompact()) {
                RecoveryUtils.dbCompact();
                return EXIT;
            }

            // if no return happened earlier, run the kernel
            return RUN;
        } catch (Exception e) {
            // TODO: should be moved to individual procedures
            System.out.println("");
            e.printStackTrace();
            return ERROR;
        }
    }

    /**
     * Utility method for truncating absolute paths wrt the {@link #BASE_PATH}.
     *
     * @return the path without the {@link #BASE_PATH} prefix.
     */
    private String getRelativePath(String path) {
        // if absolute paths are given with different prefix the replacement won't work
        return path.replaceFirst(BASE_PATH, ".");
    }

    /** Print the CLI help info. */
    private void printHelp() {
        String usage = parser.getUsageMessage();

        usage = usage.replaceFirst("OPTIONS]", "OPTIONS] [ARGUMENTS]");

        // the command line output has some styling characters in addition to the actual string
        // making the use of a regular expression necessary here
        usage = usage.replaceFirst(" \\[[^ ]*<hostname> <ip>.*]", "]");
        usage =
                usage.replaceFirst(
                        "<slow_import>([\\s\\S]*?)]+", "| <slow_import> <frequency>\u001B[0m");
        System.out.println(usage);
    }

    private void printInfo(Cfg cfg) {
        System.out.println("\nInformation");
        System.out.println(
                "----------------------------------------------------------------------------");
        System.out.println(
                "current: p2p://"
                        + cfg.getId()
                        + "@"
                        + cfg.getNet().getP2p().getIp()
                        + ":"
                        + cfg.getNet().getP2p().getPort());
        String[] nodes = cfg.getNet().getNodes();
        if (nodes != null && nodes.length > 0) {
            System.out.println("boot nodes list:");
            for (String node : nodes) {
                System.out.println("            " + node);
            }
        } else {
            System.out.println("boot nodes list is empty");
        }
        System.out.println(
                "p2p: " + cfg.getNet().getP2p().getIp() + ":" + cfg.getNet().getP2p().getPort());
    }

    /**
     * Sets the directory where the kernel will be executed.
     *
     * @param directory the directory to be used
     * @param cfg the configuration file containing the information
     * @return {@code true} when the given directory is valid, {@code false} otherwise.
     */
    private boolean setDirectory(String directory, Cfg cfg) {
        // use the path ignoring the current base path
        File file = new File(directory);
        if (!file.isAbsolute()) {
            // add the directory to the base path
            file = new File(BASE_PATH, directory);
        }

        if (!file.exists()) {
            if (!file.mkdirs()) {
                return false;
            }
        }

        if (file.isDirectory() && file.canWrite()) {
            cfg.setDataDirectory(file);
            return true;
        } else {
            return false;
        }
    }

    private void setNetwork(String network, Cfg cfg) {
        Network net = determineNetwork(network.toLowerCase());
        if (net == null) {
            // print error message and set default value
            printInvalidNetwork();
            net = Network.MAINNET;
        }
        cfg.setNetwork(net.toString());
    }

    private void printInvalidNetwork() {
        System.out.println("\nInvalid network selected!\n");
        System.out.println("------ Available Networks ------");
        System.out.println(Network.valuesString());
        System.out.println("--------------------------------\n");
    }

    /**
     * Creates the directories for persistence of the kernel data. Copies the config and genesis
     * files from the initial path for the execution directory.
     *
     * @param forkFile
     * @param cfg the configuration for the runtime kernel environment
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void makeDirs(File startConfigFile, File forkFile, Cfg cfg) {
        File file = cfg.getExecDir();
        if (!file.exists()) {
            file.mkdirs();
        }

        // create target config directory
        file = cfg.getExecConfigDirectory();
        if (!file.exists()) {
            file.mkdirs();
        }

        // copy config file
        File initial = startConfigFile;
        File target = cfg.getExecConfigFile();
        if (!initial.equals(target)) {
            copyRecursively(initial, target);

            // copy genesis file
            initial = cfg.getInitialGenesisFile();
            target = cfg.getExecGenesisFile();
            if (!initial.equals(target)) {
                copyRecursively(initial, target);
            }
        }

        // copy fork file
        initial = forkFile;
        target = cfg.getExecForkFile();
        if (!initial.equals(target)) {
            copyRecursively(initial, target);
        }

        // create target log directory
        file = cfg.getLogDir();
        if (!file.exists()) {
            file.mkdirs();
        }

        // create target database directory
        file = cfg.getDatabaseDir();
        if (!file.exists()) {
            file.mkdirs();
        }

        // create target keystore directory
        file = cfg.getKeystoreDir();
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    /**
     * Creates a new account.
     *
     * @return {@code true} only if the new account was successfully created, {@code false}
     *     otherwise.
     */
    private boolean createAccount() {
        String password, password2;
        try (InputStreamReader isr = new InputStreamReader(System.in);
                BufferedReader reader = new BufferedReader(isr)) {
            password = readPassword("Please enter a password: ", reader);
            password2 = readPassword("Please re-enter your password: ", reader);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        if (!password2.equals(password)) {
            System.out.println("Passwords do not match!");
            return false;
        }

        String address = Keystore.create(password);
        if (!address.equals("0x")) {
            System.out.println("A new account has been created: " + address);
            System.out.println(
                    "The account was stored in: " + getRelativePath(Keystore.getKeystorePath()));
            return true;
        } else {
            System.out.println("Failed to create an account!");
            return false;
        }
    }

    /** List all existing accounts. */
    @SuppressWarnings("SameReturnValue")
    private boolean listAccounts() {
        String[] accounts = Keystore.list();

        if (ArrayUtils.isNotEmpty(accounts)) {
            System.out.println(
                    "All accounts from: " + getRelativePath(Keystore.getKeystorePath()) + "\n");

            for (String account : accounts) {
                System.out.println("\t" + account);
            }
        } else {
            System.out.println(
                    "No accounts found at: " + getRelativePath(Keystore.getKeystorePath()));
        }
        return true;
    }

    /**
     * Dumps the private of the given account.
     *
     * @param address address of the account
     * @return {@code true} if the operation was successful, {@code false} otherwise.
     */
    private boolean exportPrivateKey(String address) {
        System.out.println(
                "Searching for account in: " + getRelativePath(Keystore.getKeystorePath()));

        if (!Keystore.exist(address)) {
            System.out.println("The account does not exist!");
            return false;
        }

        String password;
        try (InputStreamReader isr = new InputStreamReader(System.in);
                BufferedReader reader = new BufferedReader(isr)) {
            password = readPassword("Please enter your password: ", reader);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        ECKey key = Keystore.getKey(address, password);

        if (key != null) {
            System.out.println("Your private key is: 0x" + Hex.toHexString(key.getPrivKeyBytes()));
            return true;
        } else {
            System.out.println("Failed to unlock the account");
            return false;
        }
    }

    /**
     * Imports a private key.
     *
     * @param privateKey private key in hex string
     * @return {@code true} if the operation was successful, {@code false} otherwise.
     */
    private boolean importPrivateKey(String privateKey) {
        // TODO: the Hex.decode() method catches all exceptions which may cause
        // issues for other components
        byte[] raw = Hex.decode(privateKey.startsWith("0x") ? privateKey.substring(2) : privateKey);
        if (raw == null) {
            System.out.println("Invalid private key");
            return false;
        }

        ECKey key = ECKeyFac.inst().fromPrivate(raw);
        if (key == null) {
            System.out.println(
                    "Unable to recover private key."
                            + "Are you sure you did not import a public key?");
            return false;
        }

        String password, password2;
        try (InputStreamReader isr = new InputStreamReader(System.in);
                BufferedReader reader = new BufferedReader(isr)) {
            password = readPassword("Please enter a password: ", reader);
            password2 = readPassword("Please re-enter your password: ", reader);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        if (!password2.equals(password)) {
            System.out.println("Passwords do not match!");
            return false;
        }

        String address = Keystore.create(password, key);
        if (!address.equals("0x")) {
            System.out.println(
                    "The private key was imported to: "
                            + getRelativePath(Keystore.getKeystorePath())
                            + "\nThe address is: "
                            + address);
            return true;
        } else {
            System.out.println(
                    "Failed to import the private key. It may already exist in: "
                            + getRelativePath(Keystore.getKeystorePath()));
            return false;
        }
    }

    /**
     * Returns a password after prompting the user to enter it. This method attempts first to read
     * user input from a console environment and if one is not available it instead attempts to read
     * from reader.
     *
     * @param prompt The read-password prompt to display to the user.
     * @return The user-entered password.
     * @throws NullPointerException if prompt is null or if console unavailable and reader is null.
     */
    public String readPassword(String prompt, BufferedReader reader) {
        if (prompt == null) {
            throw new NullPointerException("readPassword given null prompt.");
        }

        Console console = System.console();
        if (console == null) {
            return readPasswordFromReader(prompt, reader);
        }
        return new String(console.readPassword(prompt));
    }

    private void checkArguments(Arguments options) {
        // Find priority of breaking task
        TaskPriority breakingTaskPriority = getBreakingTaskPriority(options);
        // Ensure that there is at least one breaking task
        if (breakingTaskPriority == TaskPriority.NONE) {
            // No breaking tasks; everything will be executed
            return;
        }
        // Get list of tasks that won't be executed
        Set<String> skippedTasks = getSkippedTasks(options, breakingTaskPriority);
        // Check that there are skipped tasks
        if (skippedTasks.isEmpty()) {
            return;
        }
        String errorMessage =
                String.format(
                        "Given arguments require incompatible tasks. Skipped arguments: %s.",
                        String.join(", ", skippedTasks));
        System.out.println(errorMessage);
    }

    TaskPriority getBreakingTaskPriority(Arguments options) {
        if (options.isHelp()) {
            return TaskPriority.HELP;
        }
        if (options.isVersion() || options.isVersionTag()) {
            return TaskPriority.VERSION;
        }
        if (options.getConfig() != null) {
            return TaskPriority.CONFIG;
        }
        if (options.isInfo()) {
            return TaskPriority.INFO;
        }
        if (options.isCreateAccount()) {
            return TaskPriority.CREATE_ACCOUNT;
        }
        if (options.isListAccounts()) {
            return TaskPriority.LIST_ACCOUNTS;
        }
        if (options.getExportAccount() != null) {
            return TaskPriority.EXPORT_ACCOUNT;
        }
        if (options.getImportAccount() != null) {
            return TaskPriority.IMPORT_ACCOUNT;
        }
        if (options.getSsl() != null) {
            return TaskPriority.SSL;
        }
        if (options.isRebuildBlockInfo()) {
            return TaskPriority.PRUNE_BLOCKS;
        }
        if (options.getRevertToBlock() != null) {
            return TaskPriority.REVERT;
        }
        if (options.getPruneStateOption() != null) {
            return TaskPriority.PRUNE_STATE;
        }
        if (options.getDumpStateSizeCount() != null) {
            return TaskPriority.DUMP_STATE_SIZE;
        }
        if (options.getDumpStateCount() != null) {
            return TaskPriority.DUMP_STATE;
        }
        if (options.getDumpBlocksCount() != null) {
            return TaskPriority.DUMP_BLOCKS;
        }
        if (options.isDbCompact()) {
            return TaskPriority.DB_COMPACT;
        }
        return TaskPriority.NONE;
    }

    Set<String> getSkippedTasks(Arguments options, TaskPriority breakingTaskPriority) {
        Set<String> skippedTasks = new HashSet<String>();
        if (breakingTaskPriority.compareTo(TaskPriority.VERSION) < 0) {
            if (options.isVersion()) {
                skippedTasks.add("-v");
            }
            if (options.isVersionTag()) {
                skippedTasks.add("--version");
            }
        }
        if (breakingTaskPriority.compareTo(TaskPriority.CONFIG) < 0) {
            if (options.getNetwork() != null) {
                skippedTasks.add("--network");
            }
            if (options.getDirectory() != null) {
                skippedTasks.add("--datadir");
            }
            if (options.getPort() != null) {
                skippedTasks.add("--port");
            }
            if (options.getForceCompact() != null) {
                skippedTasks.add("--force-compact");
            }
            if (options.getConfig() != null) {
                skippedTasks.add("--config");
            }
        }
        if (breakingTaskPriority.compareTo(TaskPriority.INFO) < 0 && options.isInfo()) {
            skippedTasks.add("--info");
        }
        if (breakingTaskPriority.compareTo(TaskPriority.CREATE_ACCOUNT) < 0
                && options.isCreateAccount()) {
            skippedTasks.add("--account create");
        }
        if (breakingTaskPriority.compareTo(TaskPriority.LIST_ACCOUNTS) < 0
                && options.isListAccounts()) {
            skippedTasks.add("--account list");
        }
        if (breakingTaskPriority.compareTo(TaskPriority.EXPORT_ACCOUNT) < 0
                && options.getExportAccount() != null) {
            skippedTasks.add("--account export");
        }
        if (breakingTaskPriority.compareTo(TaskPriority.IMPORT_ACCOUNT) < 0
                && options.getImportAccount() != null) {
            skippedTasks.add("--account import");
        }
        if (breakingTaskPriority.compareTo(TaskPriority.SSL) < 0 && options.getSsl() != null) {
            skippedTasks.add("-s create");
        }
        if (breakingTaskPriority.compareTo(TaskPriority.PRUNE_BLOCKS) < 0
                && options.isRebuildBlockInfo()) {
            skippedTasks.add("--prune-blocks");
        }
        if (breakingTaskPriority.compareTo(TaskPriority.REVERT) < 0
                && options.getRevertToBlock() != null) {
            skippedTasks.add("--revert");
        }
        if (breakingTaskPriority.compareTo(TaskPriority.PRUNE_STATE) < 0
                && options.getPruneStateOption() != null) {
            skippedTasks.add("--state");
        }
        if (breakingTaskPriority.compareTo(TaskPriority.DUMP_STATE_SIZE) < 0
                && options.getDumpStateSizeCount() != null) {
            skippedTasks.add("--dump-state-size");
        }
        if (breakingTaskPriority.compareTo(TaskPriority.DUMP_STATE) < 0
                && options.getDumpStateCount() != null) {
            skippedTasks.add("--dump-state");
        }
        if (breakingTaskPriority.compareTo(TaskPriority.DUMP_BLOCKS) < 0
                && options.getDumpBlocksCount() != null) {
            skippedTasks.add("--dump-blocks");
        }
        if (breakingTaskPriority.compareTo(TaskPriority.DB_COMPACT) < 0 && options.isDbCompact()) {
            skippedTasks.add("--db-compact");
        }
        return skippedTasks;
    }

    /**
     * Returns a password after prompting the user to enter it from reader.
     *
     * @param prompt The read-password prompt to display to the user.
     * @param reader The BufferedReader to read input from.
     * @return The user-entered password.
     * @throws NullPointerException if reader is null.
     */
    private String readPasswordFromReader(String prompt, BufferedReader reader) {
        if (reader == null) {
            throw new NullPointerException("readPasswordFromReader given null reader.");
        }
        System.out.println(prompt);
        try {
            return reader.readLine();
        } catch (IOException e) {
            System.err.println("Error reading from BufferedReader: " + reader);
            e.printStackTrace();
            System.exit(1);
        }
        return null; // Make compiler happy; never get here.
    }

    private RecoveryUtils.Status revertTo(String blockNumber) {
        // try to convert to long
        long block;

        try {
            block = Long.parseLong(blockNumber);
        } catch (NumberFormatException e) {
            System.out.println(
                    "The given argument «" + blockNumber + "» cannot be converted to a number.");
            return RecoveryUtils.Status.ILLEGAL_ARGUMENT;
        }

        return RecoveryUtils.revertTo(block);
    }

    private void createKeystoreDirIfMissing() {
        if (!keystoreDir.isDirectory()) {
            if (!keystoreDir.mkdir()) {
                System.out.println(
                        "Ssl keystore directory could not be created. "
                                + "Please check user permissions or create directory manually.");
                System.exit(1);
            }
            System.out.println();
        }
    }

    /** For security reasons we only want the ssl option to run in a console environment. */
    private void checkConsoleExists(Console console) {
        if (console == null) {
            System.out.println(
                    "No console found. This command can only be run interactively in a console environment.");
            System.exit(1);
        }
    }

    private String getCertName(Console console) {
        console.printf("Enter certificate name:\n");
        String certName = console.readLine();
        if ((certName == null) || (certName.isEmpty())) {
            System.out.println("Error: no certificate name entered.");
            System.exit(1);
        }
        return certName;
    }

    private String getCertPass(Console console) {
        int minPassLen = 7;
        String certPass =
                String.valueOf(
                        console.readPassword(
                                "Enter certificate password (at least "
                                        + minPassLen
                                        + " characters):\n"));
        if (certPass.isEmpty()) {
            System.out.println("Error: no certificate password entered.");
            System.exit(1);
        } else if (certPass.length() < minPassLen) {
            System.out.println(
                    "Error: certificate password must be at least "
                            + minPassLen
                            + " characters long.");
            System.exit(1);
        }
        return certPass;
    }

    // Methods below taken from FileUtils class
    public static boolean copyRecursively(File src, File target) {
        if (src.isDirectory()) {
            return copyDirectoryContents(src, target);
        } else {
            try {
                Files.copy(src, target);
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static boolean copyDirectoryContents(File src, File target) {
        Preconditions.checkArgument(src.isDirectory(), "Source dir is not a directory: %s", src);

        // Don't delete symbolic link directories
        if (isSymbolicLink(src)) {
            return false;
        }

        target.mkdirs();
        Preconditions.checkArgument(target.isDirectory(), "Target dir is not a directory: %s", src);

        boolean success = true;
        for (File file : listFiles(src)) {
            success = copyRecursively(file, new File(target, file.getName())) && success;
        }
        return success;
    }

    private static boolean isSymbolicLink(File file) {
        try {
            File canonicalFile = file.getCanonicalFile();
            File absoluteFile = file.getAbsoluteFile();
            File parentFile = file.getParentFile();
            // a symbolic link has a different name between the canonical and absolute path
            return !canonicalFile.getName().equals(absoluteFile.getName())
                    ||
                    // or the canonical parent path is not the same as the file's parent path,
                    // provided the file has a parent path
                    parentFile != null
                            && !parentFile.getCanonicalPath().equals(canonicalFile.getParent());
        } catch (IOException e) {
            // error on the side of caution
            return true;
        }
    }

    private static ImmutableList<File> listFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(files);
    }
}
