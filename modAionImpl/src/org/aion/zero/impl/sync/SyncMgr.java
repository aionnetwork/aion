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

import org.aion.base.util.ByteArrayWrapper;
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

    // default how many blocks forward to sync based on current block number
    private int syncForwardMax = 32;

    private final static int syncBackwordMax = 16;

    private int blocksQueueMax = 64;

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
    private ExecutorService workers = Executors.newCachedThreadPool();

    private Map<ByteArrayWrapper, Object> importedBlockHashes = Collections.synchronizedMap(new LRUMap<>(4096));

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

    public void init(final IP2pMgr _p2pMgr, final IEventMgr _evtMgr, final int _syncForwardMax,
            final int _blocksQueueMax, final boolean _showStatus, final boolean _printReport, final String _reportFolder) {
        this.p2pMgr = _p2pMgr;
        this.chain = AionBlockchainImpl.inst();
        this.evtMgr = _evtMgr;
        this.syncForwardMax = _syncForwardMax;
        this.blocksQueueMax = _blocksQueueMax;

        long selfBest = this.chain.getBestBlock().getNumber();
        SyncStatics statics = new SyncStatics(selfBest);

        new Thread(new TaskGetBodies(this.p2pMgr, this.start, this.importedHeaders, this.sentHeaders), "sync-gh").start();
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
        workers.submit(new TaskGetHeaders(p2pMgr, this.syncForwardMax, Math.max(1, this.chain.getBestBlock().getNumber() - syncBackwordMax), _selfTd));
    }

    //    void getHeaders(int _nodeId, String _displayId, long _fromBlock){
    //        ReqBlocksHeaders rbh = new ReqBlocksHeaders(_fromBlock, this.syncForwardMax);
    //        System.out.println(
    //                "try-request headers from remote-node=" + _displayId +
    //                        " remote-td=" + node.getTotalDifficulty().toString(10) +
    //                        " remote-bn=" + node.getBestBlockNumber() +
    //                        " jump=" + jump +
    //                        " from-block=" + rbh.getFromBlock() +
    //                        " take=" + rbh.getTake()
    //        );
    //        p2pMgr.send(_nodeId, );
    //    }

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

        // filter imported block headers
        BlockHeaderValidator<A0BlockHeader> headerValidator = new ChainConfiguration().createBlockHeaderValidator();
        List<A0BlockHeader> filtered = new ArrayList<>();
        A0BlockHeader prev = null;
        for(A0BlockHeader current : _headers){

            // ignore this batch if any invalidated header
            if(!headerValidator.validate(current)) {
                log.debug("<invalid-header num={} hash={}>", current.getNumber(), current.getHash());
                return;
            }

            // break if not consisting
            if(prev != null && current.getNumber() != (prev.getNumber() + 1))
                break;

            // add if not cached
            if(!importedBlockHashes.containsKey(ByteArrayWrapper.wrap(current.getHash())))
                filtered.add(current);

            prev = current;
        }

        // _headers.sort((h1, h2) -> (int) (h1.getNumber() - h2.getNumber()));
        if(filtered.size() > 0)
            importedHeaders.add(new HeadersWrapper(_nodeIdHashcode, _displayId, filtered));

        log.debug(
            "<incoming-headers size={} from-num={} to-num={} from-node={}>",
                filtered.size(),
                filtered.get(0).getNumber(),
                filtered.get(filtered.size() - 1).getNumber(),
                _displayId
        );
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
            log.debug("imported blocks queue is full!");
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
                log.error("<assemble-and-validate-blocks from-node={}>", _displayId);
                break;
            } else
                blocks.add(block);
        }

        int m = blocks.size();
        if (m == 0)
            return;


        if (log.isDebugEnabled()) {
            log.debug("<incoming-bodies size={} from-num={} to-num={} from-node={}>", blocks.size(),
                    blocks.get(0).getNumber(), blocks.get(blocks.size() - 1).getNumber(), _displayId);
        }

        // add batch
        importedBlocks.add(new BlocksWrapper(_nodeIdHashcode, _displayId, blocks));

        log.debug("<incoming-bodies size={} from-num={} to-num={} from-node={}>", m, blocks.get(0).getNumber(),
                blocks.get(blocks.size() - 1).getNumber(), _displayId);

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
