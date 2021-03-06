package org.aion.zero.impl.cli;

import static org.aion.zero.impl.cli.Cli.ReturnType.ERROR;
import static org.aion.zero.impl.cli.Cli.ReturnType.EXIT;
import static org.aion.zero.impl.cli.Cli.ReturnType.RUN;
import static org.aion.zero.impl.config.Network.determineNetwork;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.config.CfgDb;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.vm.avm.AvmConfigurations;
import org.aion.zero.impl.vm.avm.schedule.AvmVersionSchedule;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.keystore.Keystore;
import org.aion.zero.impl.config.CfgSsl;
import org.aion.zero.impl.SystemExitCodes;
import org.aion.zero.impl.Version;
import org.aion.zero.impl.config.Network;
import org.slf4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

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
    private final CommandLine parser;
    private final EditCli editCli;
    private final PasswordReader passwordReader;

    public Cli() {
        this(PasswordReader.inst());
    }

    public Cli(PasswordReader reader){
        this.passwordReader = reader;
        editCli = new EditCli();
        parser = new CommandLine(options)
            .addSubcommand("edit",editCli);
    }

    public enum ReturnType {
        RUN(2),
        EXIT(SystemExitCodes.NORMAL),
        ERROR(SystemExitCodes.INITIALIZATION_ERROR);
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
        ACCOUNT,
        SSL,
        PRUNE_BLOCKS,
        REVERT,
        PRUNE_STATE,
        DEV,
        DB_COMPACT,
        REDO_IMPORT
    }

    public ReturnType callAndInitializeAvm(String[] args, CfgAion cfg) {
        return call(args, cfg, true);
    }

    public ReturnType callAndDoNotInitializeAvm(String[] args, CfgAion cfg) {
        return call(args, cfg, false);
    }

    private ReturnType call(final String[] args, CfgAion cfg, boolean initializeAvm) {
        final CommandLine.ParseResult parseResult;
        try {
            // the pre-process method handles arguments that are separated by space
            // parsing populates the options object
            parseResult = parser.parseArgs(Arguments.preProcess(args));
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
        checkArguments(options, parseResult);

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
                cfg.setReadConfigFile(configFile);
            }

            // reading from correct fork file
            File forkFile = cfg.getForkFile();
            if (forkFile != null && forkFile.exists()) {
                cfg.setForkProperties(cfg.getNetwork(), forkFile);
            }
            // true means the UUID must be set
            boolean overwrite = cfg.fromXML(configFile);

            // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ initialize the avm ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            if (initializeAvm) {
                try {
                    // Grab the project root directory.
                    String projectRootDirectory = System.getProperty("user.dir") + File.separator;

                    // Create the multi-version schedule. Note that avm version 1 is always enabled, from block zero
                    // because it handles balance transfers. The kernel is responsible for ensuring it is not called
                    // with anything else.
                    Properties forkProperties = CfgAion.inst().getFork().getProperties();
                    String fork2 = forkProperties.getProperty("fork1.0");

                    AvmVersionSchedule schedule;
                    if (fork2 != null) {
                        schedule = AvmVersionSchedule.newScheduleForBothVersions(0, Long.valueOf(fork2), 100);
                    } else {
                        schedule = AvmVersionSchedule.newScheduleForOnlySingleVersionSupport(0, 100);
                    }

                    AvmConfigurations.initializeConfigurationsAsReadOnly(schedule, projectRootDirectory);
                } catch (Exception e) {
                    System.out.println("A fatal error occurred attempting to configure the AVM: " + e.getMessage());
                    System.exit(SystemExitCodes.INITIALIZATION_ERROR);
                }
            }
            // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

            // determine the port configuration, can be combined with the -n, -d, -c, -i arguments
            if (parseResult.subcommand() != null &&
                    parseResult.subcommand().commandSpec().userObject().getClass() == EditCli.class &&
                    editCli.runCommand(cfg)){
                overwrite = true;
            }

            if (editCli.help) {
                return ReturnType.EXIT;
            }

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
            CommandSpec commandSpec = findCommandSpec(parseResult, AccountCli.class);
            if (commandSpec !=null) {
                return ((AccountCli)commandSpec.userObject()).runCommand(
                    passwordReader);
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
                CfgAion.inst().dbFromXML();

                Map<LogEnum, LogLevel> cfgLog = new HashMap<>();
                cfgLog.put(LogEnum.GEN, LogLevel.INFO);
                AionLoggerFactory.initAll(cfgLog);

                // get the current blockchain
                AionRepositoryImpl repository = AionRepositoryImpl.inst();
                repository.pruneAndCorrectBlockStore(AionLoggerFactory.getLogger(LogEnum.GEN.name()));
                repository.close();
                System.out.println("Finished database clean-up.");
                return EXIT;
            }

            if (options.getRevertToBlock() != null) {
                String block = options.getRevertToBlock();
                if (revertTo(block)) {
                    System.out.println("Blockchain successfully reverted to block number " + block + ".");
                    return EXIT;
                } else {
                    System.out.println("Unable to revert to block number " + block + ".");
                    return ERROR;
                }
            }

            if (options.getPruneStateOption() != null) {
                String pruning_type = options.getPruneStateOption();
                try {
                    // ensure mining is disabled
                    CfgAion localCfg = CfgAion.inst();
                    localCfg.fromXML();
                    localCfg.getConsensus().setMining(false);

                    // setting pruning to the version requested
                    CfgDb.PruneOption option = CfgDb.PruneOption.fromValue(pruning_type);
                    localCfg.getDb().setPrune(option.toString());

                    AionLoggerFactory.initAll(Map.of(LogEnum.GEN, LogLevel.INFO));
                    final Logger log = AionLoggerFactory.getLogger(LogEnum.GEN.name());

                    log.info("Reorganizing the state storage to " + option + " mode ...");

                    AionBlockchainImpl chain = new AionBlockchainImpl(localCfg, null, false);
                    chain.pruneOrRecoverState(pruning_type.equals("spread"), localCfg.getGenesis(), log);
                    chain.close();
                    return EXIT;
                } catch (Exception e) {
                    System.out.println("Reorganizing the state storage FAILED due to:");
                    e.printStackTrace();
                    return ERROR;
                }
            }
            CommandLine.Model.CommandSpec spec = findCommandSpec(parseResult, DevCLI.class);
            if (spec!=null){
                ReturnType returnType = ((DevCLI) spec.userObject()).runCommand();
                if (returnType !=RUN){
                    return returnType;
                }
            }
            if (options.isDbCompact()) {
                // read database configuration
                CfgAion.inst().dbFromXML();
                AionLoggerFactory.initAll(Map.of(LogEnum.DB, LogLevel.INFO));

                // get the current blockchain
                AionRepositoryImpl repository = AionRepositoryImpl.inst();

                // compact database after the changes were applied
                repository.compact();
                repository.close();
                return EXIT;
            }

            if (options.isRedoImport() != null) {
                long height = 0L;
                String parameter = options.isRedoImport();

                // ensure mining is disabled
                CfgAion localCfg = CfgAion.inst();
                localCfg.dbFromXML();
                localCfg.getConsensus().setMining(false);

                AionLoggerFactory.initAll(Map.of(LogEnum.GEN, LogLevel.INFO));
                final Logger log = AionLoggerFactory.getLogger(LogEnum.GEN.name());

                if (height < 0) {
                    log.error("Negative values are not valid as starting height. Nothing to do.");
                    return ERROR;
                }

                log.info("Importing stored blocks INITIATED...");

                AionBlockchainImpl chain = new AionBlockchainImpl(localCfg, null, false);

                if (parameter.isEmpty()) {
                    chain.redoMainChainImport(height, localCfg.getGenesis(), log);
                    chain.close();
                    return EXIT;
                } else {
                    try {
                        height = Long.parseLong(parameter);
                    } catch (NumberFormatException e) {
                        log.error("The given argument «" + parameter + "» cannot be converted to a number.");
                        chain.close();
                        return ERROR;
                    }

                    chain.redoMainChainImport(height, localCfg.getGenesis(), log);
                    chain.close();
                    return EXIT;
                }
            }


            // if no return happened earlier, run the kernel
            return RUN;
        } catch (Exception e) {
            // TODO: should be moved to individual procedures
            System.out.println();
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

    private void printInfo(CfgAion cfg) {
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
    private boolean setDirectory(String directory, CfgAion cfg) {
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

    private void setNetwork(String network, CfgAion cfg) {
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
    private void makeDirs(File startConfigFile, File forkFile, CfgAion cfg) {
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

    private void checkArguments(Arguments options, CommandLine.ParseResult result) {
        // Find priority of breaking task
        TaskPriority breakingTaskPriority = getBreakingTaskPriority(options, result);
        // Ensure that there is at least one breaking task
        if (breakingTaskPriority == TaskPriority.NONE) {
            // No breaking tasks; everything will be executed
            return;
        }
        // Get list of tasks that won't be executed
        Set<String> skippedTasks = getSkippedTasks(options, breakingTaskPriority,  result);
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

    TaskPriority getBreakingTaskPriority(Arguments options, CommandLine.ParseResult result){
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
        if(findCommandSpec(result, DevCLI.class) !=null){
            return TaskPriority.DEV;
        }
        if (findCommandSpec(result, AccountCli.class) != null) {
            return TaskPriority.ACCOUNT;
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
        if (options.isDbCompact()) {
            return TaskPriority.DB_COMPACT;
        }
        if (options.isRedoImport() != null) {
            return TaskPriority.REDO_IMPORT;
        }
        return TaskPriority.NONE;
    }



    Set<String> getSkippedTasks(Arguments options, TaskPriority breakingTaskPriority, CommandLine.ParseResult parseResult) {
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
            if (options.getConfig() != null) {
                skippedTasks.add("--config");
            }
        }
        if (breakingTaskPriority.compareTo(TaskPriority.INFO) < 0 && options.isInfo()) {
            skippedTasks.add("--info");
        }
        if (breakingTaskPriority.compareTo(TaskPriority.DEV) < 0 && findCommandSpec(parseResult, DevCLI.class) != null){
            skippedTasks.add("dev");
        }
        if (breakingTaskPriority.compareTo(TaskPriority.ACCOUNT) < 0
                && findCommandSpec(parseResult, AccountCli.class) != null) {
            skippedTasks.add("account");
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
        if (breakingTaskPriority.compareTo(TaskPriority.DB_COMPACT) < 0 && options.isDbCompact()) {
            skippedTasks.add("--db-compact");
        }
        if (breakingTaskPriority.compareTo(TaskPriority.REDO_IMPORT) < 0
                && options.isRedoImport() != null) {
            skippedTasks.add("--redo-import");
        }

        return skippedTasks;
    }

    /**
     * @return {@code true} if successful and {@code false} in case of any failure
     */
    private boolean revertTo(String blockNumber) {
        // try to convert to long
        long block;

        try {
            block = Long.parseLong(blockNumber);
        } catch (NumberFormatException e) {
            System.out.println(
                    "The given argument «" + blockNumber + "» cannot be converted to a number.");
            return false;
        }

        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        Map<LogEnum, LogLevel> cfgLog = new HashMap<>();
        cfgLog.put(LogEnum.GEN, LogLevel.INFO);
        AionLoggerFactory.initAll(cfgLog);
        final Logger log = AionLoggerFactory.getLogger(LogEnum.GEN.name());

        // get the current blockchain
        AionRepositoryImpl repository = AionRepositoryImpl.inst();
        boolean isSuccessful = repository.revertTo(block, log);
        repository.close();

        // ok if we managed to get down to the expected block
        return isSuccessful;
    }

    private void createKeystoreDirIfMissing() {
        if (!keystoreDir.isDirectory()) {
            if (!keystoreDir.mkdir()) {
                System.out.println(
                        "Ssl keystore directory could not be created. "
                                + "Please check user permissions or create directory manually.");
                System.exit(SystemExitCodes.INITIALIZATION_ERROR);
            }
            System.out.println();
        }
    }

    /** For security reasons we only want the ssl option to run in a console environment. */
    private void checkConsoleExists(Console console) {
        if (console == null) {
            System.out.println(
                    "No console found. This command can only be run interactively in a console environment.");
            System.exit(SystemExitCodes.INITIALIZATION_ERROR);
        }
    }

    private String getCertName(Console console) {
        console.printf("Enter certificate name:\n");
        String certName = console.readLine();
        if ((certName == null) || (certName.isEmpty())) {
            System.out.println("Error: no certificate name entered.");
            System.exit(SystemExitCodes.INITIALIZATION_ERROR);
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
            System.exit(SystemExitCodes.INITIALIZATION_ERROR);
        } else if (certPass.length() < minPassLen) {
            System.out.println(
                    "Error: certificate password must be at least "
                            + minPassLen
                            + " characters long.");
            System.exit(SystemExitCodes.INITIALIZATION_ERROR);
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

    public static CommandLine.Model.CommandSpec findCommandSpec(CommandLine.ParseResult result, Class<?> clazz){
        CommandLine.ParseResult sub = result;
        while (sub.hasSubcommand()){
            sub = sub.subcommand();
            if (sub.commandSpec().userObject().getClass().equals(clazz)) return sub.commandSpec();
        }
        return null;
    }
}
