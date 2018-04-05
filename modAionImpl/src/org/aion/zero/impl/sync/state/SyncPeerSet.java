package org.aion.zero.impl.sync.state;

import org.aion.log.AionLoggerFactory;
import org.aion.p2p.INode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the set of peers we can currently sync from
 */
public class SyncPeerSet {

    private static final Logger log = AionLoggerFactory.getLogger("SYNC");

    /**
     * The set of peers we consider syncing from
     *
     * @implNote guarded by {@link #peerSet}
     */
    private final Map<Integer, SyncPeerState> peerSet = new LinkedHashMap<>();

    public List<SyncPeerState> updateSet(List<INode> nodes) {
        List<SyncPeerState> newPeers = new ArrayList<>();
        synchronized (peerSet) {
            for (INode node : nodes) {
                SyncPeerState peerState = registerPeer(node);

                if (peerState != null)
                    newPeers.add(peerState);
            }

            long currentTime = System.currentTimeMillis();
            // also perform some updates on the peer set
            for (SyncPeerState peer : this.peerSet.values()) {
                // if no response for 10 seconds, assume something has gone wrong, reset the state
                if (currentTime - peer.getLastReceivedContentTimestamp() > 10_000L) {
                    peer.reset();

                    if (log.isTraceEnabled()) {
                        log.trace("peer {} inactive for > 10s, resetting", peer.getShortId());
                    }
                }
            }
        }
        return newPeers;
    }

    /**
     * @implNote guarded by {@link #peerSet}
     */
    private SyncPeerState registerPeer(INode node) {
        if (this.peerSet.keySet().contains(node.getIdHash())) {

            if (log.isTraceEnabled()) {
                log.trace("Node {} already exists as peer", node.getIdShort());
            }
            return null;
        }

        if (log.isTraceEnabled()) {
            log.trace("added {} to SyncPeerSet", node.getIdShort());
        }

        // otherwise this is a new peer
        SyncPeerState state = new SyncPeerState(node.getIdShort(), node.getIdHash());
        this.peerSet.put(state.hashCode(), state);
        return state;
    }

    /**
     * Gets list of peers that are currently available (not busy)
     *
     * @apiNote <p>users must take care when acting on this set, for example
     * it is very easy to create an inconsistent state when the freePeers()
     * set is grabbed by two parties, both of which want to send headers.</p>
     *
     * <p>The calling thread should <b>always</b> be synchronizing on some object
     * before calling this, (atleast synchronized on {@code this} before calling)</p>
     *
     * If we do go into an inconsistent state, for example:
     *
     * Thread-A -> grabs
     * Thread-B -> grabs
     * Thread-A -> sends headers
     * Thread-B -> sends bodies
     * Thread-A -> updateSentHeaders()
     * Thread-B -> updateSentBodies()
     *
     * In this scenario, we would have inadvertently sent a peer two requests
     * when we should really be limiting the scenario to one, the correct approach is:
     *
     * Thread-A -> locks onto peerSet
     * Thread-A -> grabs
     * Thread-B -> waits for unlock
     * Thread-A -> sends headers
     * Thread-A -> updateSentHeaders()
     * Thread-A -> unlock()
     * Thread-B -> acquires
     * Thread-B -> grabs
     * Thread-B -> sends bodies
     * Thread-B -> updateSentBodies()
     * Thread-A -> unlock()
     */
    public List<SyncPeerState> getAbleToSendHeaderPeers(BigInteger currentDiff) {
        synchronized (peerSet) {
            return this.peerSet
                    .values()
                    .stream()
                    .filter(SyncPeerState::canSendHeaders)
                    .filter(p -> p.getTotalDifficulty().compareTo(currentDiff) >= 0)
                    .collect(Collectors.toList());
        }
    }


    /**
     * Same as {@link #getAbleToSendHeaderPeers(BigInteger)} ()} but for block
     * bodies
     */
    public List<SyncPeerState> getAbleToSendBodiesPeers(BigInteger currentDiff) {
        synchronized (peerSet) {
            return this.peerSet
                    .values()
                    .stream()
                    .filter(SyncPeerState::canSendBodies)
                    .filter(p -> p.getTotalDifficulty().compareTo(currentDiff) >= 0)
                    .collect(Collectors.toList());
        }
    }

    public void removeInactive() {
        synchronized (peerSet) {
            this.peerSet.values().removeIf(v -> v.getLastReceivedMessageTimestamp() > 20_000L);
        }
    }

    public SyncPeerState getSyncPeer(int hashCode) {
        return this.peerSet.get(hashCode);
    }
}
