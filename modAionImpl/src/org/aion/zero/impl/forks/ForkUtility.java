package org.aion.zero.impl.forks;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.math.BigInteger;

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


    // variables used by the Nonce addition fork
    private boolean nonceForkEnabled = false;
    private long nonceForkBlockHeight = Long.MAX_VALUE;
    private BigInteger nonceForkResetDiff;

    public void enableNonceFork(long nonceForkBlockHeight) {
        Preconditions.checkArgument(nonceForkBlockHeight >= 2, "Invalid fork1.3 block number: must be >= 2");
        this.nonceForkBlockHeight = nonceForkBlockHeight;
        this.nonceForkEnabled = true;
    }

    @VisibleForTesting
    public void disableNonceFork() {
        this.nonceForkBlockHeight = Long.MAX_VALUE;
        this.nonceForkEnabled = false;
    }

    public boolean isNonceForkActive(long contextBlockNumber) {
        return nonceForkEnabled && (contextBlockNumber > nonceForkBlockHeight);
    }

    public boolean isNonceForkBlock(long contextBlockNumber) {
        return nonceForkEnabled && (contextBlockNumber == nonceForkBlockHeight);
    }

    public void setNonceForkResetDiff(BigInteger newDiff) {
        nonceForkResetDiff = newDiff;
    }

    public BigInteger getNonceForkResetDiff() {
        return nonceForkResetDiff;
    }

    public long getNonceForkBlockHeight() {
        return nonceForkBlockHeight;
    }
}
