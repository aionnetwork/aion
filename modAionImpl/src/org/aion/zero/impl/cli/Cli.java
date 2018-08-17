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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aion.base.util.Hex;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.account.Keystore;
import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.CfgSsl;
import org.aion.zero.impl.Version;
import org.aion.zero.impl.db.RecoveryUtils;

import java.io.Console;
import java.util.UUID;

/**
 * Command line interface.
 *
 * @author chris
 */
public class Cli {

    File keystoreDir = new File(
        System.getProperty("user.dir") + File.separator + CfgSsl.SSL_KEYSTORE_DIR);

    public int call(final String[] args, final Cfg cfg) {
        try {
            cfg.fromXML();
            switch (args[0].toLowerCase()) {
                case "-h":
                    printHelp();
                    break;
                case "-a":
                    if (args.length < 2) {
                        printHelp();
                        return 1;
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
                    cfg.fromXML();
                    cfg.setId(UUID.randomUUID().toString());
                    cfg.toXML(null);
                    System.out.println("\nNew config generated");
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
        System.out.println("Usage: ./aion.sh [options] [arguments]");
        System.out.println();
        System.out.println("  -h                                            show help info");
        System.out.println();
        System.out.println("  -a create                                     create a new account");
        System.out
            .println("  -a list                                       list all existing accounts");
        System.out.println(
            "  -a export [address]                           export private key of an account");
        System.out.println("  -a import [private_key]                       import private key");
        System.out.println();
        System.out.println(
            "  -c                                            create config with default values");
        System.out.println();
        System.out.println("  -i                                            show information");
        System.out.println();
        System.out.println(
            "  -s create                                     create an ssl certificate for localhost");
        System.out.println(
            "  -s create [[hostname] [ip]]                   create an ssl certificate for a custom hostname and ip");
        System.out.println();
        System.out.println(
            "  -r                                            remove blocks on side chains and correct block info");
        System.out.println(
            "  -r [block_number]                             revert db up to specific block number");
        System.out.println();
        System.out.println("  -v                                            show version");
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

}
