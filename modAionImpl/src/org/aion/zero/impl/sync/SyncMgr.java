/*******************************************************************************
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
 *     
 ******************************************************************************/

package org.aion.zero.impl.sync;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.Hex;
import org.aion.mcf.blockchain.IChainCfg;
import org.aion.mcf.core.ImportResult;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.impl.evt.EventConsensus;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.sync.msg.BroadcastNewBlock;
import org.aion.zero.impl.sync.msg.ReqBlocksBodies;
import org.aion.zero.impl.sync.msg.ReqBlocksHeaders;
import org.aion.zero.impl.sync.msg.ReqStatus;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.aion.mcf.valid.BlockHeaderValidator;

/**
 * @author chris
 */
public final class SyncMgr {

    private static final long SYNC_HEADER_FETCH_INTERVAL = 4000;
    private final static Logger LOG = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    private boolean showStatus = false;
    private int syncBackwardMax = 128;
    private int syncForwardMax = 192;
    private int blocksQueueMax = 2000;
    private AtomicBoolean start = new AtomicBoolean(true);
    //private AtomicBoolean newBlockUpdated = new AtomicBoolean(false);

    private AionBlockchainImpl blockchain;
    private IP2pMgr p2pMgr;
    private IEventMgr evtMgr;
    private BlockHeaderValidator blockHeaderValidator;

    private AtomicInteger selectedNodeIdHashcode = new AtomicInteger(0);
    private AtomicLong longestHeaders = new AtomicLong(0);
    private AtomicLong networkBestBlockNumber = new AtomicLong(0);
    private AtomicReference<byte[]> networkBestBlockHash = new AtomicReference<>(new byte[0]);

    private static final int GET_STATUS_SLEEP = 1;

    private ConcurrentHashMap<Integer, SequentialHeaders<A0BlockHeader>> importedHeaders = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, List<A0BlockHeader>> sentHeaders = new ConcurrentHashMap<>();
    private final BlockingQueue<AionBlock> importedBlocksQueue = new LinkedBlockingQueue<>();

    private LRUMap<ByteArrayWrapper, Object> importedBlocksCache = new LRUMap<>(1024);

    /**
     * Threads
     */
    private Thread getHeadersThread;
    private Thread getBodiesThread;
    private Thread importBlocksThread;
    private ScheduledThreadPoolExecutor scheduledWorkers;

    private static final class AionSyncMgrHolder {
        static final SyncMgr INSTANCE = new SyncMgr();
    }

    public static SyncMgr inst() {
        return AionSyncMgrHolder.INSTANCE;
    }

    public void updateNetworkBestBlock(final long _nodeBestBlockNumber, final byte[] _nodeBestBlockHash) {
        long selfBestBlockNumber = this.blockchain.getBestBlock().getNumber();

        // switch to new selected node event on equal highest block
        if (_nodeBestBlockNumber > this.networkBestBlockNumber.get()) {
            this.networkBestBlockNumber.set(_nodeBestBlockNumber);
            this.networkBestBlockHash.set(_nodeBestBlockHash);

            // fire the init push
            // if(headersLatch != null)
            // headersLatch.countDown();
            //newBlockUpdated.set(true);

        }

        if (this.networkBestBlockNumber.get() <= selfBestBlockNumber) {
            this.evtMgr.newEvent(new EventConsensus(EventConsensus.CALLBACK.ON_SYNC_DONE));
            if (LOG.isDebugEnabled()) {
                LOG.debug("<network-best-block-updated num={} self-num={} send-on-sync-done>", _nodeBestBlockNumber,
                        selfBestBlockNumber);
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("<network-best-block-updated num={} self-num={} continue-on-sync>", _nodeBestBlockNumber,
                        selfBestBlockNumber);
            }
        }
    }

    private void updateSentHeaders(int _nodeIdHashcode, final List<A0BlockHeader> _receivedBlocksHeaders) {
        if (_receivedBlocksHeaders != null && _receivedBlocksHeaders.size() > 0)
            this.sentHeaders.put(_nodeIdHashcode, _receivedBlocksHeaders);
    }

    public void clearSentHeaders(int _nodeIdHashcode) {
        this.sentHeaders.remove(_nodeIdHashcode);
    }

    public List<A0BlockHeader> getSentHeaders(int _nodeIdHashcode) {
        return this.sentHeaders.get(_nodeIdHashcode);
    }

    public void init(final IP2pMgr _p2pMgr, final IEventMgr _evtMgr, final int _syncForwardMax,
            final int _blocksQueueMax, final boolean _showStatus) {
        showStatus = _showStatus;
        this.p2pMgr = _p2pMgr;
        this.blockchain = AionBlockchainImpl.inst();
        this.evtMgr = _evtMgr;
        this.syncForwardMax = _syncForwardMax;
        this.blocksQueueMax = _blocksQueueMax;
        IChainCfg chainCfg = new ChainConfiguration();
        this.blockHeaderValidator = chainCfg.createBlockHeaderValidator();

        setupEventHandler();

        getHeadersThread = new Thread(this::processGetHeaders, "sync-headers");
        getHeadersThread.start();
        getBodiesThread = new Thread(this::processGetBlocks, "sync-blocks");
        getBodiesThread.start();
        importBlocksThread = new Thread(this::processImportBlocks, "sync-import");
        importBlocksThread.start();
        scheduledWorkers = new ScheduledThreadPoolExecutor(1);
        scheduledWorkers.allowCoreThreadTimeOut(true);

        if (showStatus)
            scheduledWorkers.scheduleWithFixedDelay(() -> {
                Thread.currentThread().setName("sync-status");
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                            "<status self-best={} network-best-blk-number={} network-best-blk-hash={} longest-headers={} imported-headers=[{}] sent-headers=[{}] blocks-queue={}>",
                            blockchain.getBestBlock().getNumber(), networkBestBlockNumber.get(),
                            Hex.toHexString(networkBestBlockHash.get()).substring(0, 6), longestHeaders.get(),
                            importedHeaders.entrySet().stream()
                                    .map((entry) -> entry.getKey() + "-" + entry.getValue().size())
                                    .collect(Collectors.joining(",")),
                            sentHeaders.entrySet().stream()
                                    .map((entry) -> entry.getKey() + "-" + entry.getValue().size())
                                    .collect(Collectors.joining(",")),
                            importedBlocksQueue.size());
                }
            }, 0, 5, TimeUnit.SECONDS);
        scheduledWorkers.scheduleWithFixedDelay(() -> {
            INode node = p2pMgr.getRandom();
            if (node != null)
                p2pMgr.send(node.getIdHash(), new ReqStatus());
        }, 0, GET_STATUS_SLEEP, TimeUnit.SECONDS);
    }

    /**
     * Oct 12, 2017 jay void
     */
    private void setupEventHandler() {
        List<IEvent> evts = new ArrayList<>();
        evts.add(new EventConsensus(EventConsensus.CALLBACK.ON_SYNC_DONE));
        this.evtMgr.registerEvent(evts);
    }

    @SuppressWarnings("unchecked")
    public void validateAndAddHeaders(int _nodeIdHashcode, final List<A0BlockHeader> _headers) {

        if (_headers == null || _headers.isEmpty())
            return;

        boolean headersValid = true;
        for (A0BlockHeader _header : _headers) {
            if (!this.blockHeaderValidator.validate(_header)) {
                headersValid = false;
                break;
            }
        }
        if (!headersValid)
            return;
        SequentialHeaders<A0BlockHeader> headers = this.importedHeaders.get(_nodeIdHashcode);
        if (headers == null)
            headers = new SequentialHeaders<>();

        headers.addAll(_headers);
        importedHeaders.putIfAbsent(_nodeIdHashcode, headers);
        int size = headers.size();
        if (size > this.longestHeaders.get()) {
            this.longestHeaders.set(size);
            this.selectedNodeIdHashcode.set(_nodeIdHashcode);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("<incoming-headers size={} from={} to={} imported-headers={}>", _headers.size(),
                    _headers.get(0).getNumber(), _headers.get(_headers.size() - 1).getNumber(), headers.size());
        }
    }

    // TODO: remove ifNewBlockBroadcast if block propagation stable
    public void validateAndAddBlocks(int _nodeIdHashcode, final List<AionBlock> _blocks, boolean ifNewBlockBroadcast) {
        if (_blocks == null || _blocks.isEmpty())
            return;
        int m = _blocks.size();
        /*
         * sort if sync batch
         */
        if (m > 1)
            _blocks.sort((b1, b2) -> b1.getNumber() > b2.getNumber() ? 1 : 0);
        if (LOG.isDebugEnabled()) {
            LOG.debug("<validate-incoming-blocks size={} from={} to={}>", _blocks.size(), _blocks.get(0).getNumber(),
                    _blocks.get(_blocks.size() - 1).getNumber());
        }

        for (AionBlock b : _blocks) {
            importedBlocksQueue.add(b);
        }

    }

    private void processGetHeaders() {
        while (start.get()) {

            long ts = System.currentTimeMillis();
            while (((System.currentTimeMillis() - ts) < SYNC_HEADER_FETCH_INTERVAL)) {
                try {
                    Thread.sleep(2000);
                } catch (Exception e) {
                }
            }

            AionBlock selfBlock = this.blockchain.getBestBlock();
            long selfBest = selfBlock.getNumber();

            INode node = p2pMgr.getRandom();

            if (node != null) {
                long remoteBest = node.getBestBlockNumber();
                long diff = remoteBest - selfBest;
                if (diff > 0) {
                    long from = Math.max(1, selfBest - syncBackwardMax);
                    long to = selfBest + (diff > this.syncForwardMax ? this.syncForwardMax : diff);
                    this.p2pMgr.send(node.getIdHash(), new ReqBlocksHeaders(from, (int) (to - from) + 1));
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("<send-get-headers from-node={} from={} to={} self-best={} remote-best={}>",
                                node.getIdHash(), from, to, selfBest, remoteBest);
                    }
                }
            }
        }
    }

    private void processGetBlocks() {
        while (start.get()) {
            List<A0BlockHeader> headers = importedHeaders.get(selectedNodeIdHashcode.get());
            if (importedBlocksQueue.size() < blocksQueueMax && headers != null) {
                List<byte[]> blockHashes = new ArrayList<>();
                for (A0BlockHeader header : headers) {
                    blockHashes.add(header.getHash());
                }
                clearSentHeaders(selectedNodeIdHashcode.get());
                updateSentHeaders(selectedNodeIdHashcode.get(), headers);
                if (headers.size() > 0) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("<req-blocks from={} take={}>", headers.get(0).getNumber(), blockHashes.size());
                    }
                    importedHeaders.clear();
                    this.p2pMgr.send(selectedNodeIdHashcode.get(), new ReqBlocksBodies(blockHashes));
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
            }
        }
    }

    private void processImportBlocks() {
        while (start.get()) {
            try {
                AionBlock b = importedBlocksQueue.take();

                // TODO: is it possible the import result of a block may change
                // over time?
                if (importedBlocksCache.containsKey(ByteArrayWrapper.wrap(b.getHash()))) {
                    continue;
                }

                if (LOG.isDebugEnabled()) {

                    LOG.debug("<try-import-block num={} hash={} parent={}>", b.getNumber(), b.getShortHash(),
                            Hex.toHexString(b.getParentHash()).substring(0, 6));
                }

                ImportResult importResult = this.blockchain.tryToConnect(b);
                switch (importResult) {
                case IMPORTED_BEST:
                    if (LOG.isInfoEnabled()) {
                        LOG.info("<import-best num={} hash={} txs={}>", b.getNumber(), b.getShortHash(),
                                b.getTransactionsList().size());
                    }
                    importedBlocksCache.put(ByteArrayWrapper.wrap(b.getHash()), null);
                    break;
                case IMPORTED_NOT_BEST:
                    if (LOG.isInfoEnabled()) {
                        LOG.info("<import-not-best num={} hash={} txs={}>", b.getNumber(), b.getShortHash(),
                                b.getTransactionsList().size());
                    }
                    importedBlocksCache.put(ByteArrayWrapper.wrap(b.getHash()), null);
                    break;
                case NO_PARENT:
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("<import-unsuccess err=no-parent num={} hash={}>", b.getNumber(),
                                b.getShortHash());
                    }
                    break;
                case INVALID_BLOCK:
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("<import-unsuccess err=invalid-block num={} hash={} txs={}>", b.getNumber(),
                                b.getShortHash(), b.getTransactionsList().size());
                    }
                    break;
                case EXIST:
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("<import-unsuccess err=block-exit num={} hash={} txs={}>", b.getNumber(),
                                b.getShortHash(), b.getTransactionsList().size());
                    }
                    importedBlocksCache.put(ByteArrayWrapper.wrap(b.getHash()), null);
                    break;
                default:
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("<import-res-unknown>");
                    }
                    break;
                }
            } catch (InterruptedException e) {
            }
        }
    }

    public void shutdown() {
        scheduledWorkers.shutdown();
        start.set(false);
    }

    public long getNetworkBestBlockNumber() {
        return this.networkBestBlockNumber.get();
    }
}
