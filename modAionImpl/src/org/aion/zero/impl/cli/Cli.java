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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import org.aion.base.util.Hex;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.account.Keystore;
import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.CfgSsl;
import org.aion.zero.impl.Version;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.db.RecoveryUtils;
import picocli.CommandLine;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Command line interface.
 *
 * @author chris
 */
public class Cli {

    private final String BASE_PATH = System.getProperty("user.dir");

    private String BASE_PATH_WITH_NETWORK = BASE_PATH  + "/config/" + CfgAion.getNetwork();

    private String dstConfig = BASE_PATH_WITH_NETWORK + "/config.xml";

    private String dstGenesis = BASE_PATH_WITH_NETWORK + "/genesis.json";

    File keystoreDir = new File(System.getProperty("user.dir") + File.separator + CfgSsl.SSL_KEYSTORE_DIR);

    private Arguments options = new Arguments();
    private CommandLine parser = new CommandLine(options);

    enum Network {
        MAINNET, CONQUEST;

        @Override
        public String toString() {
            switch(this) {
                case MAINNET: return "mainnet";
                case CONQUEST: return "conquest";
                default: throw new IllegalArgumentException();
            }
        }
    }

    private Network net = Network.MAINNET;

    public int call(final String[] args, Cfg cfg) {
        return call(args, cfg, BASE_PATH);
    }

    public int call(final String[] args, Cfg cfg, String path) {
        try {
            // the preprocess method handles arguments that are separated by space
            parser.parse(Arguments.preprocess(args));
        } catch (Exception e) {
            System.out.println("Unable to parse the input arguments due to: ");
            if (e.getMessage() != null) {
                System.out.println(e.getMessage());
            } else {
                e.printStackTrace();
            }

            System.out.println();
            printHelp();
        }

        try {
            cfg.fromXML();

            switch (args[0].toLowerCase()) {
                case "-h":
                    printHelp();
                    break;
                case "-a":

                    int index = 0;
                    boolean multi = false;

                    if (args.length < 2) {
                        printHelp();
                        return 1;
                    } else {
                        while (index < args.length) {
                            if(args[index].equals("-d")||args[index].equals("-n")||args[index].equals("--datadir")||args[index].equals("--network")) {
                                multi = true;
                                break;
                            }
                            index++;
                        }
                    }

                    // Switches datadir && network
                    if(multi) {
                        String[] newArgs = Arrays.copyOfRange(args, index, args.length);
                        call(newArgs, cfg);
                    }


                    switch (args[1]) {
                        case "create":
                            if (!createAccount()) {
                                return 1;
                            }
                            break;
                        case "list":
                            if (!listAccounts()) {
                                return 1;
                            }
                            break;
                        case "export":
                            if (args.length < 3 || !exportPrivateKey(args[2])) {
                                return 1;
                            }
                            break;
                        case "import":
                            if (args.length < 3 || !importPrivateKey(args[2])) {
                                return 1;
                            }
                            break;
                        default:
                            printHelp();
                            return 1;
                    }
                    break;
                case "-c":
                    if (args.length == 2 && isValid(args[1])) {

                        net = determineNetwork(args[1].toLowerCase());

                        switch (net) {
                            case MAINNET:
                            case CONQUEST:
                                CfgAion.setNetwork(net.toString());
                                File dir = new File(BASE_PATH + "/config/" + net);
                                if(!dir.exists()) {
                                    dir.mkdirs();
                                }
                                CfgAion.setConfFilePath(BASE_PATH + "/config/" + args[1] + "/config.xml");
                                System.out.println("\nNew config generated for " + args[1]);
                                break;
                            default:
                                System.out.println("\nInvalid network selected!");
                                System.out.println("--- Available Networks ---");
                                System.out.println("    mainnet, conquest");
                                System.out.println("--------------------------");
                                return 1;
                        }
                    } else if (args.length == 1) {
                        System.out.println("\nInvalid network selected!");
                        System.out.println("--- Available Networks ---");
                        System.out.println("    mainnet, conquest");
                        System.out.println("--------------------------");
                        return 1;
                    }
                    cfg.fromXML();
                    cfg.setId(UUID.randomUUID().toString());
                    cfg.toXML(null);
                    break;
                case "-i":
                    cfg.fromXML();
                    System.out.println("\nInformation");
                    System.out.println("--------------------------------------------");
                    System.out.println(
                        "current: p2p://" + cfg.getId() + "@" + cfg.getNet().getP2p().getIp() + ":"
                            + cfg.getNet().getP2p().getPort());
                    String[] nodes = cfg.getNet().getNodes();
                    if (nodes != null && nodes.length > 0) {
                        System.out.println("boot nodes list:");
                        for (String node : nodes) {
                            System.out.println("            " + node);
                        }
                    } else {
                        System.out.println("boot nodes list: 0");
                    }
                    System.out.println(
                        "p2p: " + cfg.getNet().getP2p().getIp() + ":" + cfg.getNet().getP2p()
                            .getPort());
                    break;
                case "-s":
                    if ((args.length == 2 || args.length == 4) && (args[1].equals("create"))) {
                        createKeystoreDirIfMissing();
                        Console console = System.console();
                        checkConsoleExists(console);

                        List<String> scriptArgs = new ArrayList<>();
                        scriptArgs.add("/bin/bash");
                        scriptArgs.add("script/generateSslCert.sh");
                        scriptArgs.add(getCertName(console));
                        scriptArgs.add(getCertPass(console));
                        // add the hostname and ip optionally passed in as cli args
                        scriptArgs.addAll(Arrays.asList(Arrays.copyOfRange(args, 2, args.length)));
                        new ProcessBuilder(scriptArgs).inheritIO().start().waitFor();
                    } else {
                        System.out.println("Incorrect usage of -s create command.\n" +
                            "Command must enter both hostname AND ip or else neither one.");
                        return 1;
                    }
                    break;
                case "-r":
                    if (args.length < 2) {
                        System.out.println("Starting database clean-up.");
                        RecoveryUtils.pruneAndCorrect();
                        System.out.println("Finished database clean-up.");
                    } else {
                        switch (revertTo(args[1])) {
                            case SUCCESS:
                                System.out.println(
                                    "Blockchain successfully reverted to block number " + args[1]
                                        + ".");
                                break;
                            case FAILURE:
                                System.out
                                    .println("Unable to revert to block number " + args[1] + ".");
                                return 1;
                            case ILLEGAL_ARGUMENT:
                            default:
                                return 1;
                        }
                    }
                    break;

                case "-n":
                case "--network":
                    if ( (args.length == 2 || args.length == 4) && isValid(args[1])) {

                        net = determineNetwork(args[1].toLowerCase());

                        switch (net) {
                            case MAINNET:
                            case CONQUEST:

                                // -n [network]
                                if (args.length == 2) {

                                    CfgAion.setNetwork(net.toString());
                                    BASE_PATH_WITH_NETWORK = BASE_PATH  + "/config/" + CfgAion.getNetwork();
                                    CfgAion.setConfFilePath(BASE_PATH_WITH_NETWORK + "/config.xml");
                                    CfgAion.setGenesisFilePath((BASE_PATH_WITH_NETWORK + "/genesis.json"));

                                    copyNetwork(path, net);
                                    cfg.getLog().setLogPath(net.toString() + "/log");
                                    cfg.getDb().setDatabasePath(net.toString() + "/database");
                                    Keystore.setKeystorePath(path + "/" + net.toString() + "/keystore");
                                    return 2;

                                }

                                // -n [network] -d [directory]
                                else if ((args[2].equals("-d")||args[2].equals("--datadir")) && isValid(args[3])) {

                                    CfgAion.setNetwork(net.toString());
                                    BASE_PATH_WITH_NETWORK = BASE_PATH  + "/config/" + CfgAion.getNetwork();
                                    CfgAion.setConfFilePath(BASE_PATH_WITH_NETWORK + "/config.xml");
                                    CfgAion.setGenesisFilePath((BASE_PATH_WITH_NETWORK + "/genesis.json"));

                                    String[] newArgs = Arrays.copyOfRange(args, 2, args.length);
                                    call(newArgs, cfg);
                                    return 2;

                                } else if (!(args[2].equals("-d")||args[2].equals("--datadir"))) {
                                    System.out.println("\nInvalid multi arguments!\n");
                                    printHelp();
                                    return 1;

                                } else {
                                    System.out.println("\nInvalid datadir selected!");
                                    System.out.println("Please choose valid directory name!\n");
                                    return 1;
                                }

                            default:
                                System.out.println("\nInvalid network selected!\n");
                                System.out.println("--- Available Networks ---");
                                System.out.println("    mainnet, conquest");
                                System.out.println("--------------------------\n");
                                return 1;
                        }

                    } else {
                        System.out.println("\nInvalid network selected!");
                        System.out.println("--- Available Networks ---");
                        System.out.println("    mainnet , conquest");
                        System.out.println("--------------------------\n");
                        return 1;
                    }

                // Determines database folder path
                case "-d":
                case "--datadir":
                    if ( (args.length == 2 || args.length == 4) && isValid(args[1]))  {

                        // -d [directory]
                        if (args.length == 2) {

                            copyNetwork(path + "/" + args[1], net);
                            cfg.getLog().setLogPath(args[1] + "/" + net + "/log");
                            cfg.getDb().setDatabasePath(args[1] + "/" + net + "/database");
                            Keystore.setKeystorePath(path + "/" + args[1] + "/" + net + "/keystore");
                            return 2;

                        }

                        // -d [directory] -n [network]
                        else if (isValid(args[3])) {

                            String[] newArgs = Arrays.copyOfRange(args, 2, args.length);
                            call(newArgs, cfg);

                            copyNetwork(path + "/" + args[1], net);
                            cfg.getLog().setLogPath(args[1] + "/" + net + "/log");
                            cfg.getDb().setDatabasePath(args[1] + "/" + net + "/database");
                            Keystore.setKeystorePath(path + "/" + args[1] + "/" + net + "/keystore");
                            return 2;

                        } else if (!(args[2].equals("-n")||args[2].equals("--network"))) {
                            System.out.println("\nInvalid multi arguments!\n");
                            printHelp();
                            return 1;

                        } else {
                            System.out.println("\nInvalid network selected!");
                            System.out.println("--- Available Networks ---");
                            System.out.println("    mainnet , conquest");
                            System.out.println("--------------------------\n");
                            return 1;
                        }

                    } else {
                        System.out.println("\nInvalid datadir selected!");
                        System.out.println("Please choose valid directory name!\n");
                        return 1;
                    }

                case "--state": {
                    String pruning_type = "full";
                    if (args.length >= 2) {
                        pruning_type = args[1];
                    }
                    try {
                        RecoveryUtils.pruneOrRecoverState(pruning_type);
                    } catch (Throwable t) {
                        System.out.println("Reorganizing the state storage FAILED due to:");
                        t.printStackTrace();
                        return 1;
                    }
                    break;
                }
                case "--dump-state-size":
                    long block_count = 2L;

                    if (args.length < 2) {
                        System.out
                            .println("Retrieving state size for top " + block_count + " blocks.");
                        RecoveryUtils.printStateTrieSize(block_count);
                    } else {
                        try {
                            block_count = Long.parseLong(args[1]);
                        } catch (NumberFormatException e) {
                            System.out.println("The given argument <" + args[1]
                                + "> cannot be converted to a number.");
                        }
                        if (block_count < 1) {
                            System.out
                                .println("The given argument <" + args[1] + "> is not valid.");
                            block_count = 2L;
                        }

                        System.out
                            .println("Retrieving state size for top " + block_count + " blocks.");
                        RecoveryUtils.printStateTrieSize(block_count);
                    }
                    break;
                case "--dump-state":
                    long level = -1L;

                    if (args.length < 2) {
                        System.out.println("Retrieving state for top main chain block...");
                        RecoveryUtils.printStateTrieDump(level);
                    } else {
                        try {
                            level = Long.parseLong(args[1]);
                        } catch (NumberFormatException e) {
                            System.out.println("The given argument <" + args[1]
                                + "> cannot be converted to a number.");
                        }
                        if (level == -1L) {
                            System.out.println("Retrieving state for top main chain block...");
                        } else {
                            System.out.println(
                                "Retrieving state for main chain block at level " + level + "...");
                        }
                        RecoveryUtils.printStateTrieDump(level);
                    }
                    break;
                case "--db-compact":
                    RecoveryUtils.dbCompact();
                    break;
                case "--dump-blocks":
                    long count = 10L;

                    if (args.length < 2) {
                        System.out.println("Printing top " + count + " blocks from database.");
                        RecoveryUtils.dumpBlocks(count);
                    } else {
                        try {
                            count = Long.parseLong(args[1]);
                        } catch (NumberFormatException e) {
                            System.out.println("The given argument <" + args[1]
                                + "> cannot be converted to a number.");
                        }
                        if (count < 1) {
                            System.out
                                .println("The given argument <" + args[1] + "> is not valid.");
                            count = 10L;
                        }

                        System.out.println("Printing top " + count + " blocks from database.");
                        RecoveryUtils.dumpBlocks(count);
                    }
                    break;
                case "-v":
                    System.out.println("\nVersion");
                    System.out.println("--------------------------------------------");
                    // Don't put break here!!
                case "--version":
                    System.out.println(Version.KERNEL_VERSION);
                    break;
                default:
                    System.out.println("Unable to parse the input arguments");
                    printHelp();
                    return 1;
            }

            System.out.println("");
        } catch (Throwable e) {
            System.out.println("");
            return 1;
        }

        return 0;
    }

    /**
     * Print the CLI help info.
     */
    private void printHelp() {
        String usage = parser.getUsageMessage();

        usage = usage.replaceFirst("OPTIONS]", "OPTIONS] [ARGUMENTS]");

        // the command line output has some styling characters in addition to the actual string
        // making the use of a regular expression necessary here
        usage = usage.replaceFirst(" \\[[^ ]*<hostname> <ip>.*]", "]");

        System.out.println(usage.toString());
    }

    /**
     * Determines the correct network (mainnet / conquest) enum based on argument
     *
     * @param arg CLI input of -n [network]
     * @return Network
     */
    private Network determineNetwork(String arg) {
        Network net;
        switch(arg) {
            case "mainnet":
                net = Network.MAINNET;
                break;
            case "testnet":
                net = Network.CONQUEST;
                break;
            case "conquest":
                net = Network.CONQUEST;
                break;
            default:
                net = null;
        }
        return net;
    }

    /**
     * Copies the config files (config && genesis) from root to [datadir]/[network]
     *
     * @param path input to append base directory to copy to
     * @param net input to determine network to copy from
     */
    private void copyNetwork(String path,  Network net) {

        File dir1 = new File(path + "/" + net + "/config");
        File dir2 = new File(path + "/" + net + "/keystore");
        dir1.mkdirs();
        dir2.mkdirs();

        File src1 = new File(BASE_PATH + "/config/" + net + "/config.xml");
        File src2 = new File(BASE_PATH + "/config/" + net + "/genesis.json");
        File dst1 = new File(path + "/" + net + "/config/config.xml");
        File dst2 = new File(path + "/" + net + "/config/genesis.json");

        copyRecursively(src1, dst1);
        copyRecursively(src2, dst2);
        dstConfig = dst1.toString();
        dstGenesis = dst2.toString();
    }

    /**
     * Creates a new account.
     *
     * @return true only if the new account was successfully created, otherwise false.
     */
    private boolean createAccount() {
        String password = null, password2 = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
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
            return true;
        } else {
            System.out.println("Failed to create an account!");
            return false;
        }
    }

    /**
     * List all existing account.
     *
     * @return boolean
     */
    private boolean listAccounts() {
        String[] accounts = Keystore.list();
        for (String account : accounts) {
            System.out.println(account);
        }

        return true;
    }

    /**
     * Dumps the private of the given account.
     *
     * @param address address of the account
     * @return boolean
     */
    private boolean exportPrivateKey(String address) {
        if (!Keystore.exist(address)) {
            System.out.println("The account does not exist!");
            return false;
        }

        String password = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
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
     * @return boolean
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
            System.out.println("Unable to recover private key."
                + "Are you sure you did not import a public key?");
            return false;
        }

        String password = null, password2 = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
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
            System.out.println("The private key was imported, the address is: " + address);
            return true;
        } else {
            System.out.println("Failed to import the private key. Already exists?");
            return false;
        }
    }

    /**
     * Returns a password after prompting the user to enter it. This method attempts first to read
     * user input from a console evironment and if one is not available it instead attempts to read
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
                "The given argument <" + blockNumber + "> cannot be converted to a number.");
            return RecoveryUtils.Status.ILLEGAL_ARGUMENT;
        }

        return RecoveryUtils.revertTo(block);
    }

    /**
     * Checks for illegal inputs (for datadir && network names)
     * @param value
     * @return
     */
    public static boolean isValid(String value) {
        return !value.isEmpty() && !value.matches(".*[-=+,.?;:'!@#$%^&*].*");
    }

    private void createKeystoreDirIfMissing() {
        if (!keystoreDir.isDirectory()) {
            if (!keystoreDir.mkdir()) {
                System.out.println("Ssl keystore directory could not be created. " +
                    "Please check user permissions or create directory manually.");
                System.exit(1);
            }
            System.out.println();
        }
    }

    /**
     * For security reasons we only want the ssl option to run in a console environment.
     */
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
        String certPass = String.valueOf(console.readPassword(
                "Enter certificate password (at least " + minPassLen + " characters):\n"));
        if ((certPass == null) || (certPass.isEmpty())) {
            System.out.println("Error: no certificate password entered.");
            System.exit(1);
        } else if (certPass.length() < minPassLen) {
            System.out.println(
                "Error: certificate password must be at least " + minPassLen + " characters long.");
            System.exit(1);
        }
        return certPass;
    }

    public String getDstConfig() {
        return dstConfig;
    }

    public String getDstGenesis() {
        return dstGenesis;
    }

    // Methods below taken from FileUtils class
    private static boolean copyRecursively(File src, File target)
    {
        if (src.isDirectory()) {
            return copyDirectoryContents(src, target);
        }
        else {
            try {
                Files.copy(src, target);
                return true;
            }
            catch (IOException e) {
                return false;
            }
        }
    }

    private static boolean copyDirectoryContents(File src, File target)
    {
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

    private static boolean isSymbolicLink(File file)
    {
        try {
            File canonicalFile = file.getCanonicalFile();
            File absoluteFile = file.getAbsoluteFile();
            File parentFile = file.getParentFile();
            // a symbolic link has a different name between the canonical and absolute path
            return !canonicalFile.getName().equals(absoluteFile.getName()) ||
                    // or the canonical parent path is not the same as the file's parent path,
                    // provided the file has a parent path
                    parentFile != null && !parentFile.getCanonicalPath().equals(canonicalFile.getParent());
        }
        catch (IOException e) {
            // error on the side of caution
            return true;
        }
    }

    private static ImmutableList<File> listFiles(File dir)
    {
        File[] files = dir.listFiles();
        if (files == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(files);
    }

}
