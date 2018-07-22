package org.aion.api.server.http.undertow;

import io.undertow.Undertow;
import io.undertow.io.Receiver;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.RequestBufferingHandler;
import io.undertow.server.handlers.RequestDumpingHandler;
import io.undertow.server.handlers.StuckThreadDetectionHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
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

    private final int STUCK_THREAD_TIMEOUT_SECONDS = 600; // 10 min
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
        // writes to System.error. Rationale: the alternative is to write to the slf4j facade and then manually
        // hook up all the possible loggers defined (now and in future) by this library
        // through our logback logger; this risks missing potentially important debug information from the library
        System.setProperty("org.jboss.logging.provider", "slf4j");
    }

    private void addCorsHeaders(HttpServerExchange exchange) {
        if (corsEnabled) {
            for (Map.Entry<HttpString, String> header: CORS_HEADERS.entrySet()) {
                exchange.getResponseHeaders().put(header.getKey(), header.getValue());
            }
        }
    }

    private void handleRequest(HttpServerExchange ex0) throws Exception {
        Receiver.FullStringCallback rpcHandler = (ex3, body) -> {
            // only support post requests
            if (!Methods.POST.equals(ex3.getRequestMethod())) {
                ex3.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
                ex3.endExchange();
                return;
            }

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
        /**
         * Opinion: StuckThreadDetectionHandler should be enabled by default, since in the grand-scheme of things, it's
         * performance overhead is not too great and it could potentially help us catch implementation bugs in the API.
         * Alternative: Allow the user to enable this from the config?
         * See Impl:
         * github.com/undertow-io/undertow/blob/master/core/src/main/java/io/undertow/server/handlers/StuckThreadDetectionHandler.java
         */
        StuckThreadDetectionHandler stuckThreadDetectionHandler = new StuckThreadDetectionHandler(STUCK_THREAD_TIMEOUT_SECONDS, corsPreflightHandler);

        // Only enabled if API is in TRACE mode
        RequestDumpingHandler requestDumpingHandler = new RequestDumpingHandler(stuckThreadDetectionHandler);

        HttpHandler firstHandler;
        if (LOG.isTraceEnabled()) firstHandler = requestDumpingHandler;
        else firstHandler = stuckThreadDetectionHandler;

        BlockingHandler blockingHandler = new BlockingHandler(firstHandler);

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

            LOG.info("<rpc-server - (UNDERTOW) started on {}:{}>", hostName, port);
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
