package org.aion.zero.impl.sync;

public class PeerState {

    public enum Mode {
        /**
         * The peer is in main-chain; use normal syncing strategy.
         */
        NORMAL,

        /**
         * The peer is in side-chain; sync backward to find the fork point.
         */
        BACKWARD,

        /**
         * The peer is in side-chain; sync forward to catch up.
         */
        FORWARD
    }

    // TODO: enforce rules on this
    public enum State {
        /**
         * The initial state.
         */
        INITIAL,

        /**
         * Status request, waiting for response.
         */
        STATUS_REQUESTED,

        /**
         * Block headers request, waiting for response.
         */
        HEADERS_REQUESTED,

        /**
         * Block bodies request, waiting for response.
         */
        BODIES_REQUESTED,
    }

    // The syncing mode and the base block number
    private Mode mode;
    private long base;

    // The syncing status
    private State state;
    private long lastHeaderRequest;

    /**
     * Creates a new peer state.
     *
     * @param mode
     * @param base
     */
    public PeerState(Mode mode, long base) {
        this.mode = mode;
        this.base = base;

        this.state = State.INITIAL;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public long getBase() {
        return base;
    }

    public void setBase(long base) {
        this.base = base;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public long getLastHeaderRequest() {
        return lastHeaderRequest;
    }

    public void setLastHeaderRequest(long lastStatusRequest) {
        this.lastHeaderRequest = lastStatusRequest;
    }
}
