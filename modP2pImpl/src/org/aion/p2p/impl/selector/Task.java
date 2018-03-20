package org.aion.p2p.impl.selector;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * Analogous to Netty's NioTask, we will be using this module to
 * execute application specific or arbitrary logic when notified
 * by the EventLoop.
 *
 * Tasks will be executed by the thread running {@link MainIOLoop}
 */
public interface Task {

    public static final Task DO_NOTHING = new Task() {
        @Override
        public void channelReady(SelectableChannel channel, SelectionKey key) {

        }

        @Override
        public void channelUnregistered(SelectableChannel channel, Throwable cause) {

        }
    };

    void channelReady(SelectableChannel channel, SelectionKey key);

    void channelUnregistered(SelectableChannel channel, Throwable cause);
}
