package org.aion.zero.impl.forks;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;

/** Utility for managing the fork checks. Currently implements only Unity fork functionality. */
public class ForkUtility {

    // variables used by the Unity fork
    private boolean unityForkEnabled = false;
    private long unityForkBlockHeight = Long.MAX_VALUE;

    // variable used by the 0.4.0 fork
    private boolean fork040Enabled = false;
    private long fork040BlockHeight = Long.MAX_VALUE;

    public ForkUtility() {}

    public ForkUtility(Properties properties, Logger log) {
        Objects.requireNonNull(properties);
        Objects.requireNonNull(log);

        Optional<Long> maybeUnityFork = loadUnityForkNumberFromConfig(properties);
        if (maybeUnityFork.isPresent()) {
            if (maybeUnityFork.get() < 2) {   // AKI-419, Constrain the minimum unity fork number
                log.warn("The unity fork number cannot be less than 2, set the fork number to 2");
                maybeUnityFork = Optional.of(2L);
            }

            enableUnityFork(maybeUnityFork.get());
        }

        Optional<Long> maybe040Fork = load040ForkNumberFromConfig(properties);
        if (maybe040Fork.isPresent()) {
            if (maybe040Fork.get() < 0) {
                log.warn("The 040 fork number cannot be less than 0, set the fork number to 0");
                maybe040Fork = Optional.of(0L);
            }

            enable040Fork(maybe040Fork.get());
        }

        Optional<Long> maybeNonceFork = loadNonceForkNumberFromConfig(properties);
        if (maybeNonceFork.isPresent()) {
            if (maybeNonceFork.get() < 2) {   // AKI-419, Constrain the minimum unity fork number
                log.warn("The nonce fork number cannot be less than 2, set the fork number to 2");
                maybeNonceFork = Optional.of(2L);
            }

            enableNonceFork(maybeNonceFork.get());
        }

        Optional<Long> maybeSignatureSwapFork = loadSignatureSwapForkNumberFromConfig(properties);
        if (maybeSignatureSwapFork.isPresent()) {
            if (maybeSignatureSwapFork.get() < 2) {
                log.warn("The fork1.7 block number cannot be less than 2, set the fork number to 2");
                maybeSignatureSwapFork = Optional.of(2L);
            }

            enableSignatureSwapFork(maybeSignatureSwapFork.get());
        }
    }

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
        this.fork040BlockHeight = fork040BlockHeight;
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

    // variables used by the Nonce addition fork
    private boolean nonceForkEnabled = false;
    private long nonceForkBlockHeight = Long.MAX_VALUE;
    private BigInteger nonceForkResetDiff;

    public void enableNonceFork(long nonceForkBlockHeight) {
        Preconditions.checkArgument(nonceForkBlockHeight >= 2, "Invalid fork1.3 block number: must be >= 2");
        this.nonceForkBlockHeight = nonceForkBlockHeight;
        this.nonceForkEnabled = true;
    }

    // variable used by the signature swap fork
    private boolean signatureSwapForkEnabled = false;
    private long signatureSwapForkBlockHeight = Long.MAX_VALUE;

    /**
     * Returns a boolean value indicating if the signature swap fork is active for the given context (block
     * number). We want the fork block itself to be a PoW block subject to the old pre-Unity rules,
     * so we use a strict greater than comparison.
     *
     * @return {@code true} if the signature swap fork fork is active for the given context (block number), {@code
     *     false} otherwise
     */
    public boolean isSignatureSwapForkActive(long contextBlockNumber) {
        return signatureSwapForkEnabled && (contextBlockNumber >= signatureSwapForkBlockHeight);
    }

    public boolean isSignatureSwapForkBlock(long contextBlockNumber) {
        return signatureSwapForkEnabled && (contextBlockNumber == signatureSwapForkBlockHeight);
    }

    public void enableSignatureSwapFork(long signatureSwapForkBlockHeight) {
        Preconditions.checkArgument(signatureSwapForkBlockHeight >= 2, "Invalid fork1.7 block number: must be >= 2");
        this.signatureSwapForkBlockHeight = signatureSwapForkBlockHeight;
        this.signatureSwapForkEnabled = true;
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

    /**
     * Determine fork 1.0 fork number from Aion Config.
     *
     * @return 1.0 fork number, if configured; {@link Optional#empty()} otherwise.
     * @throws NumberFormatException if "fork1.0" present in the config, but not parseable
     */
    private static Optional<Long> loadUnityForkNumberFromConfig(Properties properties) {
        String unityforkSetting = properties.getProperty("fork1.0");
        if(unityforkSetting == null) {
            return Optional.empty();
        } else {
            return Optional.of(Long.valueOf(unityforkSetting));
        }
    }

    /**
     * Determine fork 1.3 fork number from Aion Config.
     *
     * @return 1.3 fork number, if configured; {@link Optional#empty()} otherwise.
     * @throws NumberFormatException if "fork1.3" present in the config, but not parseable
     */
    private static Optional<Long> loadNonceForkNumberFromConfig(Properties properties) {
        String nonceFork = properties.getProperty("fork1.3");
        if(nonceFork == null) {
            return Optional.empty();
        } else {
            return Optional.of(Long.valueOf(nonceFork));
        }
    }

    /**
     * Determine fork 0.4.0 fork number from Aion Config.
     *
     * @return 0.4.0 fork number, if configured; {@link Optional#empty()} otherwise.
     * @throws NumberFormatException if "fork0.4.0" present in the config, but not parseable
     */
    private static Optional<Long> load040ForkNumberFromConfig(Properties properties) {
        String fork040Setting = properties.getProperty("fork0.4.0");
        if(fork040Setting == null) {
            return Optional.empty();
        } else {
            return Optional.of(Long.valueOf(fork040Setting));
        }
    }

    /**
     * Determine fork 1.7 fork number from Aion Config.
     *
     * @return 1.7 fork number, if configured; {@link Optional#empty()} otherwise.
     * @throws NumberFormatException if "fork1.7" present in the config, but not parseable
     */
    private static Optional<Long> loadSignatureSwapForkNumberFromConfig(Properties properties) {
        String signatureSwapFork = properties.getProperty("fork1.7");
        if(signatureSwapFork == null) {
            return Optional.empty();
        } else {
            return Optional.of(Long.valueOf(signatureSwapFork));
        }
    }
}
