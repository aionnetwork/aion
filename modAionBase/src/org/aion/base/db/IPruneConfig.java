package org.aion.base.db;

/**
 * Interface for pruning configuration parameters.
 *
 * @author Alexandra Roatis
 */
public interface IPruneConfig {

    /**
     * Indicates if pruning should be enabled or disabled.
     *
     * @return {@code true} when pruning enabled, {@code false} when pruning disabled.
     */
    boolean isEnabled();

    /**
     * Indicates if archiving should be enabled or disabled.
     *
     * @return {@code true} when archiving enabled, {@code false} when archiving disabled.
     */
    boolean isArchived();

    /**
     * @return the number of topmost blocks for which the full data should be maintained on disk.
     */
    int getCurrentCount();

    /**
     * Gets the rate at which blocks should be archived (for which the full data should be
     * maintained on disk). Blocks that are exact multiples of the returned value should be
     * persisted on disk, regardless of other pruning.
     *
     * @return integer value representing the archive rate
     */
    int getArchiveRate();
}
