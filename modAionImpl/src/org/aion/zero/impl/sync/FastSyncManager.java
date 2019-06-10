package org.aion.zero.impl.sync;

import static org.aion.p2p.V1Constants.BLOCKS_REQUEST_MAXIMUM_BATCH_SIZE;

import com.google.common.annotations.VisibleForTesting;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.valid.BlockHeaderValidator;
import org.aion.p2p.INode;
import org.aion.p2p.impl1.P2pMgr;
import org.aion.vm.api.types.ByteArrayWrapper;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.sync.msg.RequestBlocks;
import org.aion.zero.impl.sync.msg.ResponseBlocks;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;

/**
 * Directs behavior for fast sync functionality.
 *
 * @author Alexandra Roatis
 */
public final class FastSyncManager {

    // TODO: ensure correct behavior when disabled
    private boolean enabled;
    // TODO: ensure correct behavior when complete
    private final AtomicBoolean complete = new AtomicBoolean(false);
    private final AtomicBoolean completeBlocks = new AtomicBoolean(false);

    private final AionBlockchainImpl chain;
    private final BlockHeaderValidator<A0BlockHeader> blockHeaderValidator;
    private final P2pMgr p2pMgr;

    // TODO: consider adding a FAST_SYNC log as well
    private static final Logger log = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    private AionBlock pivot = null;

    Map<ByteArrayWrapper, Long> importedBlockHashes =
            Collections.synchronizedMap(new LRUMap<>(4096));
    Map<ByteArrayWrapper, ByteArrayWrapper> receivedBlockHashes =
            Collections.synchronizedMap(new LRUMap<>(1000));

    BlockingQueue<BlocksWrapper> downloadedBlocks = new LinkedBlockingQueue<>();
    Map<ByteArrayWrapper, BlocksWrapper> receivedBlocks = new HashMap<>();

    private final Map<ByteArrayWrapper, byte[]> importedTrieNodes = new ConcurrentHashMap<>();

    public FastSyncManager(
            AionBlockchainImpl chain,
            BlockHeaderValidator<A0BlockHeader> blockHeaderValidator,
            final P2pMgr p2pMgr) {
        this.enabled = true;
        this.chain = chain;
        this.blockHeaderValidator = blockHeaderValidator;
        this.p2pMgr = p2pMgr;
    }

    @VisibleForTesting
    void setPivot(AionBlock pivot) {
        Objects.requireNonNull(pivot);

        this.pivot = pivot;
    }

    public AionBlock getPivot() {
        return pivot;
    }

    // TODO: shutdown pool
    ExecutorService executors =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public void addImportedNode(ByteArrayWrapper key, byte[] value, DatabaseType dbType) {
        if (enabled) {
            importedTrieNodes.put(key, value);
        }
    }

    public boolean containsExact(ByteArrayWrapper key, byte[] value) {
        return enabled
                && importedTrieNodes.containsKey(key)
                && Arrays.equals(importedTrieNodes.get(key), value);
    }

    /** Changes the pivot in case of import failure. */
    public void handleFailedImport(
            ByteArrayWrapper key, byte[] value, DatabaseType dbType, int peerId, String peer) {
        if (enabled) {
            // TODO: received incorrect or inconsistent state: change pivot??
            // TODO: consider case where someone purposely sends incorrect values
            // TODO: decide on how far back to move the pivot
        }
    }

    /**
     * Indicates the status of the fast sync process.
     *
     * @return {@code true} when fast sync is complete and secure, {@code false} while trie nodes
     *     are still required or completeness has not been confirmed yet
     */
    public boolean isComplete() {
        return !enabled || complete.get();
    }

    /**
     * Checks that all the conditions for completeness are fulfilled.
     *
     * @implNote Expensive functionality which should not be called frequently.
     */
    private void ensureCompleteness() {
        // already complete, do nothing
        if (isComplete()) {
            return;
        }

        // TODO: differentiate between requirements of light clients and full nodes

        // ensure all blocks were received
        if (!isCompleteBlockData()) {
            return;
        }

        // ensure all transaction receipts were received
        if (!isCompleteReceiptData()) {
            return;
        }

        // ensure complete world state for pivot was received
        if (!isCompleteWorldState()) {
            return;
        }

        // ensure complete contract details data was received
        if (!isCompleteContractDetails()) {
            return;
        }

        // ensure complete storage data was received
        if (!isCompleteStorage()) {
            return;
        }

        // everything is complete
        complete.set(true);
    }

    public boolean isCompleteBlockData() {
        if (completeBlocks.get()) {
            // all checks have already passed
            return true;
        } else if (pivot == null) {
            // the pivot was not initialized yet
            return false;
        } else if (chain.getBlockStore().getChainBlockByNumber(1L) == null) {
            // checks for first block for fast fail if incomplete
            return false;
        } else if (chain.findMissingAncestor(pivot) != null) { // long check done last
            // full check from pivot returned block
            // i.e. the chain was incomplete at some point
            return false;
        } else {
            // making the pivot the current best block
            chain.setBestBlock(pivot);

            // walk through the chain to update the total difficulty
            chain.getBlockStore().pruneAndCorrect();
            chain.getBlockStore().flush();

            completeBlocks.set(true);
            return true;
        }
    }

    private boolean isCompleteReceiptData() {
        // TODO: integrated implementation from separate branch
        return false;
    }

    private boolean isCompleteWorldState() {
        // TODO: implement
        return false;
    }

    private boolean isCompleteContractDetails() {
        // TODO: implement
        return false;
    }

    private boolean isCompleteStorage() {
        // TODO: implement
        return false;
    }

    public void updateRequests(
            ByteArrayWrapper topmostKey,
            Set<ByteArrayWrapper> referencedKeys,
            DatabaseType dbType) {
        if (enabled) {
            // TODO: check what's still missing and send out requests
            // TODO: send state request to multiple peers

            // TODO: check for completeness when no requests remain
            ensureCompleteness();
        }
    }

    /**
     * Processes a block response by checking the proof-of-work. Adds valid blocks to the import
     * queue.
     *
     * @param peerId the numerical identifier of the peer who sent the response
     * @param displayId the display identifier of the peer who sent the response
     * @param response the response with blocks to be processed
     */
    public void validateAndAddBlocks(int peerId, String displayId, ResponseBlocks response) {
        if (!executors.isShutdown()) {
            executors.submit(
                    new TaskValidateAndAddBlocks(
                            peerId,
                            displayId,
                            response,
                            blockHeaderValidator,
                            downloadedBlocks,
                            importedBlockHashes,
                            receivedBlockHashes,
                            log));
        }
    }

    public void addToImportedBlocks(ByteArrayWrapper hash) {
        this.importedBlockHashes.put(hash, null); // TODO: is there something useful I can add?
        this.receivedBlockHashes.remove(hash);
    }

    public BlocksWrapper takeFilteredBlocks(ByteArrayWrapper requiredHash, long requiredLevel) {
        // first check the map
        if (receivedBlocks.containsKey(requiredHash)) {
            return receivedBlocks.remove(requiredHash);
        } else if (receivedBlockHashes.containsKey(requiredHash)) {
            // retrieve the batch that contains the block
            ByteArrayWrapper wrapperHash = receivedBlockHashes.get(requiredHash);
            return receivedBlocks.remove(wrapperHash);
        }

        // process queue data
        try {
            while (!downloadedBlocks.isEmpty()) {
                BlocksWrapper wrapper = downloadedBlocks.remove();

                if (wrapper != null) {
                    wrapper.getBlocks()
                            .removeIf(b -> importedBlockHashes.containsKey(b.getHashWrapper()));
                    if (!wrapper.getBlocks().isEmpty()) {
                        ByteArrayWrapper firstHash = wrapper.getBlocks().get(0).getHashWrapper();
                        if (firstHash.equals(requiredHash)) {
                            return wrapper;
                        } else {
                            // determine if the block is in the middle of the batch
                            boolean isRequred = false;
                            for (AionBlock block : wrapper.getBlocks()) {
                                ByteArrayWrapper hash = block.getHashWrapper();
                                receivedBlockHashes.put(hash, firstHash);
                                if (hash.equals(requiredHash)) {
                                    isRequred = true;
                                    break;
                                }
                            }
                            if (isRequred) {
                                return wrapper;
                            } else {
                                receivedBlocks.put(firstHash, wrapper);
                            }
                        }
                    }
                }
            }
        } catch (NoSuchElementException e) {
            log.debug("The empty check should have prevented this exception.", e);
        }

        // couldn't find the data, so need to request it
        makeBlockRequests(requiredHash, requiredLevel);

        return null;
    }

    private void makeBlockRequests(ByteArrayWrapper requiredHash, long requiredLevel) {
        // make request for the needed hash
        RequestBlocks request =
                new RequestBlocks(requiredHash.getData(), BLOCKS_REQUEST_MAXIMUM_BATCH_SIZE, true);

        // TODO: improve peer selection
        // TODO: request that level plus further blocks
        INode peer = p2pMgr.getRandom();
        p2pMgr.send(peer.getIdHash(), peer.getIdShort(), request);

        // send an extra request ahead of time
        if (requiredLevel - BLOCKS_REQUEST_MAXIMUM_BATCH_SIZE > 0) {
            peer = p2pMgr.getRandom();
            request =
                    new RequestBlocks(
                            requiredLevel - BLOCKS_REQUEST_MAXIMUM_BATCH_SIZE,
                            BLOCKS_REQUEST_MAXIMUM_BATCH_SIZE,
                            true);

            p2pMgr.send(peer.getIdHash(), peer.getIdShort(), request);
        }
    }
}
