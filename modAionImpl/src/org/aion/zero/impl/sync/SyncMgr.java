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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.aion.base.util.Hex;
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

    // interval - get status from active nodes
    private static final int INTERVAL_GET_STATUS = 5000;

    private final static Logger log = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    // default how many blocks forward to sync based on current block number
    private int syncForwardMax = 192;

    private int blocksQueueMax = 2000;

    private AionBlockchainImpl chain;

    private IP2pMgr p2pMgr;

    private IEventMgr evtMgr;

    private AtomicBoolean start = new AtomicBoolean(true);

    // set as last block number within one batch import when first block for
    // imported success as best
    // reset to 0 as any block import result as no parent (side chain)
    private AtomicLong jump;

    private NetworkStatus networkStatus = new NetworkStatus();

    // store headers that has been sent to fetch block bodies
    private final ConcurrentHashMap<Integer, HeadersWrapper> sentHeaders = new ConcurrentHashMap<>();

    // store validated headers from network
    private final BlockingQueue<HeadersWrapper> importedHeaders = new LinkedBlockingQueue<>();

    // store blocks that ready to save to db
    private final BlockingQueue<List<AionBlock>> importedBlocks = new LinkedBlockingQueue<>();

    //private ExecutorService workers = Executors.newFixedThreadPool(5);
    private ExecutorService workers = Executors.newCachedThreadPool();

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
        AionBlock selfBest = new AionBlock(this.chain.getBestBlock());
        BigInteger selfTd = this.chain.getTotalDifficulty();

        // trigger send headers routine immediately
        if(_remoteTotalDiff.compareTo(selfTd) > 0) {
            this.getHeaders(selfTd);

            // update network best status
            synchronized (this.networkStatus){
                if(this.networkStatus.getTargetTotalDiff().compareTo(_remoteTotalDiff) > 0){

                    String remoteBestBlockHash = Hex.toHexString(_remoteBestBlockHash);

                    if (log.isDebugEnabled()) {
                        log.debug(
                            "<network-status-updated on-sync id={}->{} td={}->{} bn={}->{} bh={}->{}>",
                                this.networkStatus.getTargetDisplayId(), _displayId,
                                this.networkStatus.getTargetTotalDiff().toString(10), _remoteTotalDiff.toString(10),
                                this.networkStatus.getTargetBestBlockNumber(), _remoteBestBlockNumber,
                                this.networkStatus.getTargetBestBlockHash(), remoteBestBlockHash
                        );
                    }
                    this.networkStatus.update(
                            _displayId,
                            _remoteTotalDiff,
                            _remoteBestBlockNumber,
                            remoteBestBlockHash
                    );

                }
            }
        } else {

            //TODO: can be faked to stop miner
            this.evtMgr.newEvent(new EventConsensus(EventConsensus.CALLBACK.ON_SYNC_DONE));
            if (log.isDebugEnabled()) {
                log.debug(
                        "<network-status off-sync td={}/{} bn={}/{} bh={}/{}>",
                        selfTd.toString(10), this.networkStatus.getTargetTotalDiff().toString(10),
                        _remoteBestBlockNumber, this.networkStatus.getTargetBestBlockNumber(),
                        Hex.toHexString(selfBest.getHash()), this.networkStatus.getTargetBestBlockHash()
                );
            }
        }
    }

    public void init(final IP2pMgr _p2pMgr, final IEventMgr _evtMgr, final int _syncForwardMax,
            final int _blocksQueueMax, final boolean _showStatus) {
        this.p2pMgr = _p2pMgr;
        this.chain = AionBlockchainImpl.inst();
        this.evtMgr = _evtMgr;
        this.syncForwardMax = _syncForwardMax;
        this.blocksQueueMax = _blocksQueueMax;

        long selfBest = this.chain.getBestBlock().getNumber();
        this.jump = new AtomicLong( selfBest + 1);
        SyncStatics statics = new SyncStatics(selfBest);

        new Thread(new TaskGetBodies(this.p2pMgr, this.start, this.importedHeaders, this.sentHeaders), "sync-gh").start();
        new Thread(new TaskImportBlocks(this, this.chain, this.start, this.jump, this.importedBlocks, statics, log), "sync-ib").start();
        new Thread(new TaskGetStatus(this.start, INTERVAL_GET_STATUS, this.p2pMgr, log), "sync-gs").start();
        if(_showStatus)
            new Thread(new TaskShowStatus(this.start, INTERVAL_SHOW_STATUS, this.chain, this.jump,  this.networkStatus, statics, log), "sync-ss").start();

        setupEventHandler();
    }

    private void setupEventHandler() {
        List<IEvent> events = new ArrayList<>();
        events.add(new EventConsensus(EventConsensus.CALLBACK.ON_SYNC_DONE));
        this.evtMgr.registerEvent(events);
    }

    void getHeaders(BigInteger _selfTd){
        workers.submit(new TaskGetHeaders(p2pMgr, this.syncForwardMax, jump.get(), _selfTd));
    }

    /**
     *
     * @param _nodeIdHashcode int
     * @param _displayId String
     * @param _headers List validate headers batch and add batch to imported headers
     */
    public void validateAndAddHeaders(int _nodeIdHashcode, String _displayId, final List<A0BlockHeader> _headers) {
        if (_headers == null || _headers.isEmpty()) {
            return;
        }
        _headers.sort((h1, h2) -> (int) (h1.getNumber() - h2.getNumber()));
        importedHeaders.add(new HeadersWrapper(_nodeIdHashcode, _headers));
        if (log.isDebugEnabled()) {
            log.debug("<incoming-headers size={} from-num={} to-num={} from-node={}>", _headers.size(),
                    _headers.get(0).getNumber(), _headers.get(_headers.size() - 1).getNumber(), _displayId);
        }
    }

    /**
     * @param _nodeIdHashcode int
     * @param _displayId String
     * @param _bodies List<byte[]>
     * Assemble and validate blocks batch and add batch
     * to import queue from network response blocks bodies
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

        // add batch
        importedBlocks.add(blocks);

        if (log.isDebugEnabled()) {
            log.debug("<incoming-bodies size={} from-num={} to-num={} from-node={}>", m, blocks.get(0).getNumber(),
                    blocks.get(blocks.size() - 1).getNumber(), _displayId);
        }
    }
    
    public long getNetworkBestBlockNumber() {
        synchronized (this.networkStatus){
            return this.networkStatus.getTargetBestBlockNumber();
        }
    }

    public void shutdown() {
        start.set(false);
        workers.shutdown();
    }
}
