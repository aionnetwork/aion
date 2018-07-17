package org.aion.api.server.http.undertow;

import io.undertow.Undertow;
import io.undertow.io.Receiver;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.*;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.aion.api.server.http.RpcServer;
import org.aion.api.server.http.RpcServerBuilder;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Map;

public class UndertowRpcServer extends RpcServer {
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    Undertow server;

    private final Map<HttpString, String> CORS_HEADERS = Map.of(
            HttpString.tryFromString("Access-Control-Allow-Origin"), corsOrigin,
            HttpString.tryFromString("Access-Control-Allow-Headers"), "origin,accept,content-type",
            HttpString.tryFromString("Access-Control-Allow-Credentials"), "true",
            HttpString.tryFromString("Access-Control-Allow-Methods"), "POST",
            HttpString.tryFromString("Access-Control-Max-Age"), "86400"
    );

    public static class Builder extends RpcServerBuilder<UndertowRpcServer.Builder> {
        @Override
        public UndertowRpcServer build() {
            return new UndertowRpcServer(this);
        }

        @Override
        protected UndertowRpcServer.Builder self() { return this; }
    }

    private UndertowRpcServer(Builder builder) {
        super(builder);
    }

    private void addCorsHeaders(HttpServerExchange exchange) {
        if (corsEnabled) {
            for (Map.Entry<HttpString, String> header: CORS_HEADERS.entrySet()) {
                exchange.getResponseHeaders().put(header.getKey(), header.getValue());
            }
        }
    }

    public void handleRequest(HttpServerExchange ex0) throws Exception {
        LOG.debug("handleRequest hit");

        Receiver.FullStringCallback rpcHandler = (ex3, body) -> {
            ex3.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            addCorsHeaders(ex3);
            ex3.getResponseSender().send(rpcProcessor.process(body));
        };
        HttpHandler corsPreflightHandler = ex1 -> {
            if (corsEnabled && Methods.OPTIONS.equals(ex1.getRequestMethod())) {
                ex1.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                ex1.getResponseHeaders().put(Headers.CONTENT_LENGTH, "0");
                addCorsHeaders(ex1);
                ex1.getResponseSender().send("");
            } else {
                new RequestBufferingHandler(ex2 -> ex2.getRequestReceiver().receiveFullString(rpcHandler), 10)
                        .handleRequest(ex1);
            }
        };
        AllowedMethodsHandler allowedMethodsHandler = new AllowedMethodsHandler(corsPreflightHandler, Methods.POST);
        StuckThreadDetectionHandler stuckThreadDetectionHandler = new StuckThreadDetectionHandler(allowedMethodsHandler);
        BlockingHandler blockingHandler = new BlockingHandler(stuckThreadDetectionHandler);
        RequestDumpingHandler requestDumpingHandler = new RequestDumpingHandler(blockingHandler);

        // on trace, dump the whole request
        if (LOG.isTraceEnabled())
            requestDumpingHandler.handleRequest(ex0);
        else
            blockingHandler.handleRequest(ex0);
    }

    private SSLContext sslContext() throws Exception {
        try {
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(new FileInputStream(sslCertCanonicalPath), sslCertPass);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, sslCertPass);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keystore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

            // if the keystore object got loaded, go ahead and clear out the password
            for (char c : sslCertPass)
                c = '\0'; // NUL

            return sslContext;
        } catch (Exception e) {
            LOG.error("<rpc-server - unable to use keystore; path invalid or password incorrect");
            throw e;
        }
    }

    @Override
    public void start() {
        try {
            Undertow.Builder undertowBuilder = Undertow.builder();

            if (sslEnabled)
                undertowBuilder.addHttpsListener(port, hostName, sslContext());
            else
                undertowBuilder.addHttpListener(port, hostName);

            undertowBuilder.setHandler(this::handleRequest);

            if (getWorkerPoolSize().isPresent()) {
                LOG.info("<rpc-server - setting worker thread count manually not recommended. recommended worker thread-pool size: {}>",
                        Math.max(Runtime.getRuntime().availableProcessors(), 2) * 8);
                undertowBuilder.setWorkerThreads(getWorkerPoolSize().get());
            }
            server = undertowBuilder.build();
            server.start();

            LOG.info("<rpc-server - started on {}:{}>", hostName, port);
        } catch (Exception e) {
            LOG.error("<rpc-server - failed bind on {}:{}>", hostName, port);
            LOG.error("<rpc-server - " + e.getMessage() + ">");
            System.exit(1);
        }
    }

    @Override
    public void stop() {
        server.stop();
        rpcProcessor.shutdown();
    }
}
