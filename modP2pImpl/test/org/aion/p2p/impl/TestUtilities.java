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

package org.aion.p2p.impl;

import java.io.IOException;
import java.net.ServerSocket;

public class TestUtilities {
    private TestUtilities() {}

    /**
     * Tries to return a free port thats currently not used
     *
     * @return
     */
    public static int getFreePort() {
        try {
            ServerSocket socket = new ServerSocket(0);
            socket.setReuseAddress(true);
            int port = socket.getLocalPort();
            socket.close();

            return port;
        } catch (IOException e) {
            throw new IllegalStateException("could not find free TCP/IP port");
        }
    }

    static String formatAddr(String id, String ip, int port) {
        return id + "@" + ip + ":" + port;
    }
}
