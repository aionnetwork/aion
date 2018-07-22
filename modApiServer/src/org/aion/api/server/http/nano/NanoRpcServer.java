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
import org.aion.api.server.http.RpcServer;
import org.aion.api.server.http.RpcServerBuilder;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class NanoRpcServer extends RpcServer {
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    private AionHttpd server;
    private ExecutorService workers;
    private static final int REQ_QUEUE_CAPACITY = 200;

    private final Map<String, String> CORS_HEADERS = Map.of(
            "Access-Control-Allow-Origin", corsOrigin,
            "Access-Control-Allow-Headers", "origin,accept,content-type",
            "Access-Control-Allow-Credentials", "true",
            "Access-Control-Allow-Methods", "POST",
            "Access-Control-Max-Age", "86400"
    );

    public static class Builder extends RpcServerBuilder<Builder> {
        @Override
        public NanoRpcServer build() {
            return new NanoRpcServer(this);
        }

        @Override
        protected Builder self() { return this; }
    }

    private NanoRpcServer(Builder builder) {
        super(builder);
    }

    public void makeSecure() throws Exception {
        if (server == null)
            throw new IllegalStateException("Server not instantiated; valid instance required to enable ssl.");

        try {
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(new FileInputStream(sslCertCanonicalPath), sslCertPass);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, sslCertPass);

            server.makeSecure(NanoHTTPD.makeSSLSocketFactory(keystore, keyManagerFactory), null);

            // if the keystore object got loaded, go ahead and clear out the password
            for (char c : sslCertPass)
                c = '\0'; // NUL

        } catch (Exception e) {
            LOG.error("<rpc-server - unable to use keystore; path invalid or password incorrect");
            throw e;
        }
    }

    @Override
    public void start() {
        try {
            // default to 1 thread to minimize resource consumption by nano http
            int tCount = 1;
            if (getWorkerPoolSize().isPresent())
                tCount = getWorkerPoolSize().get();

            // create fixed thread pool of size defined by user
            workers = new ThreadPoolExecutor(tCount, tCount, 1, TimeUnit.MINUTES,
                    new ArrayBlockingQueue<>(REQ_QUEUE_CAPACITY), new AionHttpdThreadFactory());

            server = new AionHttpd(hostName, port, rpcProcessor, corsEnabled, CORS_HEADERS);
            server.setAsyncRunner(new BoundRunner(workers));

            if (this.sslEnabled)
                makeSecure();

            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);

            LOG.info("<rpc-server - (NANO) started on {}:{}>", hostName, port);
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
}
