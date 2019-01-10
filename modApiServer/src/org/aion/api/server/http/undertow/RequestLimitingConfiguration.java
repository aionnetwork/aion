package org.aion.api.server.http.undertow;

public class RequestLimitingConfiguration {
    private final boolean enabled;
    private final int maxConcurrentConnections;
    private final int queueSize;

    public RequestLimitingConfiguration(
            boolean enabled, int maxConcurrentConnections, int queueSize) {
        this.enabled = enabled;
        this.maxConcurrentConnections = maxConcurrentConnections;
        this.queueSize = queueSize;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxConcurrentConnections() {
        return maxConcurrentConnections;
    }

    public int getQueueSize() {
        return queueSize;
    }
}
