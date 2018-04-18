package org.aion.api.server.nanohttpd;

import fi.iki.elonen.NanoHTTPD;
import org.aion.api.server.rpc.RpcProcessor;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NanoHttpd extends NanoHTTPD {
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    private RpcProcessor rpcProcessor;
    private boolean corsEnabled;

    public NanoHttpd(
            String hostname,
            int port,
            boolean corsEnabled,
            List<String> enabledEndpoints) throws IOException {
        super(hostname, port);
        this.rpcProcessor = new RpcProcessor(enabledEndpoints);
        this.corsEnabled = corsEnabled;
    }

    protected Response addCORSHeaders(Response resp) {
        resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.addHeader("Access-Control-Allow-Credentials", "true");
        resp.addHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        return resp;
    }

    private Response respond(IHTTPSession session) {
        String requestBody = null;

        Map<String, String> body = new HashMap<String, String>(); // body need to grab key postData
        try {
            session.parseBody(body);
        } catch (Exception e) {
            LOG.debug("<rpc-server - no request body found>", e);
        }

        requestBody = body.getOrDefault("postData", null);

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
            r = addCORSHeaders(r);
        }
        return r;
    }

    @Override
    public void stop() {
        super.stop();
        rpcProcessor.shutdown();
    }

}
