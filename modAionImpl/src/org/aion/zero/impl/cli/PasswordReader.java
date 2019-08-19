package org.aion.zero.impl.cli;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import org.aion.zero.impl.SystemExitCodes;

public class PasswordReader {

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
            System.exit(SystemExitCodes.INITIALIZATION_ERROR);
        }
        return null; // Make compiler happy; never get here.
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

    private static final PasswordReader instance = new PasswordReader();

    public static PasswordReader inst() {
        return instance;
    }
}
