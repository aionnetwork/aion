package org.aion.zero.impl.sync;

import com.google.common.annotations.VisibleForTesting;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.types.ByteArrayWrapper;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.sync.msg.ResponseBlocks;
import org.aion.zero.impl.types.AionBlock;

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

    private AionBlock pivot = null;
    private long pivotNumber = -1;
    private ByteArrayWrapper pivotHash = null;

    private final Map<ByteArrayWrapper, byte[]> importedTrieNodes = new ConcurrentHashMap<>();

    public FastSyncManager(AionBlockchainImpl chain) {
        this.enabled = true;
        this.chain = chain;
    }

    @VisibleForTesting
    void setPivot(AionBlock pivot) {
        Objects.requireNonNull(pivot);

        this.pivot = pivot;
        this.pivotNumber = pivot.getNumber();
        this.pivotHash = ByteArrayWrapper.wrap(pivot.getHash());
    }

    public long getPivotNumber() {
        return pivotNumber;
    }

    public ByteArrayWrapper getPivotHash() {
        return pivotHash;
    }

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
        } else if (pivotHash == null) {
            // the pivot was not initialized yet
            return false;
        } else if (chain.getBlockStore().getChainBlockByNumber(1L) == null) {
            // checks for first block for fast fail if incomplete
            return false;
        } else if (chain.findMissingAncestor(pivotHash.getData()) != null) { // long check done last
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

    /** checks PoW and adds correct blocks to import list */
    public void validateAndAddBlocks(int peerId, String displayId, ResponseBlocks response) {
        // TODO: implement
    }

    public BlocksWrapper takeFilteredBlocks(ByteArrayWrapper required) {
        // TODO: ensure that blocks that are of heights larger than the required are discarded
        // TODO: the fastSyncMgr ensured the batch cannot be empty
        // TODO: ensure that the required hash is part of the batch
        // TODO: if the required hash is not among the known ones, request it from the network

        return null;
    }
}
