package org.aion.api.server.http.undertow;

public class StuckThreadDetectorConfiguration {
    private final boolean enabled;
    private final int timeoutSeconds;

    public StuckThreadDetectorConfiguration(boolean enabled, int timeoutSeconds) {
        this.enabled = enabled;
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isEnabled() { return enabled; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
}
