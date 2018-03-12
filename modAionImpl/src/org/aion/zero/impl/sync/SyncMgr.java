/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * The aion network project leverages useful source code from other
 * open source projects. We greatly appreciate the effort that was
 * invested in these projects and we thank the individual contributors
 * for their work. For provenance information and contributors
 * please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 * Aion foundation.
 */

package org.aion.zero.impl.sync;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.mcf.blockchain.IChainCfg;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.impl.evt.EventConsensus;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.sync.msg.ReqBlocksHeaders;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.aion.mcf.valid.BlockHeaderValidator;

/**
 * @author chris
 */
public final class SyncMgr {

    private static final int INTERVAL_SHOW_STATUS = 10000;

    // interval time get status from active nodes
    private static final int INTERVAL_GET_STATUS = 2000;

    private final static Logger log = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    // default how many blocks forward to sync based on current block number
    private int syncForwardMax = 192;

    private int blocksQueueMax = 2000;

    private AionBlockchainImpl blockchain;
    private IP2pMgr p2pMgr;
    private IEventMgr evtMgr;
    private BlockHeaderValidator blockHeaderValidator;
    private AtomicBoolean start = new AtomicBoolean(true);

    // set as last block number within one batch import when first blockfor
    // imported success as best
    // reset to 0 as any block import result as no parent (side chain)
    private AtomicLong jump;

    private AtomicReference<NetworkStatus> networkStatus = new AtomicReference<>(new NetworkStatus());

    private final Map<ByteArrayWrapper, Object> importedHashes = Collections.synchronizedMap(new LRUMap<>(1024));

    // store headers that has been sent to fetch block bodies
    private final ConcurrentHashMap<Integer, HeadersWrapper> sentHeaders = new ConcurrentHashMap<>();

    // store validated headers from network
    private final BlockingQueue<HeadersWrapper> importedHeaders = new LinkedBlockingQueue<>();

    // store blocks that ready to save to db
    private final BlockingQueue<AionBlock> importedBlocks = new LinkedBlockingQueue<>();

    private ExecutorService workers = Executors.newFixedThreadPool(5);

    private static final class AionSyncMgrHolder {
        static final SyncMgr INSTANCE = new SyncMgr();
    }

    public static SyncMgr inst() {
        return AionSyncMgrHolder.INSTANCE;
    }

    // Attation:
    // update best block is handler function from p2p thread pool. even the
    // blocknumber and blockhash is atomic, but still need sync to prevent
    // blocknumber link to wrong block hash.
    public synchronized void updateNetworkBestBlock(String _displayId, long _nodeBestBlockNumber,
            final byte[] _nodeBestBlockHash, final byte[] _totalDiff) {
        long selfBestBlockNumber = this.blockchain.getBestBlock().getNumber();
        BigInteger totalDiff = new BigInteger(1, _totalDiff);
        if (_nodeBestBlockNumber > this.networkStatus.get().blockNumber) {
            if (networkStatus.get().totalDiff.compareTo(totalDiff) < 0) {
                networkStatus.get().blockNumber = _nodeBestBlockNumber;
                networkStatus.get().blockHash = _nodeBestBlockHash;
                networkStatus.get().totalDiff = totalDiff;
                if (_nodeBestBlockNumber > this.blockchain.getBestBlock().getNumber())
                    this.getHeaders();

            } else {
                if (log.isDebugEnabled()) {
                    log.debug(
                            "<network-best-block-diff-fail remote-id={} num={} diff={} best-diff={} self-num={}  known-network-num={} send-on-sync-done>",
                            _displayId, _nodeBestBlockNumber, totalDiff, networkStatus.get().totalDiff,
                            selfBestBlockNumber, this.networkStatus.get().blockNumber);
                }
            }
        }

        if (this.networkStatus.get().blockNumber <= selfBestBlockNumber){
            this.evtMgr.newEvent(new EventConsensus(EventConsensus.CALLBACK.ON_SYNC_DONE));
            if (log.isDebugEnabled()) {
                log.debug(
                        "<network-best-block-updated remote-num={} self-num={} known-network-num={} send-on-sync-done>",
                        _nodeBestBlockNumber, selfBestBlockNumber, this.networkStatus.get().blockNumber);
            }
        } else {
            if (log.isDebugEnabled())
                log.debug(
                        "<network-best-block-updated remote-num={} self-num={} known-network-num={} continue-on-sync>",
                        _nodeBestBlockNumber, selfBestBlockNumber, this.networkStatus.get().blockNumber);
        }
    }

    public void init(final IP2pMgr _p2pMgr, final IEventMgr _evtMgr, final int _syncForwardMax,
            final int _blocksQueueMax, final boolean _showStatus) {
        this.p2pMgr = _p2pMgr;
        this.blockchain = AionBlockchainImpl.inst();
        this.evtMgr = _evtMgr;
        this.syncForwardMax = _syncForwardMax;
        this.blocksQueueMax = _blocksQueueMax;
        this.blockHeaderValidator = (new ChainConfiguration()).createBlockHeaderValidator();
        this.jump = new AtomicLong(this.blockchain.getBestBlock().getNumber());

        workers.submit(new TaskGetStatus(this.start, INTERVAL_GET_STATUS, this.p2pMgr, log));
        workers.submit(new TaskGetBodies(this.p2pMgr, this.start, this.importedHeaders, this.sentHeaders));
        workers.submit(new TaskImportBlocks(this, this.blockchain, this.start, this.jump, this.importedBlocks, importedHashes, log));
        if (_showStatus)
            workers.submit(new TaskShowStatus(this.start, INTERVAL_SHOW_STATUS, this.blockchain, this.networkStatus, log));

        setupEventHandler();
    }

    private void setupEventHandler() {
        List<IEvent> events = new ArrayList<>();
        events.add(new EventConsensus(EventConsensus.CALLBACK.ON_SYNC_DONE));
        this.evtMgr.registerEvent(events);
    }

    void getHeaders(){
        workers.submit(new TaskGetHeaders(p2pMgr, blockchain, networkStatus, jump, this.syncForwardMax));
    }

    /**
     *
     * @param _nodeIdHashcode
     *            int
     * @param _displayId
     *            String
     * @param _headers
     *            List validate headers batch and add batch to imported headers
     */
    public void validateAndAddHeaders(int _nodeIdHashcode, String _displayId, final List<A0BlockHeader> _headers) {

        if (_headers == null || _headers.isEmpty()) {
            return;
        }

        _headers.sort((h1, h2) -> (int) (h1.getNumber() - h2.getNumber()));
        Iterator<A0BlockHeader> it = _headers.iterator();
        while (it.hasNext()) {

            A0BlockHeader h = it.next();
            boolean valid = this.blockHeaderValidator.validate(h);
            boolean imported = this.importedHashes.containsKey(new ByteArrayWrapper(h.getHash()));

            // drop all batch
            if (!valid) {
                return;
            }
            if (imported) {
                it.remove();
            }
        }

        importedHeaders.add(new HeadersWrapper(_nodeIdHashcode, _headers));

        if (log.isDebugEnabled()) {
            log.debug("<incoming-headers size={} from-num={} to-num={} from-node={}>", _headers.size(),
                    _headers.get(0).getNumber(), _headers.get(_headers.size() - 1).getNumber(), _displayId);
        }
    }

    /**
     * @param _nodeIdHashcode
     *            int
     * @param _displayId
     *            String
     * @param _bodies
     *            List<byte[]> Assemble and validate blocks batch and add batch
     *            to import queue from network response blocks bodies
     */
    public void validateAndAddBlocks(int _nodeIdHashcode, String _displayId, final List<byte[]> _bodies) {

        if (importedBlocks.size() > blocksQueueMax)
            return;

        HeadersWrapper hw = this.sentHeaders.remove(_nodeIdHashcode);
        if (hw == null || _bodies == null)
            return;

        // assemble batch
        List<A0BlockHeader> headers = hw.getHeaders();
        List<AionBlock> blocks = new ArrayList<>(_bodies.size());
        Iterator<A0BlockHeader> headerIt = headers.iterator();
        Iterator<byte[]> bodyIt = _bodies.iterator();
        while (headerIt.hasNext() && bodyIt.hasNext()) {
            AionBlock block = AionBlock.createBlockFromNetwork(headerIt.next(), bodyIt.next());
            if (block == null) {
                log.error("<assemble-and-validate-blocks from-node={}>", _displayId);
                break;
            } else
                blocks.add(block);
        }

        int m = blocks.size();
        if (m == 0)
            return;

        // sort this batch
        if (m > 1)
            blocks.sort((b1, b2) -> b1.getNumber() > b2.getNumber() ? 1 : 0);
        if (log.isDebugEnabled()) {
            log.debug("<incoming-bodies size={} from-num={} to-num={} from-node={}>", m, blocks.get(0).getNumber(),
                    blocks.get(blocks.size() - 1).getNumber(), _displayId);
        }

        // add batch
        importedBlocks.addAll(blocks);
    }

    public long getNetworkBestBlockNumber() {
        return this.networkStatus.get().blockNumber;
    }

    public void shutdown() {
        start.set(false);
        workers.shutdown();
    }
}
