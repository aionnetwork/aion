package org.aion.zero.impl.sync.state;

import org.aion.log.AionLoggerFactory;
import org.slf4j.Logger;

import java.math.BigInteger;

public class SyncPeerState {

    private static final Logger log = AionLoggerFactory.getLogger("SYNC");

    private final String idShort;

    private final int idHashCode;

    /**
     * Represents our current status of the peer in terms of syncing
     *
     * @implNote guarded by {@link #stateLock}
     */
    private volatile OutboundStatus outboundStatus;

    /**
     * When the peer last return a message to us
     *
     * @implNote guarded by {@link #timestampLock}
     */
    private volatile long lastReceivedMessage;

    /**
     * When the peer last returned a status message to us
     *
     * @implNote guarded by {@link #timestampLock}
     */
    private long lastReceivedStatusMessage;

    /**
     * When the peer last returned a headers message to us
     *
     * @implNote guarded by {@link #timestampLock}
     */
    private long lastReceivedHeadersMessage;

    /**
     * When the peer last returned a bodies message to us
     *
     * @implNote guarded by {@link #timestampLock}
     */
    private long lastReceivedBodiesMessage;

    /**
     * @implNote guarded by {@link #stateLock}
     */
    private long latestBlockNumber = 0L;

    /**
     * @implNote guarded by {@link #stateLock}
     */
    private BigInteger totalDifficulty = BigInteger.ZERO;

    private boolean isActive = false;

    /**
     * Our current perception of the peer, this is not used for anything
     * now, but it could be interesting to log it out
     *
     * @implNote guarded by {@link #stateLock}
     */
    private int rating = 0;

    private long lastRequestedBlockHeader = 0;

    private long lastRequestedBlockBody = 0;

    // NOTE: always lock in the order the code is listed here
    // DO NOT modify this order unless you are willing to make changes
    private final Object timestampLock = new Object();
    private final Object stateLock = new Object();

    public SyncPeerState(String idShort, int idHashCode) {
        this.idShort = idShort;
        this.idHashCode = idHashCode;
    }

    public void processStatusUpdate(final long latestBlockNumber, final BigInteger totalDifficulty) {
        synchronized (timestampLock) {
            this.lastReceivedStatusMessage = System.currentTimeMillis();
            this.lastReceivedMessage = this.lastReceivedStatusMessage;

            synchronized (stateLock) {
                if (log.isWarnEnabled()) {
                    if (latestBlockNumber < this.latestBlockNumber)
                        log.warn("detected regression in peer [{}/{}], bn: {} => {}, td: {} => {}",
                                this.idShort,
                                this.idHashCode,
                                this.latestBlockNumber,
                                latestBlockNumber,
                                this.totalDifficulty,
                                totalDifficulty);
                }

                this.latestBlockNumber = latestBlockNumber;
                this.totalDifficulty = totalDifficulty;
                this.rating++;
            }
        }
    }

    public boolean canSendHeaders() {
        return this.outboundStatus == OutboundStatus.FREE;
    }

    /**
     * Inquire whether we can send bodies, note that this implies that we
     * <b>have</b> to request headers before we can request bodies from
     * a peer.
     *
     * If this turns out to be too inefficient, we can change it
     */
    public boolean canSendBodies() {
        return this.outboundStatus == OutboundStatus.POST_HEADER_FREE;
    }

    public boolean isFree() {
        return this.outboundStatus == OutboundStatus.FREE;
    }

    public boolean checkReceiveHeadersValid(long firstHeaderBlockNumber) {
        synchronized (timestampLock) {
            synchronized (stateLock) {
                updateHeadersTimestamp();

                // this indicates that we received headers even though we did not
                // request them, for now just accept but log
                if (this.outboundStatus != OutboundStatus.REQ_HEADER_SENT) {
                    return false;
                }

                // the peer returned a wrong first block number to us, this could indicate
                // they did not receive the results correctly, either way this was
                // not what we expected
                if (this.lastRequestedBlockHeader != firstHeaderBlockNumber) {
                    return false;
                }

                // update state indicating the peer is free to use
                this.outboundStatus = OutboundStatus.POST_HEADER_FREE;
            }
        }
        return true;
    }

    public boolean checkReceiveBodiesValid(long firstBodyBlockNumber) {
        synchronized (timestampLock) {
            synchronized (stateLock) {
                updateBodiesTimestamp();

                if (this.outboundStatus != OutboundStatus.REQ_BODY_SENT) {
                    return false;
                }

                if (this.lastRequestedBlockHeader != firstBodyBlockNumber) {
                    return false;
                }

                // resolve to the first state, thereby completing the cycle
                this.outboundStatus = OutboundStatus.FREE;
            }
        }
        return true;
    }

    /**
     * @implNote guarded by {@link #timestampLock}
     */
    private void updateHeadersTimestamp() {
        this.lastReceivedHeadersMessage = System.currentTimeMillis();
        this.lastReceivedMessage = this.lastReceivedHeadersMessage;
    }

    /**
     * @implNote guarded by {@link #timestampLock}
     */
    private void updateBodiesTimestamp() {
        this.lastReceivedBodiesMessage = System.currentTimeMillis();
        this.lastReceivedMessage = this.lastReceivedBodiesMessage;
    }

    private void updateStatusTimestamp() {
        this.lastReceivedStatusMessage = System.currentTimeMillis();
        this.lastReceivedMessage = this.lastReceivedStatusMessage;
    }

    /**
     * General function to update the current state of the peer
     * If we find that a peer has timed out, it generally means that
     * the peer was unable to receive the message or failed to respond
     *
     * For now, we will be lenient and keep the peer as-is. In the
     * future we may want to make the rules more strict and drop
     * this peer if subsequent events occur repeatedly.
     */
    public void reset() {

    }

    public long getLastReceivedMessageTimestamp() {
        return this.lastReceivedMessage;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SyncPeerState)) {
            return false;
        }

        SyncPeerState otherPeerState = (SyncPeerState) other;
        return this.idHashCode == otherPeerState.idHashCode;
    }

    @Override
    public int hashCode() {
        return this.idHashCode;
    }
}
