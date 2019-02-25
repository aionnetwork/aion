package org.aion.zero.impl.sync;

import static org.aion.p2p.P2pConstant.BACKWARD_SYNC_STEP;
import static org.aion.p2p.P2pConstant.CLOSE_OVERLAPPING_BLOCKS;
import static org.aion.p2p.P2pConstant.FAR_OVERLAPPING_BLOCKS;
import static org.aion.p2p.P2pConstant.LARGE_REQUEST_SIZE;
import static org.aion.p2p.P2pConstant.REQUEST_SIZE;
import static org.aion.zero.impl.sync.PeerState.Mode.NORMAL;
import static org.aion.zero.impl.sync.PeerState.Mode.THUNDER;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.sync.msg.ReqBlocksHeaders;
import org.slf4j.Logger;

/** @author chris */
final class TaskGetHeaders implements Runnable {

    private final IP2pMgr p2p;

    private final long selfNumber;

    private final BigInteger selfTd;

    private final Map<Integer, PeerState> peerStates;

    private final SyncStats stats;

    private final Logger log;

    private final Random random = new Random(System.currentTimeMillis());

    TaskGetHeaders(
            IP2pMgr p2p,
            long selfNumber,
            BigInteger selfTd,
            Map<Integer, PeerState> peerStates,
            final SyncStats _stats,
            Logger log) {
        this.p2p = p2p;
        this.selfNumber = selfNumber;
        this.selfTd = selfTd;
        this.peerStates = peerStates;
        this.stats = _stats;
        this.log = log;
    }

    /** Checks that the peer's total difficulty is higher than or equal to the local chain. */
    private boolean isAdequateTotalDifficulty(INode n) {
        return n.getTotalDifficulty() != null && n.getTotalDifficulty().compareTo(this.selfTd) >= 0;
    }

    /** Checks that the required time has passed since the last request. */
    private boolean isTimelyRequest(long now, INode n) {
        return (now - 5000)
                > peerStates
                        .computeIfAbsent(n.getIdHash(), k -> new PeerState(NORMAL, selfNumber))
                        .getLastHeaderRequest();
    }

    @Override
    public void run() {
        // get all active nodes
        Collection<INode> nodes = this.p2p.getActiveNodes().values();

        // filter nodes by total difficulty
        long now = System.currentTimeMillis();
        List<INode> nodesFiltered =
                nodes.stream()
                        .filter(n -> isAdequateTotalDifficulty(n) && isTimelyRequest(now, n))
                        .collect(Collectors.toList());

        if (nodesFiltered.isEmpty()) {
            return;
        }

        // pick one random node
        INode node = nodesFiltered.get(random.nextInt(nodesFiltered.size()));

        // fetch the peer state
        PeerState state = peerStates.get(node.getIdHash());

        // decide the start block number
        long from = 0;
        int size = REQUEST_SIZE;

        state.setLastBestBlock(node.getBestBlockNumber());

        switch (state.getMode()) {
            case LIGHTNING:
                {
                    // request far forward blocks
                    if (state.getBase() > selfNumber + LARGE_REQUEST_SIZE
                            // there have not been STEP_COUNT sequential requests
                            && state.isUnderRepeatThreshold()) {
                        size = LARGE_REQUEST_SIZE;
                        from = state.getBase();
                        break;
                    } else {
                        // transition to ramp down strategy
                        state.setMode(THUNDER);
                    }
                }
            case THUNDER:
                {
                    // there have not been STEP_COUNT sequential requests
                    if (state.isUnderRepeatThreshold()) {
                        state.setBase(selfNumber);
                        size = LARGE_REQUEST_SIZE;
                        from = Math.max(1, selfNumber - FAR_OVERLAPPING_BLOCKS);
                        break;
                    } else {
                        // behave as normal
                        state.setMode(NORMAL);
                    }
                }
            case NORMAL:
                {
                    // update base block
                    state.setBase(selfNumber);

                    // normal mode
                    long nodeNumber = node.getBestBlockNumber();
                    if (nodeNumber >= selfNumber + BACKWARD_SYNC_STEP) {
                        from = Math.max(1, selfNumber - FAR_OVERLAPPING_BLOCKS);
                    } else if (nodeNumber >= selfNumber - BACKWARD_SYNC_STEP) {
                        from = Math.max(1, selfNumber - CLOSE_OVERLAPPING_BLOCKS);
                    } else {
                        // no need to request from this node. His TD is probably corrupted.
                        return;
                    }
                    break;
                }
            case BACKWARD:
                {
                    int backwardStep;
                    // the randomness improves performance when
                    // multiple peers are on the side-chain
                    if (random.nextBoolean()) {
                        // step back by REQUEST_SIZE to BACKWARD_SYNC_STEP blocks
                        backwardStep = size * (random.nextInt(BACKWARD_SYNC_STEP / size) + 1);
                    } else {
                        // step back by BACKWARD_SYNC_STEP blocks
                        backwardStep = BACKWARD_SYNC_STEP;
                    }
                    from = Math.max(1, state.getBase() - backwardStep);
                    break;
                }
            case FORWARD:
                {
                    // start from base block
                    from = state.getBase() + 1;
                    break;
                }
        }

        // send request
        if (log.isDebugEnabled()) {
            log.debug(
                    "<get-headers mode={} from-num={} size={} node={}>",
                    state.getMode(),
                    from,
                    size,
                    node.getIdShort());
        }
        ReqBlocksHeaders rbh = new ReqBlocksHeaders(from, size);
        this.p2p.send(node.getIdHash(), node.getIdShort(), rbh);
        stats.updateTotalRequestsToPeer(node.getIdShort(), RequestType.STATUS);
        stats.updateHeadersRequest(node.getIdShort(), System.nanoTime());

        // update timestamp
        state.setLastHeaderRequest(now);
    }
}
