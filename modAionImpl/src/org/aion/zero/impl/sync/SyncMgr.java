package org.aion.zero.impl.sync;

import static org.aion.util.string.StringUtils.getNodeIdShort;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.impl.evt.EventConsensus;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.zero.impl.types.Block;
import org.aion.zero.impl.types.BlockHeader;
import org.aion.p2p.INode;
import org.aion.rlp.SharedRLPList;
import org.aion.zero.impl.config.StatsType;
import org.aion.p2p.IP2pMgr;
import org.aion.util.conversions.Hex;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.sync.SyncHeaderRequestManager.SyncMode;
import org.aion.zero.impl.sync.msg.ReqBlocksBodies;
import org.aion.zero.impl.sync.msg.ReqStatus;
import org.aion.zero.impl.sync.statistics.BlockType;
import org.aion.zero.impl.sync.statistics.RequestType;
import org.aion.zero.impl.types.BlockUtil;
import org.aion.zero.impl.valid.BlockHeaderValidator;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;

/** @author chris */
public final class SyncMgr {

    // interval - show status
    private static final long DELAY_SHOW_STATUS = 10L; // in seconds
    private static final long DELAY_STATUS_REQUEST = 2L; // in seconds
    /**
     * NOTE: This value was selected based on heap dumps for normal execution where the queue was
     * holding around 60 items.
     */
    private static final int QUEUE_CAPACITY = 100;
    private static final int HALF_QUEUE_CAPACITY = QUEUE_CAPACITY / 2;

    /**
     * Threshold for pushing received blocks to storage.
     *
     * @implNote Should be lower than {@link SyncHeaderRequestManager#MAX_BLOCK_DIFF}.
     */
    private static final int MAX_STORAGE_DIFF = 200;

    private static final Logger log = AionLoggerFactory.getLogger(LogEnum.SYNC.name());
    private static final Logger survey_log = AionLoggerFactory.getLogger(LogEnum.SURVEY.name());
    private static final Logger p2pLog = AionLoggerFactory.getLogger(LogEnum.P2P.name());

    private final NetworkStatus networkStatus = new NetworkStatus();

    @VisibleForTesting
    SyncHeaderRequestManager syncHeaderRequestManager;

    // store the hashes of blocks which have been successfully imported
    @VisibleForTesting
    final Map<ByteArrayWrapper, Object> importedBlockHashes = Collections.synchronizedMap(new LRUMap<>(4096));
    private AionBlockchainImpl chain;
    private IP2pMgr p2pMgr;
    private IEventMgr evtMgr;
    private SyncStats stats;

    private final ScheduledExecutorService syncExecutors;
    private final ThreadPoolExecutor importExecutor;

    private BlockHeaderValidator blockHeaderValidator;
    private volatile long timeUpdated = 0;

    private static final ReqStatus cachedReqStatus = new ReqStatus();

    public SyncMgr(final AionBlockchainImpl _chain,
        final IP2pMgr _p2pMgr,
        final IEventMgr _evtMgr,
        final boolean _showStatus,
        final Set<StatsType> showStatistics,
        final int maxActivePeers) {

        p2pMgr = _p2pMgr;
        chain = _chain;
        evtMgr = _evtMgr;
        syncExecutors = Executors.newScheduledThreadPool(4);
        importExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(QUEUE_CAPACITY));

        blockHeaderValidator = new ChainConfiguration().createBlockHeaderValidator();

        Set<StatsType> statsTypes = Collections.unmodifiableSet(new HashSet<>(showStatistics));

        long selfBest = chain.getBestBlock().getNumber();
        stats = new SyncStats(selfBest, _showStatus, statsTypes, maxActivePeers);

        syncHeaderRequestManager =  new SyncHeaderRequestManager(log, survey_log);

        syncExecutors.scheduleWithFixedDelay(() -> requestStatus(), 0L, DELAY_STATUS_REQUEST, TimeUnit.SECONDS);

        if (_showStatus) {
            syncExecutors.scheduleWithFixedDelay(() -> showStatus(statsTypes), 0, DELAY_SHOW_STATUS, TimeUnit.SECONDS);
        }

        setupEventHandler();
    }

    /**
     * Makes a status request to each active peer.
     */
    private void requestStatus() {
        Thread.currentThread().setName("sync-gs");
        for (INode node : p2pMgr.getActiveNodes().values()) {
            p2pMgr.send(node.getIdHash(), node.getIdShort(), cachedReqStatus);
            stats.updateTotalRequestsToPeer(node.getIdShort(), RequestType.STATUS);
            stats.updateRequestTime(node.getIdShort(), System.nanoTime(), RequestType.STATUS);
        }
    }

    /**
     * Display the current node status.
     */
    private void showStatus(Set<StatsType> showStatistics) {
        Thread.currentThread().setName("sync-ss");
        p2pLog.info(getStatus(chain, networkStatus, stats));

        String requestedStats;
        if (showStatistics.contains(StatsType.REQUESTS)) {
            requestedStats = stats.dumpRequestStats();
            if (!requestedStats.isEmpty()) {
                p2pLog.info(requestedStats);
            }
        }

        if (showStatistics.contains(StatsType.SEEDS)) {
            requestedStats = stats.dumpTopSeedsStats();
            if (!requestedStats.isEmpty()) {
                p2pLog.info(requestedStats);
            }
        }

        if (showStatistics.contains(StatsType.LEECHES)) {
            requestedStats = stats.dumpTopLeechesStats();
            if (!requestedStats.isEmpty()) {
                p2pLog.info(requestedStats);
            }
        }

        if (showStatistics.contains(StatsType.RESPONSES)) {
            requestedStats = stats.dumpResponseStats();
            if (!requestedStats.isEmpty()) {
                p2pLog.info(requestedStats);
            }
        }

        if (showStatistics.contains(StatsType.SYSTEMINFO)) {
            requestedStats = stats.dumpSystemInfo();
            if (!requestedStats.isEmpty()) {
                p2pLog.info(requestedStats);
            }
        }
    }

    private static String getStatus(AionBlockchainImpl chain, NetworkStatus networkStatus, SyncStats syncStats) {
        Block selfBest = chain.getBestBlock();
        String selfTd = selfBest.getTotalDifficulty().toString(10);

        return "sync-status avg-import="
                + String.format("%.2f", syncStats.getAvgBlocksPerSec()) + " b/s"
                + " td=" + selfTd + "/" + networkStatus.getTargetTotalDiff().toString(10)
                + " b-num=" + selfBest.getNumber() + "/" + networkStatus.getTargetBestBlockNumber()
                + " b-hash=" + Hex.toHexString(chain.getBestBlockHash()) + "/" + networkStatus.getTargetBestBlockHash();
    }

    /**
     * @param _displayId String
     * @param _remoteBestBlockNumber long
     * @param _remoteBestBlockHash byte[]
     * @param _remoteTotalDiff BigInteger null check for _remoteBestBlockHash && _remoteTotalDiff
     *     implemented on ResStatusHandler before pass through
     */
    public void updateNetworkStatus(
            String _displayId,
            long _remoteBestBlockNumber,
            final byte[] _remoteBestBlockHash,
            BigInteger _remoteTotalDiff,
            byte _apiVersion,
            short _peerCount,
            int _pendingTxCount,
            int _latency) {

        // self
        BigInteger selfTd = this.chain.getTotalDifficulty();

        // trigger send headers routine immediately
        if (_remoteTotalDiff.compareTo(selfTd) > 0) {
            this.getHeaders(selfTd);
        }

        long now = System.currentTimeMillis();
        if ((now - timeUpdated) > 1000) {
            timeUpdated = now;
            // update network best status
            synchronized (this.networkStatus) {
                BigInteger networkTd = this.networkStatus.getTargetTotalDiff();
                if (_remoteTotalDiff.compareTo(networkTd) > 0) {
                    String remoteBestBlockHash = Hex.toHexString(_remoteBestBlockHash);

                    if (log.isDebugEnabled()) {
                        log.debug(
                                "network-status-updated on-sync id={}->{} td={}->{} bn={}->{} bh={}->{}",
                                this.networkStatus.getTargetDisplayId(),
                                _displayId,
                                this.networkStatus.getTargetTotalDiff().toString(10),
                                _remoteTotalDiff.toString(10),
                                this.networkStatus.getTargetBestBlockNumber(),
                                _remoteBestBlockNumber,
                                this.networkStatus.getTargetBestBlockHash().isEmpty()
                                        ? ""
                                        : getNodeIdShort(
                                                this.networkStatus.getTargetBestBlockHash()),
                                getNodeIdShort(remoteBestBlockHash),
                                this.networkStatus.getTargetApiVersion(),
                                (int) _apiVersion,
                                this.networkStatus.getTargetPeerCount(),
                                _peerCount,
                                this.networkStatus.getTargetPendingTxCount(),
                                _pendingTxCount,
                                this.networkStatus.getTargetLatency(),
                                _latency);
                    }

                    this.networkStatus.update(
                            _displayId,
                            _remoteTotalDiff,
                            _remoteBestBlockNumber,
                            remoteBestBlockHash,
                            (int) _apiVersion,
                            _peerCount,
                            _pendingTxCount,
                            _latency);
                }
            }
        }
    }

    private void setupEventHandler() {
        List<IEvent> events = new ArrayList<>();
        events.add(new EventConsensus(EventConsensus.CALLBACK.ON_SYNC_DONE));
        this.evtMgr.registerEvent(events);
    }

    private void getHeaders(BigInteger _selfTd) {
        // Making requests only if the executor has capacity to add more than half the tasks since multiple requests may be sent at the same time.
        if (importExecutor.getQueue().size() < HALF_QUEUE_CAPACITY) {
            syncHeaderRequestManager.sendHeadersRequests(chain.getBestBlock().getNumber(), _selfTd, p2pMgr, stats);
        } else {
            log.debug("The kernel is busy importing blocks. Stopped requesting new headers.");
        }
    }

    /**
     * Validate the received batch of block headers, dispatch a request for the matching bodies and save the headers for
     * assembling the blocks when the bodies are received.
     *
     * @param nodeId the identifier of the peer that sent the block headers
     * @param displayId the display identifier for the peer that sent the block headers
     * @param headers the block headers received from the peer
     */
    public void validateAndAddHeaders(int nodeId, String displayId, List<BlockHeader> headers) {
        if (headers == null || headers.isEmpty()) {
            p2pMgr.errCheck(nodeId, displayId);
            log.error("<validate-headers: received empty/null headers from node={}>", displayId);
        } else {
            log.debug("<validate-headers: received start-block={} list-size={} node={}>", headers.get(0).getNumber(), headers.size(), displayId);

            // Filter imported block headers.
            List<BlockHeader> filtered = new ArrayList<>();
            BlockHeader prev = null;
            for (BlockHeader current : headers) {
                // Stop validating this batch if any invalidated header. Keep and import the valid ones.
                if (!blockHeaderValidator.validate(current, log)) {
                    log.debug("<validate-headers: received invalid header number={} hash={}>", current.getNumber(), current.getHashWrapper());
                    // Print header to allow debugging.
                    log.trace("<validate-headers: received invalid header {}>", current.toString());
                    break;
                }

                // Break if non-sequential blocks.
                if (prev != null && (current.getNumber() != (prev.getNumber() + 1) || !current.getParentHashWrapper().equals(prev.getHashWrapper()))) {
                    log.debug("<validate-headers: received non-sequential block headers node={} block-number={} expected-number={} parent-hash={} previous-hash={}>",
                            displayId, current.getNumber(), prev.getNumber() + 1, current.getParentHashWrapper(), prev.getHashWrapper());
                    break;
                }

                // Check for already imported blocks.
                if (!importedBlockHashes.containsKey(current.getHashWrapper())) {
                    filtered.add(current);
                }

                prev = current;
            }

            // Request bodies for the remaining headers (which are still a sequential list).
            if (!filtered.isEmpty()) {
                // Save headers for future bodies requests and matching with the received bodies.
                syncHeaderRequestManager.storeHeaders(nodeId, filtered);
                syncExecutors.execute(() -> requestBodies(nodeId, displayId));
            }
        }
    }

    /**
     * Requests the bodies associated to the given block headers.
     */
    void requestBodies(int nodeId, String displayId) {
        Thread.currentThread().setName("sync-gb-" + Thread.currentThread().getId());
        long startTime = System.nanoTime();

        List<List<BlockHeader>> forRequests = syncHeaderRequestManager.getHeadersForBodiesRequests(nodeId);
        for (List<BlockHeader> requestHeaders : forRequests) {
            // Filter headers again in case the blockchain has advanced while this task was waiting to be executed.
            List<BlockHeader> filtered = requestHeaders.stream().filter(h -> !importedBlockHashes.containsKey(ByteArrayWrapper.wrap(h.getHash()))).collect(Collectors.toList());
            // Check the peer state and discard blocks that are under the current best (in case the hashes already dropped from the above map).
            // This check is only applicable for SyncMode.NORMAL because the other sync modes deal with side chains.
            long currentBest = chain.getBestBlock() == null ? 0L : chain.getBestBlock().getNumber();
            long firstInBatch = requestHeaders.get(0).getNumber();
            if (syncHeaderRequestManager.getSyncMode(nodeId) == SyncMode.NORMAL && firstInBatch <= currentBest) {
                // remove all blocks in the batch that are under the current best
                for (Iterator<BlockHeader> it = filtered.iterator(); it.hasNext(); ) {
                    if (it.next().getNumber() <= currentBest) {
                        it.remove();
                    }
                }
            }
            if (filtered.size() == requestHeaders.size()) {
                // Log bodies request before sending the request.
                log.debug("<get-bodies from-num={} to-num={} node={}>", firstInBatch, requestHeaders.get(requestHeaders.size() - 1).getNumber(), displayId);
                p2pMgr.send(nodeId, displayId, new ReqBlocksBodies(requestHeaders.stream().map(k -> k.getHash()).collect(Collectors.toList())));
                stats.updateTotalRequestsToPeer(displayId, RequestType.BODIES);
                stats.updateRequestTime(displayId, System.nanoTime(), RequestType.BODIES);
            } else {
                // Drop the headers that are already known.
                syncHeaderRequestManager.dropHeaders(nodeId, requestHeaders);
                if (!filtered.isEmpty()) {
                    // Store the subset that is still useful.
                    syncHeaderRequestManager.storeHeaders(nodeId, filtered);
                }
            }
        }

        long duration = System.nanoTime() - startTime;
        survey_log.debug("TaskGetBodies: make request, duration = {} ns.", duration);
    }

    /**
     * @param _nodeIdHashcode int
     * @param _displayId String
     * @param _bodies List<byte[]> Assemble and validate blocks batch and add batch to import queue
     *     from network response blocks bodies
     */
    public void validateAndAddBlocks(int _nodeIdHashcode, String _displayId, final List<SharedRLPList> _bodies) {
        if (_bodies == null) return;
        log.debug("<received-bodies size={} node={}>", _bodies.size(), _displayId);

        // the requests are made such that the size varies to better map headers to bodies
        ByteArrayWrapper firstNodeRoot = ByteArrayWrapper.wrap(BlockUtil.getTxTrieRootFromUnsafeSource(_bodies.get(0)));
        List<BlockHeader> headers = syncHeaderRequestManager.matchAndDropHeaders(_nodeIdHashcode, _bodies.size(), firstNodeRoot);
        if (headers == null) {
            log.debug("<assemble-and-validate-blocks could not match headers for node={} size={} txTrieRoot={}>", _displayId, _bodies.size(), firstNodeRoot);
            return;
        }

        // assemble batch
        List<Block> blocks = new ArrayList<>(_bodies.size());
        Iterator<BlockHeader> headerIt = headers.iterator();
        Iterator<SharedRLPList> bodyIt = _bodies.iterator();
        while (headerIt.hasNext() && bodyIt.hasNext()) {
            Block block = BlockUtil.newBlockWithHeaderFromUnsafeSource(headerIt.next(),
                (SharedRLPList) bodyIt.next().get(0));
            if (block == null) {
                log.debug("<assemble-and-validate-blocks node={} size={}>", _displayId, _bodies.size());
                break;
            } else {
                blocks.add(block);
            }
        }

        int m = blocks.size();
        if (m == 0) {
            return;
        }

        log.debug("<assembled-blocks from={} size={} node={}>", blocks.get(0).getNumber(), blocks.size(), _displayId);

        // add batch
        syncExecutors.execute(() -> filterBlocks(new BlocksWrapper(_nodeIdHashcode, _displayId, blocks)));
    }

    /**
     * Filters the received blocks by delegating the ones far in the future (above {@link #MAX_STORAGE_DIFF} blocks ahead of the main chain)
     * to storage and delaying queue population when the predefined capacity is reached.
     */
    private void filterBlocks(final BlocksWrapper downloadedBlocks) {
        Thread.currentThread().setName("sync-filt-" + Thread.currentThread().getId());
        long currentBest = chain.getBestBlock() == null ? 0L : chain.getBestBlock().getNumber();
        boolean isFarInFuture = downloadedBlocks.firstBlockNumber > currentBest + MAX_STORAGE_DIFF;
        int queueSize = importExecutor.getQueue().size();
        log.debug("<import-status: import executor queue size={}>", queueSize);
        // After reaching restricted capacity store blocks that are not directly importable to reduce the likelihood of rejecting potential imports.
        boolean isRestrictedCapacity = (queueSize >= HALF_QUEUE_CAPACITY) && (downloadedBlocks.firstBlockNumber > currentBest + 1L);

        if (isFarInFuture || isRestrictedCapacity) {
            int stored = chain.storePendingBlockRange(downloadedBlocks.blocks, log);
            stats.updatePeerBlocks(downloadedBlocks.displayId, stored, BlockType.STORED);
        } else {
            importExecutor.execute(() -> TaskImportBlocks.importBlocks(chain, stats, downloadedBlocks, importedBlockHashes, syncHeaderRequestManager));
        }
    }

    public long getNetworkBestBlockNumber() {
        synchronized (this.networkStatus) {
            return this.networkStatus.getTargetBestBlockNumber();
        }
    }

    public synchronized void shutdown() {
        if (p2pLog.isDebugEnabled()) {
            // print all the gathered information before shutdown
            p2pLog.debug(getStatus(chain, networkStatus, stats));

            String requestedStats = stats.dumpRequestStats();
            if (!requestedStats.isEmpty()) {
                p2pLog.debug(requestedStats);
            }
            requestedStats = stats.dumpTopSeedsStats();
            if (!requestedStats.isEmpty()) {
                p2pLog.debug(requestedStats);
            }
            requestedStats = stats.dumpTopLeechesStats();
            if (!requestedStats.isEmpty()) {
                p2pLog.debug(requestedStats);
            }
            requestedStats = stats.dumpResponseStats();
            if (!requestedStats.isEmpty()) {
                p2pLog.debug(requestedStats);
            }
        }

        shutdownAndAwaitTermination(syncExecutors);
        shutdownAndAwaitTermination(importExecutor);
    }

    private void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("Pool did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    public SyncStats getSyncStats() {
        return this.stats;
    }
}
