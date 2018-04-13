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
 * <ether.camp> team through the ethereumJ library.
 * Ether.Camp Inc. (US) team through Ethereum Harmony.
 * John Tromp through the Equihash solver.
 * Samuel Neves through the BLAKE2 implementation.
 * Zcash project team.
 * Bitcoinj team.
 */

package org.aion.zero.impl.sync.handler;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.valid.BlockHeaderValidator;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.msg.BroadcastNewBlock;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author yao
 *
 * Handles state and actions related to block propagation
 *
 * TODO: exists as functionality of SyncMgr, need to decouple
 */
public class BlockPropagationHandler {

    public enum PropStatus {
        DROPPED, // block was invalid, drop no propagation
        PROPAGATED, // block was propagated, but was not connected
        CONNECTED, // block was ONLY connected, not propagated
        PROP_CONNECTED // block propagated and connected
    }

    /**
     * Size of the cache maintained within the map, a lower cacheSize
     * saves space, but indicates we may "forget" about a block sooner.
     *
     * This can possibly lead to increase network traffic and unecessary
     * process (?)
     */
    private final int cacheSize;

    /**
     * Connection to blockchain
     */
    private IAionBlockchain blockchain;

    /**
     * LRU cache map, maintains the latest cacheSize blocks seen (not counting duplicates).
     */
    private final Map<ByteArrayWrapper, Boolean> cacheMap;

    private final IP2pMgr p2pManager;

    private final BlockHeaderValidator<A0BlockHeader> blockHeaderValidator;

    private static final Logger log = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    public BlockPropagationHandler(final int cacheSize,
                                   final IAionBlockchain blockchain,
                                   final IP2pMgr p2pManager,
                                   BlockHeaderValidator<A0BlockHeader> headerValidator) {
        this.cacheSize = cacheSize;

        // all accesses to cacheMap are guarded by instance
        this.cacheMap = new LRUMap<>(this.cacheSize);

        // the expectation is that we will not have as many peers as we have blocks
        this.blockchain = blockchain;

        // record our own nodeId to cover corner case
        this.p2pManager = p2pManager;

        this.blockHeaderValidator = headerValidator;
    }

    // assumption here is that blocks propagated have unique hashes
    public void propagateNewBlock(final AionBlock block) {
        if (block == null)
            return;
        ByteArrayWrapper hashWrapped = new ByteArrayWrapper(block.getHash());

        synchronized(this.cacheMap) {
            this.cacheMap.put(hashWrapped, true);
        }

        this.p2pManager.getActiveNodes().values().forEach(n -> {
            if (log.isDebugEnabled())
                log.debug("<sending-new-block=" + block.getShortHash() + " to=" + n.getIdShort() + ">");
            this.p2pManager.send(n.getIdHash(), new BroadcastNewBlock(block));
        });
    }

    public PropStatus processIncomingBlock(final int nodeId, final String _displayId, final AionBlock block) {

        if (block == null)
            return PropStatus.DROPPED;

        ByteArrayWrapper hashWrapped = new ByteArrayWrapper(block.getHash());

        if (!this.blockHeaderValidator.validate(block.getHeader(), log))
            return PropStatus.DROPPED;

        // guarantees if multiple requests of same block appears, only one goes through
        synchronized(this.cacheMap) {
            if (this.cacheMap.get(hashWrapped) != null)
                return PropStatus.DROPPED;
            // regardless if block processing is successful, place into cache
            this.cacheMap.put(hashWrapped, true);
        }


//        AionBlock bestBlock = this.blockchain.getBestBlock();
//
//        // assumption is that we are on the correct chain
//        if (bestBlock.getNumber() > block.getNumber())
//            return PropStatus.DROPPED;
//
//        // do a very simple check to verify parent child relationship
//        // this implies we only propagate blocks from our own chain
//        if (!bestBlock.isParentOf(block))
//            return PropStatus.DROPPED;

        // send
        boolean sent = send(block, nodeId);

        // process
        long t1 = System.currentTimeMillis();
        ImportResult result ;

        if (this.blockchain.skipTryToConnect(block.getNumber())) {
            result = ImportResult.NO_PARENT;
            log.info("<import-status: node = {}, hash = {}, number = {}, txs = {}, result = NOT_IN_RANGE>",
                     _displayId,
                     block.getShortHash(),
                     block.getNumber(),
                     block.getTransactionsList().size(),
                     result);
        } else {
            result = this.blockchain.tryToConnect(block);
            long t2 = System.currentTimeMillis();
            log.info("<import-status: node = {}, hash = {}, number = {}, txs = {}, result = {}, time elapsed = {} ms>",
                     _displayId,
                     block.getShortHash(),
                     block.getNumber(),
                     block.getTransactionsList().size(),
                     result,
                     t2 - t1);
        }

        // process resulting state
        if (sent && result.isSuccessful())
            return PropStatus.PROP_CONNECTED;

        if (result.isSuccessful())
            return PropStatus.CONNECTED;

        if (sent)
            return PropStatus.PROPAGATED;

        // should never reach here, but just in case
        return PropStatus.DROPPED;
    }

    private boolean send(AionBlock block, int nodeId) {
        // current proposal is to send to all peers with lower blockNumbers
        AtomicBoolean sent = new AtomicBoolean();
        this.p2pManager.getActiveNodes().values()
                .stream()
                .filter(n -> n.getIdHash() != nodeId)
                // peer is within 5 blocks of the block we're about to send
                .filter(n -> {
                    long delta = block.getNumber() - n.getBestBlockNumber();
                    return delta >= 0 && delta <= 5;
                })
                .forEach(n -> {
                    if (log.isDebugEnabled())
                        log.debug("<sending-new-block hash=" + block.getShortHash() + " to-node=" + n.getIdShort() + ">");
                    this.p2pManager.send(n.getIdHash(), new BroadcastNewBlock(block));
                    sent.getAndSet(true);
                });
        return sent.get();
    }

//    public int getCacheSize() {
//        return this.cacheSize;
//    }
}
