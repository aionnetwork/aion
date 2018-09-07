package org.aion.api.server.http.undertow;

import io.undertow.Undertow;
import io.undertow.util.HttpString;
import org.aion.api.server.http.RpcServer;
import org.aion.api.server.http.RpcServerBuilder;
import org.aion.generic.IGenericAionChain;
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
    private static final int STUCK_THREAD_TIMEOUT_SECONDS = 600; // 10 min

    Undertow server;

    private final Map<HttpString, String> CORS_HEADERS = Map.of(
            HttpString.tryFromString("Access-Control-Allow-Origin"), corsOrigin,
            HttpString.tryFromString("Access-Control-Allow-Headers"), "origin,accept,content-type",
            HttpString.tryFromString("Access-Control-Allow-Credentials"), "true",
            HttpString.tryFromString("Access-Control-Allow-Methods"), "POST,OPTIONS",
            HttpString.tryFromString("Access-Control-Max-Age"), "86400"
    );

    public static class Builder extends RpcServerBuilder<UndertowRpcServer.Builder> {
        @Override
        public UndertowRpcServer build() {
            if (aionChain == null)
                throw new IllegalStateException("Aion chain instance not set; valid instance is required to build api");
            return new UndertowRpcServer(aionChain, this);
        }

        @Override
        protected UndertowRpcServer.Builder self() { return this; }
    }

    private UndertowRpcServer(IGenericAionChain aionChain, Builder builder) {
        super(aionChain, builder);
        // writes to System.error. Rationale: the alternative is to write to the slf4j facade and then manually
        // hook up all the possible loggers defined (now and in future) by this library
        // through our logback logger; this risks missing potentially important debug information from the library
        System.setProperty("org.jboss.logging.provider", "slf4j");
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

            int effectiveIoThreadCount;

            if (getIoPoolSize().isPresent()) {
                LOG.info("<rpc-server - setting io thread count manually not recommended. recommended io thread pool size: {}>",
                        Math.max(Runtime.getRuntime().availableProcessors(), 2));
                undertowBuilder.setIoThreads(getIoPoolSize().get());

                effectiveIoThreadCount = getIoPoolSize().get();
            } else {
                /** this number comes from {@link io.undertow.Undertow.Builder#Builder()} */
                effectiveIoThreadCount = Math.max(Runtime.getRuntime().availableProcessors(), 2);
            }

            /** used to "remember" Undertow worker-thread count, since no getter exposed in {@link Undertow}. */
            int effectiveWorkerThreadCount;

            if (getWorkerPoolSize().isPresent()) {
                LOG.info("<rpc-server - setting worker thread count manually not recommended. recommended worker thread pool size: {}>",
                        Math.max(Runtime.getRuntime().availableProcessors(), 2) * 8);
                undertowBuilder.setWorkerThreads(getWorkerPoolSize().get());

                effectiveWorkerThreadCount = getWorkerPoolSize().get();
            } else {
                /** this number comes from {@link io.undertow.Undertow.Builder#Builder()} */
                effectiveWorkerThreadCount = Math.max(Runtime.getRuntime().availableProcessors(), 2) * 8;
            }

            StuckThreadDetectorConfiguration stuckThreadDetector =
                    new StuckThreadDetectorConfiguration(false, STUCK_THREAD_TIMEOUT_SECONDS);
            if (stuckThreadDetectorEnabled) {
                stuckThreadDetector =
                        new StuckThreadDetectorConfiguration(true, STUCK_THREAD_TIMEOUT_SECONDS);
            }

            RequestLimitingConfiguration requestLimiting =
                    new RequestLimitingConfiguration(false, -1, 1);
            boolean isQueueBounded = getRequestQueueSize().isPresent() && getRequestQueueSize().get() > 0;
            if (isQueueBounded) {
                requestLimiting = new RequestLimitingConfiguration(true, effectiveWorkerThreadCount,
                        getRequestQueueSize().get());
            }

            AionUndertowRpcHandler rpcHandler = new AionUndertowRpcHandler(corsEnabled, CORS_HEADERS, rpcProcessor);

            undertowBuilder.setHandler(new AionUndertowRootHandler(rpcHandler, requestLimiting, stuckThreadDetector));


            server = undertowBuilder.build();
            server.start();

            LOG.info("<rpc-server - (UNDERTOW) started on {}://{}:{}>", sslEnabled ? "https" : "http", hostName, port);

            LOG.debug("----------------------------------------");
            LOG.debug("UNDERTOW RPC Server Started with Options");
            LOG.debug("----------------------------------------");
            LOG.debug("SSL: {}", sslEnabled ? "Enabled; Certificate = "+sslCertCanonicalPath : "Not Enabled");
            LOG.debug("CORS: {}", corsEnabled ? "Enabled; Allowed Origins = \""+corsOrigin+"\"" : "Not Enabled");
            LOG.debug("Worker Thread Count: {}", effectiveWorkerThreadCount);
            LOG.debug("I/O Thread Count: {}", effectiveIoThreadCount);
            LOG.debug("Request Queue Size: {}", isQueueBounded ? getRequestQueueSize().get() : "Unbounded");
            LOG.debug("----------------------------------------");

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
