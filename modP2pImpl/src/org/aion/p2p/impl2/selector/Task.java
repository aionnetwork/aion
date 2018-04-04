	package org.aion.p2p.impl2.selector;

import java.nio.ByteBuffer;
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
        public void acceptMessage(SelectableChannel channel, SelectionKey key, ByteBuffer buffer) {

        }

        @Override
        public void channelUnregistered(SelectableChannel channel, Throwable cause) {

        }
    };

    void channelReady(SelectableChannel channel, SelectionKey key);

    // sometimes, writes become pending in which case they must be accepted
    // by the channel, when OP_WRITE gets triggered, the channel is responsible for
    // writing the message to the buffer
    void acceptMessage(SelectableChannel channel, SelectionKey key, ByteBuffer buffer);

    void channelUnregistered(SelectableChannel channel, Throwable cause);
}
