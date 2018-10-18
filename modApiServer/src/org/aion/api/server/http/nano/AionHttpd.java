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

package org.aion.api.server.http.nano;

import fi.iki.elonen.NanoHTTPD;
import java.util.HashMap;
import java.util.Map;
import org.aion.api.server.rpc.RpcProcessor;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

public class AionHttpd extends NanoHTTPD {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    private RpcProcessor rpcProcessor;
    private boolean corsEnabled;
    private Map<String, String> corsHeaders;

    public AionHttpd(String hostname, int port, RpcProcessor rpcProcessor, boolean corsEnabled,
        Map<String, String> corsHeaders) {
        super(hostname, port);

        this.rpcProcessor = rpcProcessor;
        this.corsEnabled = corsEnabled;
        this.corsHeaders = corsHeaders;
    }

    private Response respond(IHTTPSession session) {
        Map<String, String> body = new HashMap<>(); // body need to grab key postData
        try {
            session.parseBody(body);
        } catch (Exception e) {
            LOG.debug("<rpc-server - no request body found>", e);
        }

        String requestBody = body.getOrDefault("postData", null);

        return NanoHTTPD.newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            rpcProcessor.process(requestBody));
    }

    @Override
    public Response serve(IHTTPSession session) {
        // First let's handle CORS OPTION query
        Response r;
        if (corsEnabled && Method.OPTIONS.equals(session.getMethod())) {
            r = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, null, 0);
        } else {
            r = respond(session);
        }

        if (corsEnabled) {
            for (Map.Entry<String, String> header : corsHeaders.entrySet()) {
                r.addHeader(header.getKey(), header.getValue());
            }
        }

        return r;
    }

    @Override
    public void stop() {
        super.stop();
        rpcProcessor.shutdown();
    }

}
