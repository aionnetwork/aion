package org.aion.zero.impl.sync;

import static org.aion.zero.impl.sync.SyncHeaderRequestManager.SyncMode.BACKWARD;
import static org.aion.zero.impl.sync.SyncHeaderRequestManager.SyncMode.FORWARD;
import static org.aion.zero.impl.sync.SyncHeaderRequestManager.SyncMode.NORMAL;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.P2pConstant;
import org.aion.zero.impl.sync.msg.ReqBlocksHeaders;
import org.aion.zero.impl.sync.statistics.RequestType;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

/**
 * Manages access to the peers and their sync states.
 *
 * @author Alexandra Roatis
 */
public class SyncHeaderRequestManager {

    /**
     * The number of blocks overlapping with the current chain requested at import when the local
     * best block is far from the top block in the peer's chain.
     *
     * @implNote The functionality for matching headers to bodies currently requires this to be an
     *     odd number.
     */
    public static final int FAR_OVERLAPPING_BLOCKS = 3;

    /**
     * The number of blocks overlapping with the current chain requested at import when the local
     * best block is close to the top block in the peer's chain.
     *
     * @implNote The functionality for matching headers to bodies currently requires this to be an
     *     odd number.
     */
    public static final int CLOSE_OVERLAPPING_BLOCKS = 15; // must be an odd number

    /**
     * Defines the notion of far from the top of the chain. If the local chain is this many blocks
     * away from the network best or less it is considered close to synced with the network.
     */
    public static final int SWITCH_OVERLAPPING_BLOCKS_RANGE = 128;

    /**
     * Minimum request size used in header requests.
     *
     * @implNote The functionality for matching headers to bodies currently requires this to be an
     *     even number. More specifically, it is expected to be of different parity from {@link
     *     #FAR_OVERLAPPING_BLOCKS} and {@link #CLOSE_OVERLAPPING_BLOCKS}.
     */
    public static final int MIN_REQUEST_SIZE = 24;

    /**
     * Maximum request size used in header requests.
     *
     * @implNote The functionality for matching headers to bodies currently requires this to be an
     *     even number. More specifically, it is expected to be of different parity from {@link
     *     #FAR_OVERLAPPING_BLOCKS} and {@link #CLOSE_OVERLAPPING_BLOCKS}.
     * @implNote Must be greater than {@link #MIN_REQUEST_SIZE}.
     */
    public static final int MAX_REQUEST_SIZE = 40;

    /** Number of blocks used in {@link SyncMode#BACKWARD} to find the common chain. */
    private static final int BACKWARD_SYNC_STEP = 128;

    /** Cap on height of requests compared to the local chain. */
    private static final int MAX_BLOCK_DIFF = 10_000;

    /**
     * Header request cap.
     *
     * @implNote Must be under the cap set by the p2p layer {@link P2pConstant#READ_MAX_RATE_TXBC }.
     * @implNote A value that is too large here can lead to a decrease in sync performance. Update
     *     only with strong experimental data to back up the change.
     */
    private static final int MAX_REQUESTS_PER_SECOND = 2;

    /** Number of nanoseconds in one second. Used for computations of time differences. */
    private static final int ONE_SECOND = 1_000_000_000;

    // track the different peers
    private final Map<Integer, RequestState> bookedPeerStates, availablePeerStates;

    // store the headers whose bodies have been requested from corresponding peer
    private final Map<Integer, Map<Integer, HeadersWrapper>> storedHeaders;

    private final Set<Integer> knownActiveNodes;

    private long localHeight, networkHeight, requestHeight;
    private final Logger syncLog, surveyLog;

    public SyncHeaderRequestManager(Logger syncLog, Logger surveyLog) {
        Objects.requireNonNull(syncLog);
        Objects.requireNonNull(surveyLog);

        // ensures that the chosen constants will not violate p2p limitations
        if (MAX_REQUESTS_PER_SECOND > P2pConstant.READ_MAX_RATE_TXBC) {
            throw new IllegalStateException("The MAX_REQUESTS_PER_SECOND constant is incorrectly set to be larger than the P2P cool down route threshold which will cause delays in communication with peers.");
        }

        this.syncLog = syncLog;
        this.surveyLog = surveyLog;

        // implementation details
        this.bookedPeerStates = new HashMap<>();
        this.availablePeerStates = new HashMap<>();
        this.storedHeaders = new HashMap<>();
        this.knownActiveNodes = new HashSet<>();
        this.localHeight = 0;
        this.networkHeight = 0;
        this.requestHeight = 0;
    }

    /**
     * Updates the internal tracked sync and request states, then generates and sends incremental
     * header requests to all available peers.
     *
     * @param currentBestBlock the local best known block
     * @param currentTotalDifficulty the local chain total difficulty
     * @param p2pManager provides access to the current peer list and their status
     * @param syncStatistics records sync statistics
     */
    public synchronized void sendHeadersRequests(
            long currentBestBlock,
            BigInteger currentTotalDifficulty,
            IP2pMgr p2pManager,
            SyncStats syncStatistics) {
        // for runtime survey information
        long startTime = System.nanoTime();

        int count = 0;

        // retrieves from the p2p manager the active nodes with adequate total difficulty
        Map<Integer, INode> currentNodes =
                p2pManager.getActiveNodes().values().stream()
                        .filter(node -> isAdequateTotalDifficulty(node, currentTotalDifficulty))
                        .collect(Collectors.toMap(node -> node.getIdHash(), node -> node));

        // makes sure the internal peer list is up to date and checks availability updates
        updateActiveNodes(currentNodes);

        // creates consecutive requests for the available peers
        List<RequestState> statesForRequest = updateStatesForRequests(currentBestBlock);

        for (RequestState requestState : statesForRequest) {
            String peerAlias = requestState.alias;
            long from = requestState.from;
            int take = requestState.size;

            if (from <= requestState.lastBestBlock || requestState.lastBestBlock == 0) {
                // send request
                p2pManager.send(requestState.id, peerAlias, new ReqBlocksHeaders(from, take));

                // record that another request has been made for availability tracking
                requestState.saveRequestTime(System.nanoTime());
                bookedPeerStates.put(requestState.id, requestState);

                syncLog.debug(
                        "<get-headers mode={} from-num={} size={} node={}>",
                        requestState.mode,
                        from,
                        take,
                        peerAlias);

                // record stats
                syncStatistics.updateTotalRequestsToPeer(peerAlias, RequestType.STATUS);
                syncStatistics.updateRequestTime(peerAlias, System.nanoTime(), RequestType.HEADERS);
                count++;
            } else {
                // TODO: possible optimisation: we can track and recycle the missed request
                // skipping this request since it will not return anything
                // and making it available for future attempts
                requestState.from = 0;
                availablePeerStates.put(requestState.id, requestState);
            }
        }

        long duration = System.nanoTime() - startTime;
        surveyLog.info(
                "Request Stage 2: made {} header request{}, duration = {} ns.",
                count,
                (count == 1 ? "" : "s"),
                duration);
    }

    /** Checks that the peer's total difficulty is higher than or equal to the local chain. */
    private static boolean isAdequateTotalDifficulty(INode peer, BigInteger totalDifficulty) {
        return peer.getTotalDifficulty() != null && peer.getTotalDifficulty().compareTo(totalDifficulty) >= 0;
    }

    /**
     * Updates the internally tracked states according to the given active peer list.
     *
     * <ol>
     *   <li>Ensures the inactive peers are dropped from all internal tracking and new peers are
     *       added according to the provided list of active connections.
     *   <li>Updates the best known block number for all active peers and the known network height.
     *   <li>Booked peers are checked for a change in their status based on the availability defined
     *       in {@link RequestState#tryMakeAvailable()} which takes into account the number of
     *       header requests allowed per second.
     * </ol>
     */
    private void updateActiveNodes(Map<Integer, INode> current) {
        // find entries in the knownActiveNodes set that are not in the current map
        Set<Integer> dropped =
                knownActiveNodes.stream()
                        .filter(node -> !current.containsKey(node))
                        .collect(Collectors.toSet());

        // remove dropped connections
        for (Integer id : dropped) {
            storedHeaders.remove(id);
            bookedPeerStates.remove(id);
            availablePeerStates.remove(id);
        }

        // add new peers and update best block for known peers
        for (INode node : current.values()) {
            Integer id = node.getIdHash();
            if (bookedPeerStates.containsKey(id)) { // update best
                bookedPeerStates.get(id).lastBestBlock = node.getBestBlockNumber();
            } else if (availablePeerStates.containsKey(id)) { // update best
                availablePeerStates.get(id).lastBestBlock = node.getBestBlockNumber();
            } else { // add peer
                availablePeerStates.put(
                        id, new RequestState(id, node.getIdShort(), node.getBestBlockNumber()));
            }

            // update the known network height
            networkHeight = Math.max(networkHeight, node.getBestBlockNumber());
        }

        // update known active nodes
        knownActiveNodes.clear();
        knownActiveNodes.addAll(current.keySet());

        // reset booked states if now available
        if (!bookedPeerStates.isEmpty()) {
            // check if any of the booked states have become available
            Iterator<RequestState> states = bookedPeerStates.values().iterator();
            while (states.hasNext()) {
                RequestState currentState = states.next();
                if (currentState.tryMakeAvailable()) {
                    availablePeerStates.put(currentState.id, currentState);
                    states.remove();
                }
            }
        }
    }

    /**
     * Used in <b>unit tests</b> for validating correctness of the {@link #updateActiveNodes(Map)}
     * method.
     *
     * <p>This method takes the input to the tested functionality and the expected outcomes. When
     * one of the outcomes is a {@code null} object the code path is not checked.
     *
     * @return a pair of objects, the first indicating the success of the test {@code true} if the
     *     expected behaviour is observed, {@code false} otherwise, the second provides a meaningful
     *     message in case of failure
     */
    @VisibleForTesting
    Pair<Boolean, String> assertUpdateActiveNodes(
            Map<Integer, INode> currentActiveNodes,
            Set<Integer> expectedStoredHeaders,
            Set<Integer> expectedBooked,
            Set<Integer> expectedAvailable,
            Set<Integer> expectedKnown,
            Long expectedNetworkHeight) {
        updateActiveNodes(currentActiveNodes);

        if (expectedStoredHeaders != null // ignored when set to null
                && (storedHeaders.size() != expectedStoredHeaders.size()
                        || !(storedHeaders.keySet().containsAll(expectedStoredHeaders)))) {
            return Pair.of(
                    false,
                    "The stored headers were not correctly updated:\n\texpected="
                            + Arrays.toString(expectedStoredHeaders.toArray())
                            + "\n\tactual="
                            + Arrays.toString(storedHeaders.keySet().toArray()));
        }

        if (expectedBooked != null // ignored when set to null
                && (bookedPeerStates.size() != expectedBooked.size()
                        || !(bookedPeerStates.keySet().containsAll(expectedBooked)))) {
            return Pair.of(
                    false,
                    "The booked states were not correctly updated:\n\texpected="
                            + Arrays.toString(expectedBooked.toArray())
                            + "\n\tactual="
                            + Arrays.toString(bookedPeerStates.keySet().toArray()));
        }

        if (expectedAvailable != null // ignored when set to null
                && (availablePeerStates.size() != expectedAvailable.size()
                        || !(availablePeerStates.keySet().containsAll(expectedAvailable)))) {
            return Pair.of(
                    false,
                    "The available states were not correctly updated:\n\texpected="
                            + Arrays.toString(expectedAvailable.toArray())
                            + "\n\tactual="
                            + Arrays.toString(availablePeerStates.keySet().toArray()));
        }

        if (expectedKnown != null // ignored when set to null
                && (knownActiveNodes.size() != expectedKnown.size()
                        || !(knownActiveNodes.containsAll(expectedKnown)))) {
            return Pair.of(
                    false,
                    "The known peers were not correctly updated:\n\texpected="
                            + Arrays.toString(expectedKnown.toArray())
                            + "\n\tactual="
                            + Arrays.toString(knownActiveNodes.toArray()));
        }

        if (expectedNetworkHeight != null // ignored when set to null
                && !(networkHeight == expectedNetworkHeight)) {
            return Pair.of(
                    false,
                    "The network height were not correctly updated:\n\texpected="
                            + expectedNetworkHeight
                            + "\n\tactual="
                            + networkHeight);
        }

        return Pair.of(true, "Expected output matched.");
    }

    /**
     * Returns a list of {@link RequestState} objects ready for use in sending header requests.
     *
     * <p>The request states are set up based on the following heuristic:
     *
     * <ol>
     *   <li>All available peers are prepared for a request.
     *   <li>The first request is made based on the given {@code currentBestBlock} chain height,
     *       with a small overlap {@link #FAR_OVERLAPPING_BLOCKS} in header requests if the current
     *       height is far from the network best, and a larger overlap {@link
     *       #CLOSE_OVERLAPPING_BLOCKS} when close to the top. If a request with this calculated
     *       starting point has already been made from this peer, a larger starting point is used
     *       instead.
     *   <li>The next requests are made anticipating a successful response to the first one. They
     *       are created incrementally based on previously requested blocks without overlap. The
     *       starting point is either where the first request left off or the largest block
     *       requested since the start (when this value is less than {@link #MAX_BLOCK_DIFF} blocks
     *       ahead of the current best).
     *   <li>To allow multiple requests made at the same time with reduced chances for assembly
     *       errors when the bodies are received, each peers state keeps track of the size of the
     *       last request made and updates the size to iterate within a range of even numbers given
     *       by the constants {@link #MIN_REQUEST_SIZE} and {@link #MAX_REQUEST_SIZE}. The values
     *       are even numbers to allow for the different sizes returned by the overlapping requests
     *       which will be odd numbers.
     * </ol>
     */
    private List<RequestState> updateStatesForRequests(long currentBestBlock) {
        // update the known localHeight
        localHeight = Math.max(localHeight, currentBestBlock);

        long nextFrom;
        SyncMode nextMode;

        // add the requested number to the list of requests to be made
        if (networkHeight >= currentBestBlock + SWITCH_OVERLAPPING_BLOCKS_RANGE) {
            nextFrom = Math.max(1, currentBestBlock - FAR_OVERLAPPING_BLOCKS);
        } else {
            nextFrom = Math.max(1, currentBestBlock - CLOSE_OVERLAPPING_BLOCKS);
        }
        nextMode = SyncMode.NORMAL;

        List<RequestState> requestStates = new ArrayList<>();
        for (RequestState state : availablePeerStates.values()) {
            // set up the size to decrease the chance of overlap for consecutive headers requests
            // the range is from MIN to MAX_LARGE_REQUEST_SIZE
            // avoids overlap with FAR_OVERLAPPING_BLOCKS and CLOSE_OVERLAPPING_BLOCKS because they
            // are odd and these are even numbers
            int nextSize = state.size - 2;
            if (nextSize < MIN_REQUEST_SIZE) {
                nextSize = MAX_REQUEST_SIZE;
            }

            if (state.mode == BACKWARD) {
                state.from = Math.max(1, state.from - BACKWARD_SYNC_STEP);
                state.size = nextSize;
            } else if (state.mode == FORWARD) {
                state.from = state.from + state.size;
                state.size = nextSize;
            } else {
                // if we already made a request from this peer with this base, increase the base
                if (state.from == nextFrom) {
                    nextFrom = nextFrom + state.size;
                }

                // under normal circumstances use the predefined request size
                state.from = nextFrom;
                state.mode = nextMode;
                state.size = nextSize;

                // update the maximum request height
                requestHeight = Math.max(requestHeight, nextFrom + nextSize);
                // set up for next peer
                nextFrom =
                        requestHeight > currentBestBlock + MAX_BLOCK_DIFF
                                ? nextFrom + nextSize
                                : requestHeight;
            }

            requestStates.add(state);
        }

        // the available peers have all been processed
        availablePeerStates.clear();

        return requestStates;
    }

    /**
     * Used in <b>unit tests</b> for validating correctness of the {@link
     * #updateStatesForRequests(long)} method.
     *
     * <p>This method takes the input to the tested functionality and the expected outcomes. When
     * one of the outcomes is a {@code null} object the code path is not checked.
     *
     * @return a pair of objects, the first indicating the success of the test {@code true} if the
     *     expected behaviour is observed, {@code false} otherwise, the second provides a meaningful
     *     message in case of failure
     */
    @VisibleForTesting
    Pair<Boolean, String> assertUpdateStatesForRequests(
            long currentBestBlock,
            Map<Integer, Long> expectedFrom,
            Map<Integer, Integer> expectedSize) {
        Map<Integer, RequestState> states =
                updateStatesForRequests(currentBestBlock).stream()
                        .collect(Collectors.toMap(n -> n.id, n -> n));

        // ensure that all available states were processed
        if (!availablePeerStates.isEmpty()) {
            return Pair.of(
                    false,
                    "Some available peers states were not processed:\n\texpected=[]"
                            + "\n\tactual="
                            + Arrays.toString(availablePeerStates.keySet().toArray()));
        }

        if (expectedFrom != null) { // ignored when set to null
            if (states.size() != expectedFrom.size()
                    || !(states.keySet().containsAll(expectedFrom.keySet()))) {
                return Pair.of(
                        false,
                        "The number of returned states does not match the expected list size:\n\texpected="
                                + Arrays.toString(expectedFrom.keySet().toArray())
                                + "\n\tactual="
                                + Arrays.toString(states.keySet().toArray()));
            }
            for (Map.Entry<Integer, Long> entry : expectedFrom.entrySet()) {
                int id = entry.getKey();
                long from = entry.getValue();
                RequestState peer = states.get(id);
                if (from != peer.from) {
                    return Pair.of(
                            false,
                            "The base for "
                                    + peer
                                    + " does not match the expected from:\n\texpected="
                                    + entry.getValue()
                                    + "\n\tactual="
                                    + peer.from);
                }
            }
        }

        if (expectedSize != null) { // ignored when set to null
            if (states.size() != expectedSize.size()
                    || !(states.keySet().containsAll(expectedFrom.keySet()))) {
                return Pair.of(
                        false,
                        "The number of returned states does not match the expected list size:\n\texpected="
                                + Arrays.toString(expectedSize.keySet().toArray())
                                + "\n\tactual="
                                + Arrays.toString(states.keySet().toArray()));
            }
            for (Map.Entry<Integer, Integer> entry : expectedSize.entrySet()) {
                int id = entry.getKey();
                long size = entry.getValue();
                RequestState peer = states.get(id);
                if (size != peer.size) {
                    return Pair.of(
                            false,
                            "The size for "
                                    + peer
                                    + " does not match the expected size:\n\texpected="
                                    + entry.getValue()
                                    + "\n\tactual="
                                    + peer.size);
                }
            }
        }

        return Pair.of(true, "Expected output matched.");
    }

    /** Keeps track of received headers. */
    public synchronized void storeHeaders(int peerId, HeadersWrapper headersWrapper) {
        Objects.requireNonNull(headersWrapper);

        // store the received headers for later matching with bodies
        int size = headersWrapper.size;
        if (storedHeaders.containsKey(peerId)) {
            storedHeaders.get(peerId).put(size, headersWrapper);
        } else {
            Map<Integer, HeadersWrapper> peerHeaders = new HashMap<>();
            peerHeaders.put(size, headersWrapper);
            storedHeaders.put(peerId, peerHeaders);
        }

        // headers were received so the peer is available for further requests
        if (bookedPeerStates.containsKey(peerId)
                && bookedPeerStates.get(peerId).tryMakeAvailable()) {
            availablePeerStates.put(peerId, bookedPeerStates.remove(peerId));
        }

        syncLog.debug(
                "<save-headers node={} size={} object={}>",
                headersWrapper.displayId,
                headersWrapper.size,
                headersWrapper);
    }

    /** Returns the headers received for the given size. */
    public synchronized HeadersWrapper matchHeaders(int peerId, int size) {
        if (!storedHeaders.containsKey(peerId)) {
            syncLog.debug("<match-headers null for nodeId={}", peerId, size);
            return null;
        }

        HeadersWrapper headersWrapper = storedHeaders.get(peerId).remove(size);
        if (headersWrapper != null) {
            syncLog.debug(
                    "<match-headers node={} size={} object={}>",
                    headersWrapper.displayId,
                    headersWrapper.size,
                    headersWrapper);
        } else {
            syncLog.debug("<match-headers null for nodeId={} size={}", peerId, size);
        }

        // these get dropped by a different method call only if correctly matched to bodies
        // they get overwritten if not used by the next same size response
        return headersWrapper;
    }

    /**
     * Sets a peer state to a specific mode. The set state is meaningful only for {@link
     * SyncMode#BACKWARD} and {@link SyncMode#FORWARD} since the other modes are automatically
     * managed.
     */
    public synchronized void runInMode(int peerId, SyncMode mode) {
        // checks both lists for the peer
        RequestState state = bookedPeerStates.get(peerId);
        if (state == null) {
            state = availablePeerStates.get(peerId);
        }

        // if the peer is known, makes the update
        if (state != null) {
            syncLog.debug(
                    "<mode-update: node={}, nodeId={}, from_mode={}, to_mode={}>",
                    state.alias,
                    peerId,
                    state.mode,
                    mode);
            state.mode = mode;
        }
    }

    /**
     * Retrieves the last used {@link SyncMode} for the peer given by identifier.
     *
     * @param peerId the identifier of the peer of interest
     * @return the last used {@link SyncMode} for the peer given by id.
     */
    public synchronized SyncMode getSyncMode(int peerId) {
        RequestState state = bookedPeerStates.get(peerId);
        if (state == null) {
            state = availablePeerStates.get(peerId);
        }
        return state == null ? null : state.mode;
    }

    public enum SyncMode {
        /** The peer is in main-chain. Use fast syncing strategy. */
        NORMAL,

        /** The peer is in side-chain. Sync backward to find the fork point. */
        BACKWARD,

        /** The peer is in side-chain. Sync forward to catch up. */
        FORWARD;
    }

    private static class RequestState {
        // reference to corresponding node
        private final int id;
        private final String alias;

        // state information
        private long lastBestBlock;
        private final TreeSet<Long> headerRequests;

        // sync request
        private SyncMode mode;
        private long from;
        private int size;

        public RequestState(int id, String alias, long lastBestBlock) {
            // reference to corresponding node
            this.id = id;
            this.alias = alias;
            // state information
            this.lastBestBlock = lastBestBlock;
            this.headerRequests = new TreeSet<>();
            // initial sync request data
            this.mode = NORMAL;
            this.from = 0;
            this.size = MIN_REQUEST_SIZE;
        }

        /** Stores the nano time of the last header request. */
        public void saveRequestTime(long latestStatusRequest) {
            headerRequests.add(latestStatusRequest);
        }

        /** Determines if a request can be sent based on the route cool down. */
        public boolean tryMakeAvailable() {
            if (headerRequests.size() < MAX_REQUESTS_PER_SECOND) {
                // have not reached the limit of requests
                return true;
            } else {
                long now = System.nanoTime();
                long first = headerRequests.first();

                if ((now - first) <= ONE_SECOND) {
                    // less than a second has passed since the first request
                    return false;
                } else {
                    // more than 1 second has passed, so we can make another request
                    // remove first request (no longer useful) to keep the size capped
                    headerRequests.remove(first);
                    return true;
                }
            }
        }
    }
}
