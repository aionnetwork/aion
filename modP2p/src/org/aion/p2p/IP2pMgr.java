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
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p;

import java.util.List;
import java.util.Map;


/**
 *
 * @author chris
 *
 */
public interface IP2pMgr {

    /**
     * @return Map
     */
    Map<Integer, INode> getActiveNodes();
    
    INodeMgr getNodeMgr();

    /**
     * @param _hs
     *            List<Handler>
     */
    void register(final List<Handler> _hs);

    /**
     * @return INode
     */
    INode getRandom();

    /**
     * @param _id
     *            int
     * @param _msg
     *            Msg
     */
    void send(int _id, final Msg _msg);

    /**
     * Used to hook up with kernel to shutdown threads in network module
     */
    void shutdown();

    /**
     * start all p2p process
     */
    void run();

    List<Short> versions();

    int chainId();
}
