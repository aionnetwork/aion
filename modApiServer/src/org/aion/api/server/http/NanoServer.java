package org.aion.api.server.http;

import fi.iki.elonen.NanoHTTPD;
import java.io.File;
import org.aion.api.server.nanohttpd.NanoHttpd;
import org.aion.api.server.nanohttpd.BoundRunner;
import org.aion.api.server.rpc.RpcThreadFactory;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.config.CfgApiRpcSsl;
import org.slf4j.Logger;

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
    private String sslPass;

    public NanoServer(String hostname, int port, boolean corsEnabled, List<String> enabledEndpoints,
        int tpoolSize, boolean sslEnabled, String sslCert, String sslPass) {

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
                if (!(new File(CfgApiRpcSsl.SSL_KEYSTORE_DIR)).isDirectory()) {
                    LOG.error("<rpc-server - no sslKeystore directory found>");
                    System.exit(1);
                }
                System.setProperty("javax.net.ssl.trustStore", new File(this.sslCert).getAbsolutePath());
                server.makeSecure(NanoHTTPD.makeSSLSocketFactory(
                    "/" + this.sslCert, this.sslPass.toCharArray()), null);
            }

            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (Throwable t) {
            LOG.error("<rpc-server - failed bind on {}:{}>", hostname, port);
            if (t.getMessage().contains("password")) {
                LOG.error("<rpc-server - incorrect password provided for ssl certificate>");
            } else if (t.getMessage().contains("Unable to load")) {
                LOG.error("<rpc-server - unable to find the ssl certificate named " +
                    this.sslCert + " in the sslKeystore directory>");
            }
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
