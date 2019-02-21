package org.aion.zero.impl.sync.handler;

import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.valid.BlockHeaderValidator;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.SyncStats;
import org.aion.zero.impl.sync.msg.BroadcastNewBlock;
import org.aion.zero.impl.sync.msg.ResStatus;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;

/**
 * @author yao
 *     <p>Handles state and actions related to block propagation
 *     <p>TODO: exists as functionality of SyncMgr, need to decouple
 */
public class BlockPropagationHandler {

    public enum PropStatus {
        DROPPED, // block was invalid, drop no propagation
        PROPAGATED, // block was propagated, but was not connected
        CONNECTED, // block was ONLY connected, not propagated
        PROP_CONNECTED // block propagated and connected
    }

    /** Connection to blockchain */
    private IAionBlockchain blockchain;

    /** LRU cache map, maintains the latest cacheSize blocks seen (not counting duplicates). */
    private final Map<ByteArrayWrapper, Boolean> cacheMap;

    private final SyncStats syncStats;

    private final IP2pMgr p2pManager;

    private final BlockHeaderValidator<A0BlockHeader> blockHeaderValidator;

    private static final Logger log = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    private final boolean isSyncOnlyNode;

    private static final byte[] genesis = CfgAion.inst().getGenesis().getHash();

    private final byte apiVersion;

    private final int pendingTxCount;

    public BlockPropagationHandler(
            final int cacheSize,
            final IAionBlockchain blockchain,
            final SyncStats syncStats,
            final IP2pMgr p2pManager,
            BlockHeaderValidator<A0BlockHeader> headerValidator,
            final boolean isSyncOnlyNode,
            final byte apiVersion,
            final int pendingTxCount) {
        /*
         * Size of the cache maintained within the map, a lower cacheSize
         * saves space, but indicates we may "forget" about a block sooner.
         *
         * This can possibly lead to increase network traffic and unecessary
         * process (?)
         *
         * all accesses to cacheMap are guarded by instance
         */
        this.cacheMap = new LRUMap<>(cacheSize);

        // the expectation is that we will not have as many peers as we have blocks
        this.blockchain = blockchain;

        this.syncStats = syncStats;

        // record our own nodeId to cover corner case
        this.p2pManager = p2pManager;

        this.blockHeaderValidator = headerValidator;

        this.isSyncOnlyNode = isSyncOnlyNode;
        this.apiVersion = apiVersion;
        this.pendingTxCount = pendingTxCount;
    }

    // assumption here is that blocks propagated have unique hashes
    public void propagateNewBlock(final AionBlock block) {
        if (block == null) return;
        ByteArrayWrapper hashWrapped = new ByteArrayWrapper(block.getHash());

        synchronized (this.cacheMap) {
            this.cacheMap.put(hashWrapped, true);
        }

        this.p2pManager
                .getActiveNodes()
                .values()
                .forEach(
                        n -> {
                            if (log.isDebugEnabled())
                                log.debug(
                                        "<sending-new-block="
                                                + block.getShortHash()
                                                + " to="
                                                + n.getIdShort()
                                                + ">");
                            this.p2pManager.send(
                                    n.getIdHash(), n.getIdShort(), new BroadcastNewBlock(block));
                        });
    }

    public PropStatus processIncomingBlock(
            final int nodeId, final String _displayId, final AionBlock block) {
        if (block == null) return PropStatus.DROPPED;

        ByteArrayWrapper hashWrapped = new ByteArrayWrapper(block.getHash());

        if (!this.blockHeaderValidator.validate(block.getHeader(), log)) return PropStatus.DROPPED;

        // guarantees if multiple requests of same block appears, only one goes through
        synchronized (this.cacheMap) {
            if (this.cacheMap.get(hashWrapped) != null) {
                if (log.isTraceEnabled()) {
                    log.trace("block {} already cached", block.getShortHash());
                }
                return PropStatus.DROPPED;
            }
            // regardless if block processing is successful, place into cache
            this.cacheMap.put(hashWrapped, true);
        }

        // process
        long t1 = System.currentTimeMillis();
        ImportResult result;

        if (this.blockchain.skipTryToConnect(block.getNumber())) {
            result = ImportResult.NO_PARENT;
            if (log.isInfoEnabled()) {
                log.info(
                        "<import-status: node = {}, hash = {}, number = {}, txs = {}, result = NOT_IN_RANGE>",
                        _displayId,
                        block.getShortHash(),
                        block.getNumber(),
                        block.getTransactionsList().size(),
                        result);
            } else if (log.isDebugEnabled()) {
                log.debug(
                        "<import-status: node = {}, hash = {}, number = {}, txs = {}, block time = {}, result = NOT_IN_RANGE>",
                        _displayId,
                        block.getShortHash(),
                        block.getNumber(),
                        block.getTransactionsList().size(),
                        block.getTimestamp(),
                        result);
            }
            boolean stored = blockchain.storePendingStatusBlock(block);
            if (stored) {
                this.syncStats.updatePeerStoredBlocks(_displayId, 1);
                this.syncStats.updatePeerTotalBlocks(_displayId, 1);
            }

            if (log.isDebugEnabled()) {
                log.debug(
                        "Block hash = {}, number = {}, txs = {} was {}.",
                        block.getShortHash(),
                        block.getNumber(),
                        block.getTransactionsList().size(),
                        stored ? "STORED" : "NOT STORED");
            }
        } else {
            result = this.blockchain.tryToConnect(block);

            long t2 = System.currentTimeMillis();
            if (result.isStored()) {
                this.syncStats.updatePeerImportedBlocks(_displayId, 1);
                this.syncStats.updatePeerTotalBlocks(_displayId, 1);
            }

            if (log.isInfoEnabled()) {
                log.info(
                        "<import-status: node = {}, hash = {}, number = {}, txs = {}, result = {}, time elapsed = {} ms>",
                        _displayId,
                        block.getShortHash(),
                        block.getNumber(),
                        block.getTransactionsList().size(),
                        result,
                        t2 - t1);
            } else if (log.isDebugEnabled()) {
                log.debug(
                        "<import-status: node = {}, hash = {}, number = {}, txs = {}, block time = {}, result = {}, time elapsed = {} ms>",
                        _displayId,
                        block.getShortHash(),
                        block.getNumber(),
                        block.getTransactionsList().size(),
                        block.getTimestamp(),
                        result,
                        t2 - t1);
            }
        }

        // send
        boolean sent = result.isValid() && send(block, nodeId);

        // notify higher td peers in order to limit the rebroadcast on delay of res status updating
        if (result.isBest()) {
            AionBlock bestBlock = blockchain.getBestBlock();
            BigInteger td = bestBlock.getCumulativeDifficulty();
            ResStatus rs =
                    new ResStatus(
                            bestBlock.getNumber(),
                            td.toByteArray(),
                            bestBlock.getHash(),
                            genesis,
                            apiVersion,
                            (short) p2pManager.getActiveNodes().size(),
                            BigInteger.valueOf(this.pendingTxCount).toByteArray(),
                            p2pManager.getAvgLatency());

            this.p2pManager.getActiveNodes().values().stream()
                    .filter(n -> n.getIdHash() != nodeId)
                    .filter(n -> n.getTotalDifficulty().compareTo(td) >= 0)
                    .forEach(
                            n -> {
                                log.debug(
                                        "<push-status blk={} hash={} to-node={} dd={} import-result={}>",
                                        block.getNumber(),
                                        block.getShortHash(),
                                        n.getIdShort(),
                                        td.longValue() - n.getTotalDifficulty().longValue(),
                                        result.name());
                                this.p2pManager.send(n.getIdHash(), n.getIdShort(), rs);
                            });
        }

        // process resulting state
        if (sent && result.isSuccessful()) return PropStatus.PROP_CONNECTED;

        if (result.isSuccessful()) return PropStatus.CONNECTED;

        if (sent) return PropStatus.PROPAGATED;

        // gets dropped when the result is not valid
        return PropStatus.DROPPED;
    }

    private boolean send(AionBlock block, int nodeId) {
        if (isSyncOnlyNode) return true;

        // current proposal is to send to all peers with lower blockNumbers
        AtomicBoolean sent = new AtomicBoolean();
        this.p2pManager.getActiveNodes().values().stream()
                .filter(n -> n.getIdHash() != nodeId)
                // peer is within 5 blocks of the block we're about to send
                .filter(
                        n -> {
                            long delta = block.getNumber() - n.getBestBlockNumber();
                            return (delta >= 0 && delta <= 100) || (n.getBestBlockNumber() == 0);
                        })
                .forEach(
                        n -> {
                            if (log.isDebugEnabled())
                                log.debug(
                                        "<sending-new-block hash="
                                                + block.getShortHash()
                                                + " to-node="
                                                + n.getIdShort()
                                                + ">");
                            this.p2pManager.send(
                                    n.getIdHash(), n.getIdShort(), new BroadcastNewBlock(block));
                            sent.getAndSet(true);
                        });
        return sent.get();
    }
}
