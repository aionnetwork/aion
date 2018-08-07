package org.aion.api.server.http.nano;

import fi.iki.elonen.NanoHTTPD;
import org.aion.api.server.rpc.RpcProcessor;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AionHttpd extends NanoHTTPD {
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    private RpcProcessor rpcProcessor;
    private boolean corsEnabled;
    private Map<String, String> corsHeaders;

    public AionHttpd(String hostname, int port, RpcProcessor rpcProcessor, boolean corsEnabled, Map<String, String> corsHeaders) {
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
            for(Map.Entry<String, String> header: corsHeaders.entrySet()){
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
