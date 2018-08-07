package org.aion.api.server.http;

import org.aion.api.server.rpc.RpcProcessor;
import org.aion.zero.impl.config.CfgAion;

import java.io.File;
import java.util.*;

public abstract class RpcServer {

    protected String hostName;
    protected int port;

    protected boolean corsEnabled;
    protected String corsOrigin;

    protected RpcProcessor rpcProcessor;

    protected boolean sslEnabled;
    protected String sslCertCanonicalPath;
    protected char[] sslCertPass;

    // want to explicitly force user of this class to check for null value here.
    private Integer workerPoolSize;

    protected RpcServer(RpcServerBuilder<?> builder) {
        // everything exposed by the builder is immutable, except for the List<String> & char[] sslCertPass
        // 1. List<String> enabledEndpoints - defensively copy
        // 2. char[] sslCertPass - we want to mutate it later ourselves, so store original reference

        hostName = Objects.requireNonNull(builder.hostName);
        port = Objects.requireNonNull(builder.port);

        corsEnabled = builder.corsEnabled;
        corsOrigin = builder.corsOrigin;

        List<String> enabledEndpoints = Collections.unmodifiableList(Objects.requireNonNull(builder.enabledEndpoints));
        rpcProcessor = new RpcProcessor(enabledEndpoints);

        sslEnabled = builder.sslEnabled;
        if (sslEnabled) {
            String certPath = Objects.requireNonNull(builder.sslCertPath);
            try {
                sslCertCanonicalPath = new File(certPath).getCanonicalPath();
            } catch (Exception e) {
                // rethrow checked exceptions from File
                throw new RuntimeException("Could not locate SSL file at path: "+certPath);
            }
            Objects.requireNonNull(sslCertCanonicalPath); // paranoid check

            //we want to mutate it later ourselves, so store original reference
            sslCertPass = Objects.requireNonNull(builder.sslCertPass);
        }
        // if worker pool size is null => select best size based on system
        workerPoolSize = builder.workerPoolSize;
    }

    // want to explicitly force user of this class to check for null value here.
    protected Optional<Integer> getWorkerPoolSize() { return Optional.ofNullable(workerPoolSize); }

    public abstract void start();
    public abstract void stop();
}
