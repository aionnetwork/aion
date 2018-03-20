package org.aion.p2p.impl;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/**
 * Commands are used by the two worker threads to submit commands onto
 * the the main selector thread
 *
 *
 * This is necessary because although items like {@link SelectionKey#cancel()}
 * and {@link java.nio.channels.SocketChannel#register(Selector, int)} may cause
 * the selector to block (during iteration), therefore items related to the
 * state of the selector keySet must be done on the selector thread
 *
 * @see <a href="https://stackoverflow.com/questions/11523471/java-selectionkey-interestopsint-not-thread-safe">Selector Thread Safety</a>
 * @see
 */
public class Command {

    public static final byte REGISTER_CHANNEL = 0x1;
    public static final byte UNREGISTER_CHANNEL = 0x2;

    public final byte command;

    public Command(byte command) {
        this.command = command;
    }
}
