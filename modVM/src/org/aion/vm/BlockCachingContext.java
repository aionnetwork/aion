package org.aion.vm;

import org.aion.avm.core.ExecutionType;

/**
 * This is used in conjunction with cachedBlockNumberForAVM to determine whether to read or write to
 * the cache. Note that even cases which don't update a cache may still invalidate it (eviction).
 *
 * <p>This class is a proxy for {@link ExecutionType} to enable use inside the kernel without
 * unnecessarily adding a dependency on the AVM modules.
 */
public enum BlockCachingContext {
    /**
     * The case where the new block will be added to the current main chain best block.
     * cachedBlockNumberForAVM must be equal to currentBlockNumber - 1. Both code and data cache
     * will be read and written.
     */
    MAINCHAIN(ExecutionType.ASSUME_MAINCHAIN),
    /**
     * The case where the new block is on a side chain, but its parent is a main chain block.
     * cachedBlockNumberForAVM must be the new block's immediate parent and it must be on the
     * mainchain. cachedBlockNumberForAVM must be equal to currentBlockNumber - 1. Any cache (code
     * and data) up to and including cachedBlockNumberForAVM is valid to use. No caches are updated.
     */
    SIDECHAIN(ExecutionType.ASSUME_SIDECHAIN),
    /**
     * The case where the new block is on a side chain, and its parent is on the side chain as well.
     * cachedBlockNumberForAVM must be zero and is not used because the exact fork point is not
     * known. No caches are read. No caches are updated.
     */
    DEEP_SIDECHAIN(ExecutionType.ASSUME_DEEP_SIDECHAIN),
    /**
     * The case where the main chain is switched/reorganized and a side chain is marked as the new
     * main chain. cachedBlockNumberForAVM reflects the common ancestor of the new and old main
     * chain (fork point). Only code caches up to and including cachedBlockNumberForAVM are valid
     * because the blocks between the common main chain block and current block are not reflected in
     * the cache. The data cache is completely invalidated and not read. Both caches are written.
     */
    SWITCHING_MAINCHAIN(ExecutionType.SWITCHING_MAINCHAIN),
    /**
     * The case where mining operation is being performed. cachedBlockNumberForAVM must be equal to
     * currentBlockNumber - 1. Only the code cache is read. No caches are updated.
     */
    PENDING(ExecutionType.MINING),
    /**
     * Used for calls that do not store the result including eth_call and eth_estimategas.
     * cachedBlockNumberForAVM reflects the block and state where the request should be executed.
     * Both code and data caches up to and including cachedBlockNumberForAVM will be read. No caches
     * are updated.
     */
    CALL(ExecutionType.ETH_CALL);

    public final ExecutionType avmType;

    BlockCachingContext(ExecutionType avmType) {
        this.avmType = avmType;
    }
}
