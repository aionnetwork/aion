package org.aion.zero.impl.sync.state;

import org.aion.p2p.INode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the set of peers we can currently sync from
 */
public class SyncPeerSet {

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
        }
        return newPeers;
    }

    /**
     * @implNote guarded by {@link #peerSet}
     */
    private SyncPeerState registerPeer(INode node) {
        if (this.peerSet.keySet().contains(node.getIdHash()))
            return null;

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
    public List<SyncPeerState> getFreePeers() {
        synchronized (peerSet) {
            return this.peerSet
                    .values()
                    .stream()
                    .filter(SyncPeerState::isFree)
                    .collect(Collectors.toList());
        }
    }

    /**
     * This should be called sporadically as we want to maintain as much state as possible
     * to avoid forgetting states due to a unstable connection
     */
    public void removeInactive() {
        synchronized (peerSet) {
            this.peerSet.values().removeIf(v -> v.getLastReceivedMessageTimestamp() > 20_000L);
        }
    }
}
