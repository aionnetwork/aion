/*******************************************************************************
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
 *     
 ******************************************************************************/

package org.aion.api.server.zmq;

import org.aion.log.LogEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

import java.util.concurrent.atomic.AtomicBoolean;

public class Proxy {
    protected static final Logger LOG = LoggerFactory.getLogger(LogEnum.API.toString());
    protected static AtomicBoolean shutDown = new AtomicBoolean(false);

    public static boolean proxy(Socket frontend, Socket backend, Socket callback, Socket event) {
        PollItem[] items = new PollItem[4];
        items[0] = new PollItem(frontend, Poller.POLLIN);
        items[1] = new PollItem(backend, Poller.POLLIN);
        items[2] = new PollItem(callback, Poller.POLLIN);
        items[3] = new PollItem(event, Poller.POLLIN);

        try {
            while (!shutDown.get()) {
                // Wait while there are either requests or replies to process.
                int rc = ZMQ.poll(items, 3000);
                if (rc < 0) {
                    continue;
                }

                // Process a request.
                if (items[0].isReadable()) {
                    while (true) {
                        if (!msgProcess(frontend, backend)) {
                            return false;
                        }
                        break;
                    }
                }
                // Process a reply.
                if (items[1].isReadable()) {
                    while (true) {
                        if (!msgProcess(backend, frontend)) {
                            return false;
                        }
                        break;
                    }
                }

                // Process a callback
                if (items[2].isReadable()) {
                    while (true) {
                        if (!msgProcess(callback, frontend)) {
                            return false;
                        }
                        break;
                    }
                }

                if (items[3].isReadable()) {
                    while (true) {
                        if (!msgProcess(event, frontend)) {
                            return false;
                        }
                        break;
                    }
                }
            }

            LOG.info("zmq-proxy thread was interrupted.");
        } catch (Exception e) {
            LOG.error("aion.api.server.zmq.Proxy exception" + e.getMessage());
        }

        return true;
    }

    private static boolean msgProcess(Socket receiver, Socket sender) {
        byte[] msg = receiver.recv(0);
        if (msg == null) {
            return false;
        }

        byte[] msgMore = null;
        if (receiver.hasReceiveMore()) {
            msgMore = receiver.recv(0);

            if (msgMore == null) {
                return false;
            }
        }

        if (!sender.send(msg, msgMore == null ? ZMQ.DONTWAIT : ZMQ.SNDMORE)) {
            return false;
        }

        if (msgMore != null) {

            if (!sender.send(msgMore, ZMQ.DONTWAIT)) {
                return false;
            }
        }

        return true;
    }

    public void shutdown() throws InterruptedException {
        LOG.info("zmq-proxy thread shuting down...");
        shutDown.set(true);

        LOG.info("waiting zmq-proxy thread shutdown");
        Thread.sleep(3000);
    }
}
