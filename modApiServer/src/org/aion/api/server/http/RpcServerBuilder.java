package org.aion.api.server.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This builder is opinionated; 1. It assumes that false is a reasonable default for sslEnabled and
 * corsEnabled. 2. It assumes empty array for enabledEndpoints is a reasonable default 3. It assumes
 * "*" is a reasonable default for corsOrigin 4. Any "unset" objects get initialized to null
 */
public abstract class RpcServerBuilder<T extends RpcServerBuilder<T>> {

    // package private for now.
    // can consider making these private and enforce access through accessors,
    // but I personally like to avoid the visual clutter of accessors
    String hostName;
    Integer port;

    boolean corsEnabled = false;
    String corsOrigin = "*";

    List<String> enabledEndpoints = new ArrayList<>();
    List<String> enabledMethods = new ArrayList<>();
    List<String> disabledMethods = new ArrayList<>();

    boolean sslEnabled = false;
    String sslCertPath;
    char[] sslCertPass;

    Integer workerPoolSize = null;
    Integer ioPoolSize = null;
    Integer requestQueueSize = null;
    boolean stuckThreadDetectorEnabled = false;

    public T setUrl(String hostName, int port) {
        this.hostName = Objects.requireNonNull(hostName);

        if (port < 1) throw new RuntimeException("Port number must be greater than 0.");
        if (port < 1024) System.out.println("Ports < 1024 are privileged and require sudo.");
        if (port > 65535) System.out.println("Ports > 65535 are not supported by OS.");
        this.port = port; // autoboxing

        return self();
    }

    public T enableCors() {
        this.corsEnabled = true; // autoboxing
        return self();
    }

    public T enableCorsWithOrigin(String origin) {
        this.corsEnabled = true;
        this.corsOrigin = Objects.requireNonNull(origin);
        return self();
    }

    public T enableEndpoints(List<String> enabledEndpoints) {
        // empty List is a valid input here.
        this.enabledEndpoints = Objects.requireNonNull(enabledEndpoints);
        return self();
    }

    public T enableMethods(List<String> enabledMethods) {
        // Empty List or null are valid input here.
        this.enabledMethods = Objects.requireNonNullElse(enabledMethods, new ArrayList<>());
        return self();
    }

    public T disableMethods(List<String> disabledMethods) {
        // Empty List or null are valid input here.
        this.disabledMethods = Objects.requireNonNullElse(disabledMethods, new ArrayList<>());
        return self();
    }

    public T enableSsl(String sslCertName, char[] sslCertPass) {
        this.sslEnabled = true;
        this.sslCertPath = Objects.requireNonNull(sslCertName);
        this.sslCertPass = Objects.requireNonNull(sslCertPass);

        return self();
    }

    public T setWorkerPoolSize(Integer workerPoolSize) {
        this.workerPoolSize = workerPoolSize;
        return self();
    }

    public T setIoPoolSize(Integer x) {
        this.ioPoolSize = x;
        return self();
    }

    public T setRequestQueueSize(Integer x) {
        this.requestQueueSize = x;
        return self();
    }

    public T setStuckThreadDetectorEnabled(boolean x) {
        this.stuckThreadDetectorEnabled = x;
        return self();
    }

    protected abstract RpcServer build();

    // Subclasses must override this method to return "this"
    protected abstract T self();
}
