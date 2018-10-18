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

package org.aion.p2p.impl1.tasks;

/**
 * An incoming message.
 */
public class MsgIn {

    private final int nodeId;
    private final String displayId;
    private final int route;
    private final byte[] msg;

    /**
     * Constructs an incoming message.
     *
     * @param nodeId The node id.
     * @param displayId The display id.
     * @param route The route.
     * @param msg The message.
     */
    MsgIn(final int nodeId, final String displayId, final int route, final byte[] msg) {
        this.nodeId = nodeId;
        this.displayId = displayId;
        this.route = route;
        this.msg = msg;
    }

    public int getNodeId() {
        return this.nodeId;
    }

    String getDisplayId() {
        return this.displayId;
    }

    int getRoute() {
        return this.route;
    }

    public byte[] getMsg() {
        return this.msg;
    }
}
