/*******************************************************************************
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
 *     
 ******************************************************************************/

package org.aion.zero.impl.cli;

import org.aion.mcf.account.Keystore;
import org.aion.base.util.Hex;
import org.aion.mcf.config.Cfg;
import org.aion.zero.impl.AionHub;
import org.aion.zero.impl.db.RecoveryUtils;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;

import java.io.Console;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/**
 * Command line interface.
 *
 * @author chris
 */

public class Cli {

    private final static String ipv4Pattern = "^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$";

    public int call(final String[] args, final Cfg cfg) {
        try {
            switch (args[0].toLowerCase()) {
            case "-h":
            case "--help":
                printHelp();
                break;
            case "-a":
            case "--account":
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
            case "--config":
                /**
                 * for fast prototyping config setting for a new network
                 */
                if (args.length >= 2) {
                    if (args[1].startsWith("--ips=")) {
                        String[] subArgs = args[1].replace("--ips=", "").split(",");
                        ArrayList<String[]> nodeInfos = new ArrayList<String[]>();
                        for (int i = 0, m = subArgs.length; i < m; i++) {
                            if (subArgs[i].matches(ipv4Pattern)) {
                                String[] nodeInfo = new String[3];
                                nodeInfo[0] = UUID.randomUUID().toString();
                                nodeInfo[1] = subArgs[i];
                                nodeInfo[2] = "30303";
                                nodeInfos.add(nodeInfo);
                            }
                        }
                        System.out.println("Config Overriding Commands");
                        System.out.println("---------------------------------------------");
                        for (int i = 0, m = nodeInfos.size(); i < m; i++) {
                            String[] ni = nodeInfos.get(i);
                            System.out.println("");
                            String command = "-c --id=" + ni[0] + " --p2p=" + ni[1] + "," + ni[2] + " --nodes=";
                            for (int j = 0; j < m; j++) {
                                if (j != i) {
                                    command += "p2p://" + nodeInfos.get(j)[0] + "@" + nodeInfos.get(j)[1] + ":"
                                            + nodeInfos.get(j)[2] + ",";
                                }
                            }
                            command = command.substring(0, command.length() - 1);
                            System.out.println(command);
                        }
                    } else {
                        cfg.toXML(Arrays.copyOfRange(args, 1, args.length));
                    }
                } else {
                    cfg.toXML(null);
                    System.out.println("\nnew config generated");
                }
                break;
            case "-i":
            case "--info":
                cfg.fromXML();
                System.out.println("\nInformation");
                System.out.println("--------------------------------------------");
                System.out.println("current: p2p://" + cfg.getId() + "@" + cfg.getNet().getP2p().getIp() + ":"
                        + cfg.getNet().getP2p().getPort());
                String[] nodes = cfg.getNet().getNodes();
                if (nodes != null && nodes.length > 0) {
                    System.out.println("boot nodes list:");
                    for (int i = 0, m = nodes.length; i < m; i++) {
                        System.out.println("            " + nodes[i]);
                    }
                } else {
                    System.out.println("boot nodes list: 0");
                }
                System.out.println("p2p: " + cfg.getNet().getP2p().getIp() + ":" + cfg.getNet().getP2p().getPort());
                break;
            case "-r":
            case "--revert":
                if (args.length < 2) {
                    System.out.println("Please provide the block number you want to revert to as an argument.");
                    return 1;
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
            case "-v":
                System.out.println("\nVersion");
                System.out.println("--------------------------------------------");
                System.out.println(AionHub.VERSION);
                break;
            case "--version":
                System.out.println(AionHub.VERSION);
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
    protected void printHelp() {
        System.out.println("Usage: ./aion.sh [options] [arguments]\n");
        System.out.println();
        System.out.println("  -h");
        System.out.println("  --help                       show help info");
        System.out.println();
        System.out.println("  -a [arguments]");
        System.out.println("  --account [arguments]");
        System.out.println("  -a create                    create a new account");
        System.out.println("  -a list                      list all existing accounts");
        System.out.println("  -a export [address]          export private key of an account");
        System.out.println("  -a import [private_key]      import private key");
        System.out.println();
        System.out.println("  -c ");
        System.out.println("  --config                     create config with default values");
        System.out.println("  -c --id=uuid                 create config with customized node id");
        System.out.println("  -c --nodes=p2p0,p2p1,..      create config with customized boot nodes");
        System.out.println("  -c --p2p=ip:port             create config with customized p2p listening address");
        System.out.println();
        System.out.println("  -i ");
        System.out.println("  --info                       show information");
        System.out.println();
        System.out.println("  -r [block_number]");
        System.out.println(
                "  --revert [block_number]      removes from the database all blocks greater than the one given");
        System.out.println();
        System.out.println("  -v ");
        System.out.println("  --version                    show version");
    }

    /**
     * Creates a new account.
     *
     * @return
     */
    protected boolean createAccount() {
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
     * @return
     */
    protected boolean listAccounts() {
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
     * @return
     */
    protected boolean exportPrivateKey(String address) {
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
     * @return
     */
    protected boolean importPrivateKey(String privateKey) {
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
     * @param prompt
     * @return
     */
    public String readPassword(String prompt) {
        Console console = System.console();
        return new String(console.readPassword(prompt));
    }

    protected RecoveryUtils.Status revertTo(String blockNumber) {
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
