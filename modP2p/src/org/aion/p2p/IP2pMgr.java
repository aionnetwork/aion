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
package org.aion.p2p;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;

/** @author chris */
public interface IP2pMgr {
    // TODO: need refactor by passing the parameter in the later version to P2pMgr.
    int txBroadCastRoute = (Ctrl.SYNC << 8) + 6; // ((Ver.V0 << 16) + (Ctrl.SYNC << 8) + 6);

    /** @return Map */
    Map<Integer, INode> getActiveNodes();

    /** @param _hs List<Handler> */
    void register(final List<Handler> _hs);

    /** @return INode */
    INode getRandom();

    /**
     * @param _id int
     * @param _msg Msg
     */
    void send(int _id, String _displayId, final Msg _msg);

    /** Used to hook up with kernel to shutdown threads in network module. */
    void shutdown();

    /** Starts all p2p processes. */
    void run();

    List<Short> versions();

    int chainId();

    int getSelfIdHash();

    void closeSocket(final SocketChannel _sc, String _reason);

    boolean isShowLog();

    void errCheck(int nodeIdHashcode, String _displayId);

    void dropActive(int _nodeIdHash, String _reason);

    void configChannel(SocketChannel _channel) throws IOException;

    int getMaxActiveNodes();

    boolean isSyncSeedsOnly();

    int getMaxTempNodes();

    boolean validateNode(INode _node);

    int getSelfNetId();
}
