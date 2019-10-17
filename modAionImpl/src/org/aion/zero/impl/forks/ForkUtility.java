package org.aion.zero.impl.forks;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

/** Utility for managing the fork checks. Currently implements only Unity fork functionality. */
public class ForkUtility {

    // variables used by the Unity fork
    private boolean unityForkEnabled = false;
    private long unityForkBlockHeight = Long.MAX_VALUE;

    /**
     * Enables the Unity fork after the given block number.
     *
     * @param unityForkBlockHeight the height of the block after which Unity behaviour is applied
     */
    public void enableUnityFork(long unityForkBlockHeight) {
        Preconditions.checkArgument(unityForkBlockHeight >= 2, "Invalid fork0.5.0 (Unity) block number: must be >= 2");
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
}
