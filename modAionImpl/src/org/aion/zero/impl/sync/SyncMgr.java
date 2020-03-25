package org.aion.zero.impl.sync;

import static org.aion.util.string.StringUtils.getNodeIdShort;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.impl.evt.EventConsensus;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.p2p.INode;
import org.aion.zero.impl.config.StatsType;
import org.aion.p2p.IP2pMgr;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.blockchain.ChainConfiguration;
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
    private static final int INTERVAL_SHOW_STATUS = 10000;
    private static final long DELAY_STATUS_REQUEST = 2L; // in seconds
    /**
     * NOTE: This value was selected based on heap dumps for normal execution where the queue was
     * holding around 60 items.
     */
    private static final int QUEUE_CAPACITY = 100;

    /**
     * Threshold for pushing received blocks to storage.
     *
     * @implNote Should be lower than {@link SyncHeaderRequestManager#MAX_BLOCK_DIFF}.
     */
    private static final int MAX_STORAGE_DIFF = 200;

    private static final Logger log = AionLoggerFactory.getLogger(LogEnum.SYNC.name());
    private static final Logger survey_log = AionLoggerFactory.getLogger(LogEnum.SURVEY.name());

    private final NetworkStatus networkStatus = new NetworkStatus();

    private SyncHeaderRequestManager syncHeaderRequestManager;

    /**
     * This queue receives data from downloaded blocks. Its capacity is bounded inside the implementation that writes to it.
     */
    private final PriorityBlockingQueue<BlocksWrapper> sortedBlocks = new PriorityBlockingQueue<>();
    // store the hashes of blocks which have been successfully imported
    private final Map<ByteArrayWrapper, Object> importedBlockHashes =
            Collections.synchronizedMap(new LRUMap<>(4096));
    private AionBlockchainImpl chain;
    private IP2pMgr p2pMgr;
    private IEventMgr evtMgr;
    private SyncStats stats;
    private AtomicBoolean start = new AtomicBoolean(true);

    private final ScheduledExecutorService syncExecutors;

    private Thread syncIb;
    private Thread syncSs = null;

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
        syncExecutors = Executors.newScheduledThreadPool(3);

        blockHeaderValidator = new ChainConfiguration().createBlockHeaderValidator();

        long selfBest = chain.getBestBlock().getNumber();
        stats = new SyncStats(selfBest, _showStatus, showStatistics, maxActivePeers);

        syncHeaderRequestManager =  new SyncHeaderRequestManager(log, survey_log);

        syncIb =
            new Thread(
                new TaskImportBlocks(
                    log,
                    survey_log,
                    chain,
                    start,
                    stats,
                    sortedBlocks,
                    importedBlockHashes,
                    syncHeaderRequestManager),
                "sync-ib");
        syncIb.start();

        syncExecutors.scheduleWithFixedDelay(() -> requestStatus(), 0L, DELAY_STATUS_REQUEST, TimeUnit.SECONDS);

        if (_showStatus) {
            syncSs =
                new Thread(
                    new TaskShowStatus(
                        start,
                        INTERVAL_SHOW_STATUS,
                        chain,
                        networkStatus,
                        stats,
                        p2pMgr,
                        showStatistics,
                        AionLoggerFactory.getLogger(LogEnum.P2P.name())),
                    "sync-ss");
            syncSs.start();
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
        if (sortedBlocks.size() >= QUEUE_CAPACITY) {
            log.warn("Downloaded blocks queues are full. Stopped requesting headers.");
        } else {
            syncHeaderRequestManager.sendHeadersRequests(chain.getBestBlock().getNumber(), _selfTd, p2pMgr, stats);
        }
    }

    /**
     * @param _nodeIdHashcode int
     * @param _displayId String
     * @param _headers List validate headers batch and add batch to imported headers
     */
    public void validateAndAddHeaders(int _nodeIdHashcode, String _displayId, List<BlockHeader> _headers) {
        if (_headers == null || _headers.isEmpty()) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "<incoming-headers from={} size={} node={}>",
                    _headers.get(0).getNumber(),
                    _headers.size(),
                    _displayId);
        }

        // filter imported block headers
        List<BlockHeader> filtered = new ArrayList<>();
        BlockHeader prev = null;
        for (BlockHeader current : _headers) {

            // ignore this batch if any invalidated header
            if (!this.blockHeaderValidator.validate(current, log)) {
                log.debug(
                        "<invalid-header num={} hash={}>", current.getNumber(), current.getHash());

                // Print header to allow debugging
                log.debug("Invalid header: {}", current.toString());

                return;
            }

            // break if not consisting
            if (prev != null
                    && (current.getNumber() != (prev.getNumber() + 1)
                            || !Arrays.equals(current.getParentHash(), prev.getHash()))) {
                log.debug(
                        "<inconsistent-block-headers from={}, num={}, prev+1={}, p_hash={}, prev={}>",
                        _displayId,
                        current.getNumber(),
                        prev.getNumber() + 1,
                        ByteUtil.toHexString(current.getParentHash()),
                        ByteUtil.toHexString(prev.getHash()));
                return;
            }

            // add if not cached
            if (!importedBlockHashes.containsKey(ByteArrayWrapper.wrap(current.getHash()))) {
                filtered.add(current);
            }

            prev = current;
        }

        // NOTE: the filtered headers is still continuous
        if (!filtered.isEmpty()) {
            syncExecutors.execute(() -> requestBodies(_nodeIdHashcode, _displayId, filtered));
        }
    }

    /**
     * Requests the bodies associated to the given block headers.
     */
    private void requestBodies(int nodeId, String displayId, final List<BlockHeader> headers) {
        Thread.currentThread().setName("sync-gb");
        long startTime = System.nanoTime();

        // save headers for matching with bodies
        syncHeaderRequestManager.storeHeaders(nodeId, headers);

        // log bodies request before sending the request
        log.debug("<get-bodies from-num={} to-num={} node={}>", headers.get(0).getNumber(), headers.get(headers.size() - 1).getNumber(), displayId);

        p2pMgr.send(nodeId, displayId, new ReqBlocksBodies(headers.stream().map(k -> k.getHash()).collect(Collectors.toList())));
        stats.updateTotalRequestsToPeer(displayId, RequestType.BODIES);
        stats.updateRequestTime(displayId, System.nanoTime(), RequestType.BODIES);

        long duration = System.nanoTime() - startTime;
        survey_log.debug("TaskGetBodies: make request, duration = {} ns.", duration);
    }

    /**
     * @param _nodeIdHashcode int
     * @param _displayId String
     * @param _bodies List<byte[]> Assemble and validate blocks batch and add batch to import queue
     *     from network response blocks bodies
     */
    public void validateAndAddBlocks(
            int _nodeIdHashcode, String _displayId, final List<byte[]> _bodies) {
        if (_bodies == null) return;
        log.debug("<received-bodies size={} node={}>", _bodies.size(), _displayId);

        // the requests are made such that the size varies to better map headers to bodies
        List<BlockHeader> headers = syncHeaderRequestManager.matchHeaders(_nodeIdHashcode, _bodies.size());
        if (headers == null) return;

        // assemble batch
        List<Block> blocks = new ArrayList<>(_bodies.size());
        Iterator<BlockHeader> headerIt = headers.iterator();
        Iterator<byte[]> bodyIt = _bodies.iterator();
        while (headerIt.hasNext() && bodyIt.hasNext()) {
            Block block = BlockUtil.newBlockWithHeaderFromUnsafeSource(headerIt.next(), bodyIt.next());
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
        Thread.currentThread().setName("sync-filter");
        long currentBest = chain.getBestBlock() == null ? 0L : chain.getBestBlock().getNumber();
        boolean isFarInFuture = downloadedBlocks.firstBlockNumber > currentBest + MAX_STORAGE_DIFF;
        // unfortunately the PriorityBlockingQueue does not support a bounded size
        // so the blocks are stored if we reached capacity and they are not directly importable
        boolean isRestrictedCapacity = (sortedBlocks.size() >= QUEUE_CAPACITY) && (downloadedBlocks.firstBlockNumber > currentBest + 1L);

        if (isFarInFuture || isRestrictedCapacity) {
            int stored = chain.storePendingBlockRange(downloadedBlocks.blocks, log);
            stats.updatePeerBlocks(downloadedBlocks.displayId, stored, BlockType.STORED);
        } else {
            sortedBlocks.put(downloadedBlocks);
            log.debug("<import-status: sorted blocks size={}>", sortedBlocks.size());
        }
    }

    public long getNetworkBestBlockNumber() {
        synchronized (this.networkStatus) {
            return this.networkStatus.getTargetBestBlockNumber();
        }
    }

    public synchronized void shutdown() {
        start.set(false);
        shutdownAndAwaitTermination(syncExecutors);

        interruptAndWait(syncIb, 10000);
        interruptAndWait(syncSs, 10000);
    }

    private void interruptAndWait(Thread t, long timeout) {
        if (t != null) {
            log.info("Stopping thread: " + t.getName());
            t.interrupt();
            try {
                t.join(timeout);
            } catch (InterruptedException e) {
                log.warn("Failed to stop " + t.getName());
            }
        }
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
