package org.aion.zero.impl.sync;

/**
 * Used for tracking different types of requests made to peers.
 *
 * @author Alexandra Roatis
 */
public class RequestCounter {

    private long status = 0;
    private long headers = 0;
    private long bodies = 0;
    private long total = 0;

    public RequestCounter() {}

    public long getStatus() {
        return status;
    }

    public long getHeaders() {
        return headers;
    }

    public long getBodies() {
        return bodies;
    }

    public long getTotal() {
        return total;
    }

    public void incStatus() {
        this.status++;
        this.total++;
    }

    public void incHeaders() {
        this.headers++;
        this.total++;
    }

    public void incBodies() {
        this.bodies++;
        this.total++;
    }
}
