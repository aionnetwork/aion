/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * The aion network project leverages useful source code from other
 * open source projects. We greatly appreciate the effort that was
 * invested in these projects and we thank the individual contributors
 * for their work. For provenance information and contributors
 * please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 * Aion foundation.
 *
 */


package org.aion.zero.impl.cli;

import org.aion.base.util.Hex;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.account.Keystore;
import org.aion.mcf.config.Cfg;
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
                    System.out.println("current: p2p://" + cfg.getId() + "@" + cfg.getNet().getP2p().getIp() + ":" + cfg.getNet().getP2p().getPort());
                    String[] nodes = cfg.getNet().getNodes();
                    if (nodes != null && nodes.length > 0) {
                        System.out.println("boot nodes list:");
                        for (String node : nodes) {
                            System.out.println("            " + node);
                        }
                    } else {
                        System.out.println("boot nodes list: 0");
                    }
                    System.out.println("p2p: " + cfg.getNet().getP2p().getIp() + ":" + cfg.getNet().getP2p().getPort());
                    break;
                case "-r":
                    if (args.length < 2) {
                        System.out.println("Starting database clean-up.");
                        RecoveryUtils.pruneAndCorrect();
                        System.out.println("Finished database clean-up.");
                    } else {
                        switch (revertTo(args[1])) {
                        case SUCCESS:
                            System.out.println("Blockchain successfully reverted to block number " + args[1] + ".");
                            break;
                        case FAILURE:
                            System.out.println("Unable to revert to block number " + args[1] + ".");
                            return 1;
                        case ILLEGAL_ARGUMENT:
                        default:
                            return 1;
                        }
                    }
                    break;
                case "--db-compact":
                    RecoveryUtils.dbCompact();
                    break;
                case "--dump-blocks":
                    long count = 100L;

                    if (args.length < 2) {
                        System.out.println("Printing top " + count + " blocks from database.");
                        RecoveryUtils.dumpBlocks(count);
                    } else {
                        try {
                            count = Long.parseLong(args[1]);
                        } catch (NumberFormatException e) {
                            System.out.println("The given argument <" + args[1] + "> cannot be converted to a number.");
                        }
                        System.out.println("Printing top " + count + " blocks from database.");
                        RecoveryUtils.dumpBlocks(count);
                    }
                    System.out.println("Finished printing blocks.");
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
        System.out.println("  -h                           show help info");
        System.out.println();
        System.out.println("  -a create                    create a new account");
        System.out.println("  -a list                      list all existing accounts");
        System.out.println("  -a export [address]          export private key of an account");
        System.out.println("  -a import [private_key]      import private key");
        System.out.println();
        System.out.println("  -c                           create config with default values");
        System.out.println();
        System.out.println("  -i                           show information");
        System.out.println();
        System.out.println("  -r                           remove blocks on side chains and correct block info");
        System.out.println("  -r [block_number]            revert db up to specific block number");
        System.out.println();
        System.out.println("  -v                           show version");
    }

    /**
     * Creates a new account.
     *
     * @return boolean
     */
    private boolean createAccount() {
        String password = readPassword("Please enter a password: ");
        String password2 = readPassword("Please re-enter your password: ");

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
     * @param address
     *            address of the account
     * @return boolean
     */
    private boolean exportPrivateKey(String address) {
        if (!Keystore.exist(address)) {
            System.out.println("The account does not exist!");
            return false;
        }

        String password = readPassword("Please enter your password: ");
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
     * @param privateKey
     *            private key in hex string
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

        String password = readPassword("Please enter a password: ");
        String password2 = readPassword("Please re-enter your password: ");
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
     * Reads a password from the console.
     *
     * @param prompt String
     * @return boolean
     */
    public String readPassword(String prompt) {
        Console console = System.console();
        return new String(console.readPassword(prompt));
    }

    private RecoveryUtils.Status revertTo(String blockNumber) {
        // try to convert to long
        long block;

        try {
            block = Long.parseLong(blockNumber);
        } catch (NumberFormatException e) {
            System.out.println("The given argument <" + blockNumber + "> cannot be converted to a number.");
            return RecoveryUtils.Status.ILLEGAL_ARGUMENT;
        }

        return RecoveryUtils.revertTo(block);
    }

}
