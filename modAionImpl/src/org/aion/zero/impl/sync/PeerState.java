package org.aion.zero.impl.sync;

public class PeerState {

    public enum Mode {
        /** The peer is in main-chain; use normal syncing strategy. */
        NORMAL,

        /** The peer is in side-chain; sync backward to find the fork point. */
        BACKWARD,

        /** The peer is in side-chain; sync forward to catch up. */
        FORWARD
    }

    // TODO: enforce rules on this
    public enum State {
        /** The initial state. */
        INITIAL,

        /** Status request, waiting for response. */
        STATUS_REQUESTED,

        /** Block headers request, waiting for response. */
        HEADERS_REQUESTED,

        /** Block bodies request, waiting for response. */
        BODIES_REQUESTED,
    }

    // The syncing mode and the base block number
    private Mode mode;
    private long base;

    // used in FORWARD mode to prevent endlessly importing EXISTing blocks
    // compute how many times to go forward without importing a new block
    private int repeated;
    private int maxRepeats;

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

    public void resetLastHeaderRequest() {
        this.lastHeaderRequest = 0;
    }

    public int getRepeated() {
        return repeated;
    }

    public void resetRepeated() {
        this.repeated = 0;
    }

    public void incRepeated() {
        this.repeated++;
    }

    /**
     * This number is set based on the BACKWARD step size and the size of each requested batch in
     * FORWARD mode. Passing the number of repeats allowed means that we have entered in the
     * previous BACKWARD step. If that step would have been viable, we never would have made another
     * step back, so it effectively ends the FORWARD pass.
     *
     * @return The number of times that a node in FORWARD mode can import only blocks that already
     *     EXIST.
     */
    public int getMaxRepeats() {
        return maxRepeats;
    }

    public void setMaxRepeats(int maxRepeats) {
        this.maxRepeats = maxRepeats;
    }
}
