package org.aion.zero.impl.cli;

import java.io.PrintStream;
import org.aion.zero.impl.cli.Cli.ReturnType;
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

    @ArgGroup()
    private Composite group = new Composite();

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
            }else {
                group.checkOptions();
            }
        }
    }

    public ReturnType runCommand(Cli cli) {
        try {
            checkOptions();
            if (help) {

            } else {

            }
            return ReturnType.EXIT;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            printUsage(System.out, this);
            return ReturnType.ERROR;
        }
    }

    public void setHelp(Boolean help) {
        this.help = help;
    }

    public void setList(Boolean list) {
        this.list = list;
    }

    public void setGroup(Composite group) {
        this.group = group;
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

        public void setExport(String export) {
            this.export = export;
        }

        public void setImportAcc(String importAcc) {
            this.importAcc = importAcc;
        }

        public void setCreate(Boolean create) {
            this.create = create;
        }
    }
}
