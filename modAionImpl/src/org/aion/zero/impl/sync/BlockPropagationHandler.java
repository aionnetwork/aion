package org.aion.zero.impl.sync;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.IChainCfg;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.valid.BlockHeaderValidator;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.msg.BroadcastNewBlock;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.IAionBlock;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Handles state and actions related to block propagation
 *
 * TODO: exists as functionality of SyncMgr, need to decouple
 */
public class BlockPropagationHandler {

    public enum Status {
        DROPPED,
        PROPAGATED,
        CONNECTED
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

    private final BlockHeaderValidator blockHeaderValidator;

    private static final Logger log = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    public BlockPropagationHandler(final int cacheSize,
                                   final IAionBlockchain blockchain,
                                   final IP2pMgr p2pManager,
                                   BlockHeaderValidator headerValidator) {
        this.cacheSize = cacheSize;

        // guarded by self
        this.cacheMap = new LRUMap<>(this.cacheSize);

        // the expectation is that we will not have as many peers as we have blocks
        this.blockchain = blockchain;

        // record our own nodeId to cover corner case
        this.p2pManager = p2pManager;

        this.blockHeaderValidator = headerValidator;
    }

    // propagate a new block, send without discrimination to maximize chances
    public void sendNewBlock(final AionBlock block) {
        synchronized (this.cacheMap) {
            this.cacheMap.put(new ByteArrayWrapper(block.getHash()), true);
        }

        this.p2pManager.getActiveNodes().values().forEach(n -> {
            if (log.isDebugEnabled())
                log.debug("sending new block" + block.getShortHash() + " to: " + n.getIdHash());
            this.p2pManager.send(n.getIdHash(), new BroadcastNewBlock(block));
        });
    }

    public Status processIncomingBlock(final int nodeId, final AionBlock block) {
        if (block == null)
            return Status.DROPPED;

        ByteArrayWrapper hashWrapped = new ByteArrayWrapper(block.getHash());

        // prevent cache from overflowing with well formed but invalid block headers
        if (!this.blockHeaderValidator.validate(block.getHeader()))
            return Status.DROPPED;

        // guarantees if multiple requests of same block appears, only one goes through
        synchronized(this.cacheMap) {
            if (this.cacheMap.containsKey(hashWrapped))
                return Status.DROPPED;
            // regardless if block processing is successful, place into cache
            this.cacheMap.put(hashWrapped, true);
        }

        AionBlock bestBlock = this.blockchain.getBestBlock();

        // assumption is that we are on the correct chain
        if (bestBlock.getNumber() > block.getNumber())
            return Status.DROPPED;

        // do a very simple check to verify parent child relationship
        // this implies we only propagate blocks from our own chain
        if (!bestBlock.isParentOf(block))
            return Status.DROPPED;

        // send
        send(block, nodeId);

        // process
        ImportResult result = this.blockchain.tryToConnect(block);
        return result.isSuccessful() ? Status.CONNECTED : Status.PROPAGATED;
    }

    private void send(AionBlock block, int
            nodeId) {
        // current proposal is to send to all peers with lower blockNumbers
        this.p2pManager.getActiveNodes().values()
                .stream()
                .filter(n -> n.getIdHash() != nodeId)
                .filter(n -> n.getBestBlockNumber() <= block.getNumber())
                .forEach(n -> {
                    if (log.isDebugEnabled())
                        log.debug("sending new block" + block.getShortHash() + " to: " + n.getIdHash());
                    this.p2pManager.send(n.getIdHash(), new BroadcastNewBlock(block));
                });
    }

    public int getCacheSize() {
        return this.cacheSize;
    }
}
