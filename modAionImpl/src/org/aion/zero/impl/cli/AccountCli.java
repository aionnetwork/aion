package org.aion.zero.impl.cli;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.account.Keystore;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.SystemExitCodes;
import org.aion.zero.impl.cli.Cli.ReturnType;
import org.apache.commons.lang3.ArrayUtils;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "account",
        aliases = {"a", "--account","-a"},
        description = "This command provides simple utilities to manage the kernel Keystore."
                        + " Use -h to view available options.",
        abbreviateSynopsis = true)
public class AccountCli {

    private final String BASE_PATH = System.getProperty("user.dir");
    @ArgGroup() private Composite group = new Composite();
    @Option(
            names = {"--help", "-h"},
            arity = "0",
            description = "Print the help for the account subcommand.")
    private Boolean help = false;
    @Option(
            names = {"list", "l"},
            arity = "0",
            description = "Lists all accounts within the kernel's keystore")
    private Boolean list = false;

    /*
    Prints the usage message for this command
     */
    public static void printUsage(PrintStream out, AccountCli instance) {
        CommandLine.usage(instance, out);
    }

    public void checkOptions() {
        if (!list && !help) {
            if (group == null) {
                throw new IllegalArgumentException("account accepts at least one argument");
            } else {
                group.checkOptions();
            }
        }
    }

    public ReturnType runCommand(PasswordReader passwordReader) {
        try {
            checkOptions();
            if (help) {
                printUsage(System.out, this);
            } else {
                if (group != null) {
                    // 1. Handle all keystore changes
                    if (group.getCreate() && !createAccount(passwordReader)) {
                        return ReturnType.ERROR;
                    } else if (!group.getExport().isEmpty()
                            && !exportPrivateKey(group.getExport(), passwordReader)) {
                        return ReturnType.ERROR;
                    } else if (!group.getImportAcc().isEmpty()
                            && !importPrivateKey(group.getImportAcc(), passwordReader)) {
                        return ReturnType.ERROR;
                    }
                }
                // 2. Read from the keystore
                if (list != null && list && !listAccounts()) {
                    return ReturnType.ERROR;
                }
            }
            return ReturnType.EXIT;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            printUsage(System.out, this);
            return ReturnType.ERROR;
        }
    }

    public Boolean getHelp() {
        return help;
    }

    public void setHelp(Boolean help) {
        this.help = help;
    }

    public Boolean getList() {
        return list;
    }

    public void setList(Boolean list) {
        this.list = list;
    }

    public Composite getGroup() {
        return group;
    }

    public void setGroup(Composite group) {
        this.group = group;
    }

    /** List all existing accounts. */
    @SuppressWarnings("SameReturnValue")
    boolean listAccounts() {
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
    boolean exportPrivateKey(String address, PasswordReader passwordReader) {
        System.out.println(
            "Searching for account in: " + getRelativePath(Keystore.getKeystorePath()));

        if (!Keystore.exist(address)) {
            System.out.println("The account does not exist!");
            return false;
        }

        String password;
        try (InputStreamReader isr = new InputStreamReader(System.in);
            BufferedReader reader = new BufferedReader(isr)) {
            password = passwordReader.readPassword("Please enter your password: ", reader);
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
     * Creates a new account.
     *
     * @return {@code true} only if the new account was successfully created, {@code false}
     *     otherwise.
     */
    boolean createAccount(PasswordReader passwordReader) {
        String password, password2;
        try (InputStreamReader isr = new InputStreamReader(System.in);
            BufferedReader reader = new BufferedReader(isr)) {
            password = passwordReader.readPassword("Please enter a password: ", reader);
            password2 = passwordReader.readPassword("Please re-enter your password: ", reader);
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


    /**
     * Imports a private key.
     *
     * @param privateKey private key in hex string
     * @return {@code true} if the operation was successful, {@code false} otherwise.
     */
    boolean importPrivateKey(String privateKey, PasswordReader passwordReader) {
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
            password = passwordReader.readPassword("Please enter a password: ", reader);
            password2 = passwordReader.readPassword("Please re-enter your password: ", reader);
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
     * Utility method for truncating absolute paths wrt the {@link #BASE_PATH}.
     *
     * @return the path without the {@link #BASE_PATH} prefix.
     */
    private String getRelativePath(String path) {
        // if absolute paths are given with different prefix the replacement won't work
        return path.replaceFirst(BASE_PATH, ".");
    }

    public static class Composite {
        @Option(
                names = {"export", "e"},
                arity = "1",
                paramLabel = "<key>",
                defaultValue = "",
                description = "Export the private key of an account")
        private String export = "";

        @Option(
                names = {"i", "import"},
                paramLabel = "<key>",
                arity = "1",
                defaultValue = "",
                description = "Import private key")
        private String importAcc = "";

        @Option(
                names = {"create", "c"},
                arity = "0",
                description = "Creates a new account and prints the public key to the terminal")
        private Boolean create = false;

        private void checkOptions() {
            if (export.isEmpty() && importAcc.isEmpty() && !create) {
                throw new IllegalArgumentException("account accepts at least one argument");
            }
        }

        public String getExport() {
            return export;
        }

        public void setExport(String export) {
            this.export = export;
        }

        public String getImportAcc() {
            return importAcc;
        }

        public void setImportAcc(String importAcc) {
            this.importAcc = importAcc;
        }

        public Boolean getCreate() {
            return create;
        }

        public void setCreate(Boolean create) {
            this.create = create;
        }
    }
}
