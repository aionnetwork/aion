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
package org.aion.api.server.http;

import fi.iki.elonen.NanoHTTPD;
import java.io.File;
import org.aion.api.server.nanohttpd.NanoHttpd;
import org.aion.api.server.nanohttpd.BoundRunner;
import org.aion.api.server.rpc.RpcThreadFactory;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.CfgSsl;
import org.aion.zero.impl.config.CfgAion;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

public class NanoServer {
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    private NanoHttpd server;
    private String hostname;
    private int port;
    private boolean corsEnabled;
    private List<String> enabledEndpoints;
    private ExecutorService workers;
    private boolean sslEnabled;
    private String sslCert;
    private char[] sslPass;

    public NanoServer(String hostname, int port, boolean corsEnabled, List<String> enabledEndpoints,
        int tpoolSize, boolean sslEnabled, String sslCert, char[] sslPass) {

        this.corsEnabled = corsEnabled;
        this.enabledEndpoints = enabledEndpoints;

        this.hostname = hostname;
        this.port = port;

        this.sslEnabled = sslEnabled;
        this.sslCert = sslCert;
        this.sslPass = sslPass;

        // do not protect user from over-allocating resources to api.
        // int fixedPoolSize = Math.min(Runtime.getRuntime().availableProcessors()-1, tpoolSize);

        if (tpoolSize < 1) {
            tpoolSize = 1;
        }

        // create fixed thread pool of size defined by user
        this.workers = new ThreadPoolExecutor(
                tpoolSize,
                tpoolSize,
                1,
                TimeUnit.MINUTES,
                new ArrayBlockingQueue<>(200),
                new RpcThreadFactory());
    }

    public void start() {
        try {
            server = new NanoHttpd(hostname, port, corsEnabled, enabledEndpoints);
            server.setAsyncRunner(new BoundRunner(workers));

            if (this.sslEnabled) {
                File keystoreDir = new File(CfgAion.inst().getBasePath() + File.separator +  CfgSsl.SSL_KEYSTORE_DIR);
                if (!(keystoreDir).isDirectory()) {
                    LOG.error("<rpc-server - no "+keystoreDir.getPath()+" directory found (SSL Keystore)>");
                    System.exit(1);
                }
                /* Location of the Java keystore file containing the collection of CA certificates
                   trusted by this application process (trust store).

                   https://docs.ora`cle.com/javase/9/security/java-secure-socket-extension-jsse-reference-guide.htm#GUID-A41282C3-19A3-400A-A40F-86F4DA22ABA9__SYSTEMPROPERTIESANDCUSTOMIZEITEMSIN-DCEEB591
                 */
                // chrome://flags/#allow-insecure-localhost

                System.setProperty("javax.net.ssl.trustStore", keystoreDir.getAbsolutePath() + File.separator + this.sslCert);

                server.makeSecure(NanoHTTPD.makeSSLSocketFactory(File.separator + this.sslCert, this.sslPass), null);

                // if the keystore object got loaded, go ahead and clear out the password
                for (char c : this.sslPass) {
                    c = '\0'; // NUL
                }
            }

            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (Throwable t) {
            LOG.error("<rpc-server - failed bind on {}:{}>", hostname, port);
            // error messages out of nanohttpd seem to be pretty clean. transparently show them to user.
            LOG.error("<rpc-server - " + t.getMessage() + ">");
            System.exit(1);
        }

        LOG.info("<rpc-server - started on {}:{}>", hostname, port);
    }

    public void shutdown() {
        server.stop();

        // graceful(ish) shutdown of thread pool
        // NOTE: ok to call workers.*() from some shutdown thread since sun's implementation of ExecutorService is threadsafe
        workers.shutdownNow();
    }
}
