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
package org.aion.api.server.zmq;

import static org.aion.api.server.pb.ApiAion0.heartBeatMsg;

import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.log.LogEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

public class Proxy {
    protected static final Logger LOG = LoggerFactory.getLogger(LogEnum.API.toString());
    private static AtomicBoolean shutDown = new AtomicBoolean(false);

    static void proxy(Socket frontend, Socket backend, Socket callback, Socket event, Socket hb) {
        PollItem[] items = new PollItem[5];
        items[0] = new PollItem(frontend, Poller.POLLIN);
        items[1] = new PollItem(backend, Poller.POLLIN);
        items[2] = new PollItem(callback, Poller.POLLIN);
        items[3] = new PollItem(event, Poller.POLLIN);
        items[4] = new PollItem(hb, Poller.POLLIN);

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
                        if (msgProcessRecv(frontend, backend, hb)) {
                            return;
                        }
                        break;
                    }
                }
                // Process a reply.
                if (items[1].isReadable()) {
                    while (true) {
                        if (msgProcessSend(backend, frontend)) {
                            return;
                        }
                        break;
                    }
                }

                // Process a callback
                if (items[2].isReadable()) {
                    while (true) {
                        if (msgProcessSend(callback, frontend)) {
                            return;
                        }
                        break;
                    }
                }

                if (items[3].isReadable()) {
                    while (true) {
                        if (msgProcessSend(event, frontend)) {
                            return;
                        }
                        break;
                    }
                }

                // heartBeat reply
                if (items[4].isReadable()) {
                    while (true) {
                        if (msgProcessSend(hb, frontend)) {
                            return;
                        }
                        break;
                    }
                }
            }

            LOG.debug("zmq-proxy thread was interrupted.");
        } catch (Exception e) {
            LOG.error("aion.api.server.zmq.Proxy exception" + e.getMessage());
        }
    }

    private static boolean msgProcessRecv(Socket receiver, Socket sender, Socket hb) {
        byte[] msg = receiver.recv(0);
        if (msg == null) {
            return true;
        }

        byte[] msgMore = null;
        if (receiver.hasReceiveMore()) {
            msgMore = receiver.recv(0);

            if (msgMore == null) {
                return true;
            }
        }

        if (heartBeatMsg(msgMore)) {
            if (!hb.send(msg, ZMQ.SNDMORE)) {
                return true;
            }

            return !hb.send(msgMore, ZMQ.DONTWAIT);
        } else {
            if (!sender.send(msg, msgMore == null ? ZMQ.DONTWAIT : ZMQ.SNDMORE)) {
                return true;
            }

            if (msgMore != null) {
                return !sender.send(msgMore, ZMQ.DONTWAIT);
            }
        }

        return false;
    }

    private static boolean msgProcessSend(Socket receiver, Socket sender) {
        byte[] msg = receiver.recv(0);
        if (msg == null) {
            return true;
        }

        byte[] msgMore = null;
        if (receiver.hasReceiveMore()) {
            msgMore = receiver.recv(0);

            if (msgMore == null) {
                return true;
            }
        }

        if (!sender.send(msg, msgMore == null ? ZMQ.DONTWAIT : ZMQ.SNDMORE)) {
            return true;
        }

        if (msgMore != null) {
            return !sender.send(msgMore, ZMQ.DONTWAIT);
        }

        return false;
    }

    public static void shutdown() throws InterruptedException {
        LOG.debug("zmq-proxy thread shutting down...");
        shutDown.set(true);

        LOG.debug("waiting zmq-proxy thread shutdown");
        Thread.sleep(3000);
    }
}
