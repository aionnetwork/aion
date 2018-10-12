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
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import org.aion.api.server.http.RpcServer;
import org.aion.api.server.http.RpcServerBuilder;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

public class NanoRpcServer extends RpcServer {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());
    private final Map<String, String> CORS_HEADERS = Map.of(
        "Access-Control-Allow-Origin", corsOrigin,
        "Access-Control-Allow-Headers", "origin,accept,content-type",
        "Access-Control-Allow-Credentials", "true",
        "Access-Control-Allow-Methods", "POST,OPTIONS",
        "Access-Control-Max-Age", "86400"
    );
    private AionHttpd server;
    private ExecutorService workers;

    private NanoRpcServer(Builder builder) {
        super(builder);
    }

    private void makeSecure() throws Exception {
        if (server == null) {
            throw new IllegalStateException(
                "Server not instantiated; valid instance required to enable ssl.");
        }

        try {
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(new FileInputStream(sslCertCanonicalPath), sslCertPass);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory
                .getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, sslCertPass);

            server.makeSecure(NanoHTTPD.makeSSLSocketFactory(keystore, keyManagerFactory), null);

            // if the keystore object got loaded, go ahead and clear out the password
            for (char c : sslCertPass) {
                c = '\0'; // NUL
            }

        } catch (Exception e) {
            LOG.error("<rpc-server - unable to use keystore; path invalid or password incorrect");
            throw e;
        }
    }

    @Override
    public void start() {
        try {
            /*
             * default to cpu_count * 8 threads. java http servers, particularly with the servlet-type processing model
             * (jetty, tomcat, etc.) generally default to 200-1000 count thread pools
             *
             * rationale: if the user want's to restrict the worker pool size, they can manually override it
             */
            int tCount;
            if (getWorkerPoolSize().isPresent()) {
                tCount = getWorkerPoolSize().get();
            } else {
                tCount = Math.max(Runtime.getRuntime().availableProcessors(), 2) * 8;
            }

            // For unbounded queues, LinkedBlockingQueue is ideal, due to it's linked-list based impl.
            workers = new ThreadPoolExecutor(tCount, tCount, 10, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), new AionHttpdThreadFactory());

            server = new AionHttpd(hostName, port, rpcProcessor, corsEnabled, CORS_HEADERS);
            server.setAsyncRunner(new BoundRunner(workers));

            if (this.sslEnabled) {
                makeSecure();
            }

            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);

            LOG.info("<rpc-server - (NANO) started on {}://{}:{}>", sslEnabled ? "https" : "http",
                hostName, port);

            LOG.debug("------------------------------------");
            LOG.debug("NANO RPC Server Started with Options");
            LOG.debug("------------------------------------");
            LOG.debug("SSL: {}",
                sslEnabled ? "Enabled; Certificate = " + sslCertCanonicalPath : "Not Enabled");
            LOG.debug("CORS: {}",
                corsEnabled ? "Enabled; Allowed Origins = \"" + corsOrigin + "\"" : "Not Enabled");
            LOG.debug("Worker Thread Count: {}", tCount);
            LOG.debug("I/O Thread Count: Not Applicable");
            LOG.debug("Request Queue Size: Unbounded");
            LOG.debug("------------------------------------");

        } catch (Exception e) {
            LOG.error("<rpc-server - failed bind on {}:{}>", hostName, port);
            LOG.error("<rpc-server - " + e.getMessage() + ">");
            System.exit(1);
        }
    }

    @Override
    public void stop() {
        server.stop();

        // graceful(ish) shutdown of thread pool
        // NOTE: ok to call workers.*() from some shutdown thread since sun's implementation of ExecutorService is threadsafe
        workers.shutdownNow();
    }

    public static class Builder extends RpcServerBuilder<Builder> {

        @Override
        public NanoRpcServer build() {
            return new NanoRpcServer(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
