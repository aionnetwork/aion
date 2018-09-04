package org.aion.zero.impl.sync;

import static org.aion.p2p.P2pConstant.STEP_COUNT;

public class PeerState {

    public enum Mode {
        /** The peer is in main-chain. Use normal syncing strategy. */
        NORMAL,

        /** The peer is in side-chain. Sync backward to find the fork point. */
        BACKWARD,

        /** The peer is in side-chain. Sync forward to catch up. */
        FORWARD,

        /**
         * The peer is far ahead of the local chain. Use lightning sync strategy of jumping forward
         * to request blocks out-of-order ahead of import time. Continue by filling the gap to the
         * next jump step.
         */
        LIGHTNING,

        /**
         * The peer was far ahead of the local chain and made a sync jump. Gradually return to
         * normal syncing strategy, allowing time for old lightning sync requests to come in.
         */
        THUNDER
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

    // The syncing status
    private State state;
    private long lastBestBlock = 0;
    private long lastHeaderRequest;

    /** Creates a new peer state. */
    public PeerState(Mode mode, long base) {
        this.mode = mode;
        this.base = base;

        this.state = State.INITIAL;
    }

    public void copy(PeerState _state) {
        this.mode = _state.mode;
        this.base = _state.base;
        this.repeated = _state.repeated;
        this.state = _state.state;
        this.lastBestBlock = _state.lastBestBlock;
        this.lastHeaderRequest = _state.lastHeaderRequest;
    }

    public long getLastBestBlock() {
        return lastBestBlock;
    }

    public void setLastBestBlock(long lastBestBlock) {
        this.lastBestBlock = lastBestBlock;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        this.resetRepeated();
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
        this.state = State.HEADERS_REQUESTED;
        this.lastHeaderRequest = lastStatusRequest;
    }

    public void resetLastHeaderRequest() {
        this.state = State.INITIAL;
        this.lastHeaderRequest = 0;
    }

    public boolean isOverRepeatThreshold() {
        return repeated >= STEP_COUNT;
    }

    private void resetRepeated() {
        this.repeated = 0;
    }

    public void incRepeated() {
        this.repeated++;
    }

    public int getRepeated() {
        return repeated;
    }
}
