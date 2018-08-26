package org.aion.api.server.http.undertow;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import org.aion.api.server.rpc.RpcProcessor;

import java.util.Map;

class AionUndertowRpcHandler implements HttpHandler {
    private final boolean corsEnabled;
    private final Map<HttpString, String> corsHeaders;
    private final RpcProcessor rpcProcessor;

    public AionUndertowRpcHandler(boolean corsEnabled, Map<HttpString, String> corsHeaders, RpcProcessor rpcProcessor) {
        this.corsEnabled = corsEnabled;
        this.corsHeaders = corsHeaders;
        this.rpcProcessor = rpcProcessor;
    }

    private void addCorsHeaders(HttpServerExchange exchange) {
        for (Map.Entry<HttpString, String> header: corsHeaders.entrySet()) {
            exchange.getResponseHeaders().put(header.getKey(), header.getValue());
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        boolean isPost = Methods.POST.equals(exchange.getRequestMethod());
        boolean isOptions = Methods.OPTIONS.equals(exchange.getRequestMethod());

        // only support POST & OPTIONS requests
        if (!isPost && !isOptions) {
            exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
            exchange.setPersistent(false); // don't need to keep-alive connection in case of error.
            exchange.endExchange();
            return;
        }

        // respond to cors-preflight request
        if (corsEnabled && isOptions) {
            addCorsHeaders(exchange);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "0");
            exchange.getResponseSender().send("");
            return;
        }

        /** respond to rpc call; {@link io.Undertow.BlockingReceiverImpl#receiveFullString} */
        exchange.getRequestReceiver().receiveFullString((_exchange, body) -> {
            if (corsEnabled) addCorsHeaders(_exchange);
            _exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            _exchange.getResponseSender().send(rpcProcessor.process(body));
        });
    }
}
