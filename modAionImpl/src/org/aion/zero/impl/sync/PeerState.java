/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.zero.impl.sync;

import static org.aion.p2p.P2pConstant.STEP_COUNT;

import java.util.Objects;

public class PeerState {

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

    /**
     * Creates a new peer state.
     */
    public PeerState(Mode mode, long base) {
        this.mode = mode;
        this.base = base;

        this.state = State.INITIAL;
    }

    /**
     * Copy constructor.
     */
    public PeerState(PeerState _state) {
        this.copy(_state);
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

    /**
     * Method for checking if the state is in one of the fast modes, namely {@link Mode#LIGHTNING}
     * and {@link Mode#THUNDER}.
     *
     * @return {@code true} when the state is in one of the fast modes, {@code false} otherwise.
     */
    public boolean isInFastMode() {
        return mode == Mode.LIGHTNING || mode == Mode.THUNDER;
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

    public boolean isUnderRepeatThreshold() {
        return repeated < STEP_COUNT;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PeerState peerState = (PeerState) o;
        return base == peerState.base
            && repeated == peerState.repeated
            && lastBestBlock == peerState.lastBestBlock
            && lastHeaderRequest == peerState.lastHeaderRequest
            && mode == peerState.mode
            && state == peerState.state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, base, repeated, state, lastBestBlock, lastHeaderRequest);
    }

    @Override
    public String toString() {
        return "{"
            + mode.toString().charAt(0)
            + ", "
            + state.toString().substring(0, 2)
            + ", "
            + base
            + ", "
            + repeated
            + ", "
            + lastBestBlock
            + ", "
            + lastHeaderRequest
            + '}';
    }

    public enum Mode {
        /**
         * The peer is in main-chain. Use normal syncing strategy.
         *
         * @implNote When switching to this mode it is not necessary to set the base value. The base
         * will automatically be set to the current best block.
         */
        NORMAL,

        /**
         * The peer is in side-chain. Sync backward to find the fork point.
         */
        BACKWARD,

        /**
         * The peer is in side-chain. Sync forward to catch up.
         */
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
         *
         * @implNote When switching to this mode it is not necessary to set the base value. The base
         * will automatically be set to the current best block.
         */
        THUNDER
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
}
