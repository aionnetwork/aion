package org.aion.zero.impl.sync;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.types.ByteArrayWrapper;
import org.aion.zero.impl.sync.msg.ResponseBlocks;

/**
 * Directs behavior for fast sync functionality.
 *
 * @author Alexandra Roatis
 */
public final class FastSyncManager {

    private boolean enabled;
    private final AtomicBoolean complete = new AtomicBoolean(false);

    private final Map<ByteArrayWrapper, byte[]> importedTrieNodes = new ConcurrentHashMap<>();

    public FastSyncManager() {
        this.enabled = false;
    }

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

    private boolean isCompleteBlockData() {
        // TODO: block requests should be made backwards from pivot
        // TODO: requests need to be based on hash instead of level
        return false;
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
}
