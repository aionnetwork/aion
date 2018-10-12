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

package org.aion.api.server.http.undertow;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.RequestDumpingHandler;
import io.undertow.server.handlers.RequestLimitingHandler;
import io.undertow.server.handlers.StuckThreadDetectionHandler;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

/**
 * Created this handler to "collect" all handlers in the chain in one place. This is the classical
 * approach to server design (filter request through a bunch of objects that can choose to either
 * pass the request object to the next handler or respond to the request itself)
 *
 * @implNote a possible optimization here would be to use the RequestBufferingHandler
 *
 * According to Stuart Douglas (http://lists.jboss.org/pipermail/undertow-dev/2018-July/002224.html):
 *
 * "The advantage [of RequestBufferingHandler] is that if you are going to dispatch to a worker
 * thread then the dispatch does not happen until the request has been read, thus reducing the
 * amount of time a worker spends processing the request. Essentially this allows you to take
 * advantage of non-blocking IO even for applications that use blocking IO, but at the expense of
 * memory for buffering."
 */
public class AionUndertowRootHandler implements HttpHandler {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    // the root handler stores the first reference in the chain of handler references
    // (therefore we don't have to hold all the downstream references)
    private final HttpHandler rootHandler;

    public AionUndertowRootHandler(AionUndertowRpcHandler rpcHandler,
        RequestLimitingConfiguration requestLimiting,
        StuckThreadDetectorConfiguration stuckThreadDetector) {
        /**
         * Opinion: StuckThreadDetectionHandler should be enabled by default, since in the grand-scheme of things, it's
         * performance overhead is not too great and it could potentially help us catch implementation bugs in the API.
         *
         * See Impl: github.com/undertow-io/undertow/blob/master/core/src/main/java/io/undertow/server/handlers/StuckThreadDetectionHandler.java
         */
        HttpHandler thirdHandler;
        if (stuckThreadDetector.isEnabled()) {
            thirdHandler = new StuckThreadDetectionHandler(stuckThreadDetector.getTimeoutSeconds(),
                rpcHandler);
        } else {
            thirdHandler = rpcHandler;
        }

        // Only enable request dumping in TRACE mode
        HttpHandler secondHandler;
        if (LOG.isTraceEnabled()) {
            secondHandler = new RequestDumpingHandler(thirdHandler);
        } else {
            secondHandler = thirdHandler;
        }

        HttpHandler firstHandler;
        if (requestLimiting.isEnabled()) {
            /**
             * @implNote rationale for doing this: request limiting handler is really a last resort for someone
             * trying to protect their kernel from being dos-ed by limiting compute resources the RPC server can consume.
             * The maximumConcurrentRequests in this case, are effectively the number of worker threads available.
             */
            firstHandler = new RequestLimitingHandler(requestLimiting.getMaxConcurrentConnections(),
                requestLimiting.getQueueSize(), secondHandler);
        } else {
            firstHandler = secondHandler;
        }

        // first thing we need to do is dispatch this http request to a worker thread (off the io thread)
        rootHandler = new BlockingHandler(firstHandler);
    }

    @Override
    public void handleRequest(HttpServerExchange httpServerExchange) throws Exception {
        rootHandler.handleRequest(httpServerExchange);
    }
}
