package org.aion.zero.impl.forks;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

/** Utility for managing the fork checks. Currently implements only Unity fork functionality. */
public class ForkUtility {

    // variables used by the Unity fork
    private boolean unityForkEnabled = false;
    private long unityForkBlockHeight = Long.MAX_VALUE;

    // variable used by the 0.4.0 fork
    private boolean fork040Enabled = false;
    private long fork040BlockHeight = Long.MAX_VALUE;

    /**
     * Enables the Unity fork after the given block number.
     *
     * @param unityForkBlockHeight the height of the block after which Unity behaviour is applied
     */
    public void enableUnityFork(long unityForkBlockHeight) {
        Preconditions.checkArgument(unityForkBlockHeight >= 2, "Invalid fork1.0 (Unity) block number: must be >= 2");
        this.unityForkBlockHeight = unityForkBlockHeight;
        this.unityForkEnabled = true;
    }

    /** Disables the Unity fork. */
    @VisibleForTesting
    public void disableUnityFork() {
        this.unityForkBlockHeight = Long.MAX_VALUE;
        this.unityForkEnabled = false;
    }

    /**
     * Enables the kernel ver0.4.0 fork after the given block number.
     *
     * @param fork040BlockHeight the height of the block after which 0.4.0 behaviour is applied
     */
    public void enable040Fork(long fork040BlockHeight) {
        Preconditions.checkArgument(fork040BlockHeight >= 0, "Invalid fork0.4.0 block number: must be >= 0");
        this.fork040BlockHeight = unityForkBlockHeight;
        this.fork040Enabled = true;
    }

    /** Disables the 0.4.0 fork. */
    @VisibleForTesting
    public void disable040Fork() {
        this.fork040BlockHeight = Long.MAX_VALUE;
        this.fork040Enabled = false;
    }

    /**
     * Returns a boolean value indicating if the Unity fork is active for the given context (block
     * number). We want the fork block itself to be a PoW block subject to the old pre-Unity rules,
     * so we use a strict greater than comparison.
     *
     * @return {@code true} if the unity fork is active for the given context (block number), {@code
     *     false} otherwise
     */
    public boolean isUnityForkActive(long contextBlockNumber) {
        return unityForkEnabled && (contextBlockNumber > unityForkBlockHeight);
    }

    public boolean isUnityForkBlock(long contextBlockNumber) {
        return unityForkEnabled && (contextBlockNumber == unityForkBlockHeight);
    }

    /**
     * Returns a boolean value indicating if the kernel v0.4.0 fork is active for the given context (block
     * number).
     *
     * @return {@code true} if the 0.4.0 fork is active for the given context (block number), {@code
     *     false} otherwise
     */
    public boolean is040ForkActive(long contextBlockNumber) {
        return fork040Enabled && (contextBlockNumber >= fork040BlockHeight);
    }

    public boolean is040ForkBlock(long contextBlockNumber) {
        return fork040Enabled && (contextBlockNumber == fork040BlockHeight);
    }
}
