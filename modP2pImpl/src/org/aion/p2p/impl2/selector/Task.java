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

package org.aion.p2p.impl2.selector;

import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * Analogous to Netty's NioTask, we will be using this module to execute application specific or
 * arbitrary logic when notified by the EventLoop.
 *
 * <p>Tasks will be executed by the thread running {@link MainIOLoop}
 */
public interface Task {

    public static final Task DO_NOTHING =
            new Task() {
                @Override
                public void channelReady(SelectableChannel channel, SelectionKey key) {}

                @Override
                public void acceptMessage(
                        SelectableChannel channel, SelectionKey key, ByteBuffer buffer) {}

                @Override
                public void channelUnregistered(SelectableChannel channel, Throwable cause) {}
            };

    void channelReady(SelectableChannel channel, SelectionKey key);

    // sometimes, writes become pending in which case they must be accepted
    // by the channel, when OP_WRITE gets triggered, the channel is responsible for
    // writing the message to the buffer
    void acceptMessage(SelectableChannel channel, SelectionKey key, ByteBuffer buffer);

    void channelUnregistered(SelectableChannel channel, Throwable cause);
}
