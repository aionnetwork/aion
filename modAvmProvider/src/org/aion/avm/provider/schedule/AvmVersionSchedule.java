package org.aion.avm.provider.schedule;

import org.aion.avm.stub.AvmVersion;

/**
 * A schedule around which versions should be started and stopped and when.
 */
public final class AvmVersionSchedule {
    private final boolean isSupportedVersion1;
    private final long blockNumberToActivateVersion1;
    private final long activeVersionTolerance;

    private AvmVersionSchedule(boolean isSupportedVersion1, long blockNumberToActivateVersion1, long activeVersionTolerance) {
        if (activeVersionTolerance < 0) {
            throw new IllegalArgumentException("Cannot create schedule with negative activeVersionTolerance!");
        }
        if (blockNumberToActivateVersion1 > blockNumberToActivateVersion1 + activeVersionTolerance) {
            throw new IllegalArgumentException("Cannot create schedule with activeVersionTolerance so large it causes overflow!");
        }

        this.isSupportedVersion1 = isSupportedVersion1;
        this.blockNumberToActivateVersion1 = blockNumberToActivateVersion1;
        this.activeVersionTolerance = activeVersionTolerance;
    }

    /**
     * Constructs a new version schedule that has support for only a single version of the AVM.
     *
     * The only version of the AVM that will be supported will be referred to as version 1, and it
     * will be active beginning at block number {@code blockNumberToActivateVersion1}.
     *
     * The {@code activeVersionTolerance} parameter indicates how many blocks above or below the
     * point at which a version becomes active or inactive (superseded by the next version) that
     * the version may be permitted to stay alive. This parameter exists because starting and
     * shutting down the avm is expensive and this may prevent necessary shutdowns and start ups
     * during times like reorgs or syncing.
     *
     * AVM version 1 will be permitted to remain enabled over the following block numbers:
     * [blockNumberToActivateVersion1 - activeVersionTolerance, infinity)
     *
     * @param blockNumberToActivateAvm The block at which the avm becomes able to be run.
     * @param activeVersionTolerance The number of blocks both versions can stay alive.
     */
    public static AvmVersionSchedule newScheduleForOnlySingleVersionSupport(long blockNumberToActivateAvm, long activeVersionTolerance) {
        if (blockNumberToActivateAvm < 0) {
            throw new IllegalArgumentException("Cannot create schedule with negative version 1 active block!");
        }

        return new AvmVersionSchedule(true, blockNumberToActivateAvm, activeVersionTolerance);
    }

    /**
     * Constructs a new version schedule that has NO support for the AVM.
     */
    public static AvmVersionSchedule newScheduleForNoAvmSupport() {
        return new AvmVersionSchedule(false, -1, 0);
    }

    /**
     * Returns {@code true} only if the specified avm version should be shutdown and disabled at
     * the given block number, otherwise returns {@code false}.
     *
     * @param version The avm version.
     * @param blockNumber The block number.
     * @return whether the version is prohibited at the block.
     */
    public boolean isVersionProhibitedAtBlockNumber(AvmVersion version, long blockNumber) {
        if (blockNumber < 0) {
            throw new IllegalArgumentException("Cannot query a negative block number: " + blockNumber);
        }

        if (version == AvmVersion.VERSION_1) {
            if (this.isSupportedVersion1) {
                return (blockNumber < this.blockNumberToActivateVersion1 - this.activeVersionTolerance);
            } else {
                return true;
            }
        } else {
            throw new IllegalStateException("Unknown avm version: " + version);
        }
    }

    /**
     * Returns the version of the avm which should be used to execute transactions at the specified
     * block number.
     *
     * If no versions should be active/live at this point then {@code null} is returned.
     *
     * @param blockNumber The block number to run at.
     * @return the version to use.
     */
    public AvmVersion whichVersionToRunWith(long blockNumber) {
        if (blockNumber < 0) {
            throw new IllegalArgumentException("Cannot query a negative block number: " + blockNumber);
        }

        if (this.isSupportedVersion1) {
            if (blockNumber >= this.blockNumberToActivateVersion1) {
                return AvmVersion.VERSION_1;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
}
