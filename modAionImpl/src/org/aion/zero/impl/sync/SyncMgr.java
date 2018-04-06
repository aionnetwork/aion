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
 *
 */

package org.aion.zero.impl.sync;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.mcf.valid.BlockHeaderValidator;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.impl.evt.EventConsensus;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;

/**
 * @author chris
 */
public final class SyncMgr {

    // interval - show status
    private static final int INTERVAL_SHOW_STATUS = 10000;

    private final static Logger log = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    private int syncBackwardMin;
    private int syncBackwardMax;

    private int syncRequestMax;
    private int syncResponseMax;

    private int blocksQueueMax; // block header wrappers

    private AionBlockchainImpl chain;

    private IP2pMgr p2pMgr;

    private IEventMgr evtMgr;

    private AtomicBoolean start = new AtomicBoolean(true);

    // set as last block number within one batch import when first block for
    // imported success as best
    // reset to 0 as any block import result as no parent (side chain)
    // private AtomicLong jump;

    private final NetworkStatus networkStatus = new NetworkStatus();

    // store headers that has been sent to fetch block bodies
    private final ConcurrentHashMap<Integer, HeadersWrapper> sentHeaders = new ConcurrentHashMap<>();

    // store validated headers from network
    private final BlockingQueue<HeadersWrapper> importedHeaders = new LinkedBlockingQueue<>();

    // store blocks that ready to save to db
    private final BlockingQueue<BlocksWrapper> importedBlocks = new LinkedBlockingQueue<>();

    //private ExecutorService workers = Executors.newFixedThreadPool(5);
    private ExecutorService workers = Executors.newCachedThreadPool(new ThreadFactory() {

        private AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "sync-gh-" + cnt.incrementAndGet());
        }
    });

    private Map<ByteArrayWrapper, Object> importedBlockHashes = Collections.synchronizedMap(new LRUMap<>(4096));

    private BlockHeaderValidator<A0BlockHeader> blockHeaderValidator;

    private static final class AionSyncMgrHolder {
        static final SyncMgr INSTANCE = new SyncMgr();
    }

    public static SyncMgr inst() {
        return AionSyncMgrHolder.INSTANCE;
    }

    /**
     *
     * @param _displayId String
     * @param _remoteBestBlockNumber long
     * @param _remoteBestBlockHash byte[]
     * @param _remoteTotalDiff BigInteger
     * null check for _remoteBestBlockHash && _remoteTotalDiff
     * implemented on ResStatusHandler before pass through
     *
     */
    public void updateNetworkStatus(
        String _displayId,
        long _remoteBestBlockNumber,
        final byte[] _remoteBestBlockHash,
        BigInteger _remoteTotalDiff) {

        // self
        BigInteger selfTd = this.chain.getTotalDifficulty();

        // trigger send headers routine immediately
        if(_remoteTotalDiff.compareTo(selfTd) > 0) {
            this.getHeaders(selfTd);

            // update network best status
            synchronized (this.networkStatus){
                BigInteger networkTd = this.networkStatus.getTargetTotalDiff();
                if(_remoteTotalDiff.compareTo(networkTd) > 0){
                    String remoteBestBlockHash = Hex.toHexString(_remoteBestBlockHash);

                    log.debug(
                        "<network-status-updated on-sync id={}->{} td={}->{} bn={}->{} bh={}->{}>",
                            this.networkStatus.getTargetDisplayId(), _displayId,
                            this.networkStatus.getTargetTotalDiff().toString(10), _remoteTotalDiff.toString(10),
                            this.networkStatus.getTargetBestBlockNumber(), _remoteBestBlockNumber,
                            this.networkStatus.getTargetBestBlockHash(), remoteBestBlockHash
                    );

                    this.networkStatus.update(
                            _displayId,
                            _remoteTotalDiff,
                            _remoteBestBlockNumber,
                            remoteBestBlockHash
                    );
                }
            }
        }
//        else {
//
//            //TODO: can be faked to stop miner
//            this.evtMgr.newEvent(new EventConsensus(EventConsensus.CALLBACK.ON_SYNC_DONE));
//            if (log.isDebugEnabled()) {
//                log.debug(
//                        "<network-status off-sync td={}/{} bn={}/{} bh={}/{}>",
//                        selfTd.toString(10), this.networkStatus.getTargetTotalDiff().toString(10),
//                        _remoteBestBlockNumber, this.networkStatus.getTargetBestBlockNumber(),
//                        Hex.toHexString(selfBest.getHash()), this.networkStatus.getTargetBestBlockHash()
//                );
//            }
//        }
    }

    public void init(final IP2pMgr _p2pMgr, final IEventMgr _evtMgr, final int _syncBackwardMin, final int _syncBackwardMax,
                     final int _syncRequestMax, final int _syncResponseMax, final int _blocksQueueMax,
                     final boolean _showStatus, final boolean _printReport, final String _reportFolder) {
        this.p2pMgr = _p2pMgr;
        this.chain = AionBlockchainImpl.inst();
        this.evtMgr = _evtMgr;

        this.syncBackwardMin = _syncBackwardMin;
        this.syncBackwardMax = _syncBackwardMax;

        this.syncRequestMax = _syncRequestMax;
        this.syncResponseMax = _syncResponseMax;

        this.blocksQueueMax = _blocksQueueMax;

        this.blockHeaderValidator = new ChainConfiguration().createBlockHeaderValidator();

        long selfBest = this.chain.getBestBlock().getNumber();
        SyncStatics statics = new SyncStatics(selfBest);

        new Thread(new TaskGetBodies(this.p2pMgr, this.start, this.importedHeaders, this.sentHeaders, log), "sync-gb").start();
        new Thread(new TaskImportBlocks(this.p2pMgr, this.chain, this.start, this.importedBlocks, statics, log, importedBlockHashes), "sync-ib").start();
        new Thread(new TaskGetStatus(this.start, this.p2pMgr, log), "sync-gs").start();
        if(_showStatus)
            new Thread(new TaskShowStatus(this.start, INTERVAL_SHOW_STATUS, this.chain, this.networkStatus, statics, log, _printReport, _reportFolder), "sync-ss").start();

        setupEventHandler();
    }

    private void setupEventHandler() {
        List<IEvent> events = new ArrayList<>();
        events.add(new EventConsensus(EventConsensus.CALLBACK.ON_SYNC_DONE));
        this.evtMgr.registerEvent(events);
    }

    private void getHeaders(BigInteger _selfTd){
        if (importedBlocks.size() > blocksQueueMax) {
            log.debug("Imported blocks queue is full. Stop requesting headers");
            return;
        }

        workers.submit(new TaskGetHeaders(p2pMgr, chain.getBestBlock().getNumber(), _selfTd,
                syncBackwardMin, syncBackwardMax, syncRequestMax,  log));
    }

    /**
     *
     * @param _nodeIdHashcode int
     * @param _displayId String
     * @param _headers List validate headers batch and add batch to imported headers
     */
    public void validateAndAddHeaders(int _nodeIdHashcode, String _displayId, List<A0BlockHeader> _headers) {
        if (_headers == null || _headers.isEmpty()) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "<incoming-headers from-num={} to-num={} node={}>",
                    _headers.get(0).getNumber(),
                    _headers.get(_headers.size() - 1).getNumber(),
                    _displayId
            );
        }

        // filter imported block headers
        List<A0BlockHeader> filtered = new ArrayList<>();
        A0BlockHeader prev = null;
        for(A0BlockHeader current : _headers){

            // ignore this batch if any invalidated header
            if(!this.blockHeaderValidator.validate(current, log)) {
                log.debug("<invalid-header num={} hash={}>", current.getNumber(), current.getHash());

                // Print header to allow debugging
                log.debug("Invalid header: {}", current.toString());

                return;
            }

            // break if not consisting
            if(prev != null && (current.getNumber() != (prev.getNumber() + 1) || !Arrays.equals(current.getParentHash(), prev.getHash()))) {
                log.debug("<inconsistent-block-headers from={}, num={}, prev+1={}, p_hash={}, prev={}>",
                        _displayId,
                        current.getNumber(),
                        prev.getNumber() + 1,
                        ByteUtil.toHexString(current.getParentHash()),
                        ByteUtil.toHexString(prev.getHash()));
                return;
            }

            // add if not cached
            if(!importedBlockHashes.containsKey(ByteArrayWrapper.wrap(current.getHash())))
                filtered.add(current);

            prev = current;
        }

        // _headers.sort((h1, h2) -> (int) (h1.getNumber() - h2.getNumber()));
        if(filtered.size() > 0)
            importedHeaders.add(new HeadersWrapper(_nodeIdHashcode, _displayId, filtered));
    }

    /**
     * @param _nodeIdHashcode int
     * @param _displayId String
     * @param _bodies List<byte[]>
     * Assemble and validate blocks batch and add batch
     * to import queue from network response blocks bodies
     */
    public void validateAndAddBlocks(int _nodeIdHashcode, String _displayId, final List<byte[]> _bodies) {

        if (importedBlocks.size() > blocksQueueMax) {
            log.debug("Imported blocks queue is full. Stop validating incoming bodies");
            return;
        }

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
                log.error("<assemble-and-validate-blocks node={}>", _displayId);
                break;
            } else
                blocks.add(block);
        }

        int m = blocks.size();
        if (m == 0)
            return;

        if (log.isDebugEnabled()) {
            log.debug("<incoming-bodies from-num={} to-num={} node={}>",
                    blocks.get(0).getNumber(),
                    blocks.get(blocks.size() - 1).getNumber(),
                    _displayId);
        }

        // add batch
        importedBlocks.add(new BlocksWrapper(_nodeIdHashcode, _displayId, blocks));
    }
    
    public long getNetworkBestBlockNumber() {
        synchronized (this.networkStatus){
            return this.networkStatus.getTargetBestBlockNumber();
        }
    }

    public synchronized void shutdown() {
        start.set(false);
        workers.shutdown();
    }
}
