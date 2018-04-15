package org.aion.api.server.http;

import fi.iki.elonen.NanoHTTPD;
import org.aion.api.server.nanohttpd.NanoHttpd;
import org.aion.api.server.nanohttpd.BoundRunner;
import org.aion.api.server.rpc.RpcProcessor;
import org.aion.api.server.rpc.RpcThreadFactory;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import java.io.IOException;
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

    public NanoServer(String hostname,
                      int port,
                      boolean corsEnabled,
                      List<String> enabledEndpoints,
                      int tpoolSize) {

        this.corsEnabled = corsEnabled;
        this.enabledEndpoints = enabledEndpoints;

        this.hostname = hostname;
        this.port = port;

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
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (Throwable t) {
            LOG.error("<rpc-server - failed bind on {}:{}>", hostname, port);
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
