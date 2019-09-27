package org.aion.vm.avm.schedule;

import org.aion.avm.stub.AvmVersion;

/**
 * A schedule around which versions should be started and stopped and when.
 */
public final class AvmVersionSchedule {
    private final boolean isSupportedVersion1;
    private final boolean isSupportedVersion2;
    private final long blockNumberToActivateVersion1;
    private final long blockNumberToActivateVersion2;
    private final long activeVersionTolerance;

    private AvmVersionSchedule(boolean isSupportedVersion1, long blockNumberToActivateVersion1, boolean isSupportedVersion2, long blockNumberToActivateVersion2, long activeVersionTolerance) {
        if (!isSupportedVersion1 && isSupportedVersion2) {
            throw new IllegalArgumentException("Cannot create schedule with no version 1 support but with version 2 support!");
        }
        if (activeVersionTolerance < 0) {
            throw new IllegalArgumentException("Cannot create schedule with negative activeVersionTolerance!");
        }
        if (blockNumberToActivateVersion1 > blockNumberToActivateVersion1 + activeVersionTolerance) {
            throw new IllegalArgumentException("Cannot create schedule with activeVersionTolerance so large it causes overflow!");
        }
        if (blockNumberToActivateVersion2 > blockNumberToActivateVersion2 + activeVersionTolerance) {
            throw new IllegalArgumentException("Cannot create schedule with activeVersionTolerance so large it causes overflow!");
        }

        this.isSupportedVersion1 = isSupportedVersion1;
        this.blockNumberToActivateVersion1 = blockNumberToActivateVersion1;
        this.isSupportedVersion2 = isSupportedVersion2;
        this.blockNumberToActivateVersion2 = blockNumberToActivateVersion2;
        this.activeVersionTolerance = activeVersionTolerance;
    }

    /**
     * Constructs a new version schedule that has support for two versions of the AVM.
     *
     * AVM version 1 will be active beginning at block number {@code blockNumberToActivateVersion1}
     * and then AVM version 2 will become active at block number {@code blockNumberToActivateVersion2}.
     *
     * The {@code activeVersionTolerance} parameter indicates how many blocks above or below the
     * point at which a version becomes active or inactive (superseded by the next version) that
     * the version may be permitted to stay alive. This parameter exists because starting and
     * shutting down the avm is expensive and this may prevent necessary shutdowns and start ups
     * during times like reorgs or syncing.
     *
     * AVM version 1 will be permitted to remain enabled over the following block numbers:
     * [blockNumberToActivateVersion1 - activeVersionTolerance, blockNumberToActivateVersion2 + activeVersionTolerance)
     *
     * AVM version 2 will be permitted to remain enabled over the following block numbers:
     * [blockNumberToActivateVersion2 - activeVersionTolerance, infinity)
     *
     * @param blockNumberToActivateVersion1 The block at which version 1 becomes the version to run.
     * @param blockNumberToActivateVersion2 The block at which version 2 becomes the version to run.
     * @param activeVersionTolerance The number of blocks both versions can stay alive.
     */
    public static AvmVersionSchedule newScheduleForBothVersions(long blockNumberToActivateVersion1, long blockNumberToActivateVersion2, long activeVersionTolerance) {
        if (blockNumberToActivateVersion1 < 0) {
            throw new IllegalArgumentException("Cannot create schedule with negative version 1 active block!");
        }
        if (blockNumberToActivateVersion2 < 0) {
            throw new IllegalArgumentException("Cannot create schedule with negative version 2 active block!");
        }
        if (blockNumberToActivateVersion1 >= blockNumberToActivateVersion2) {
            throw new IllegalArgumentException("Cannot create schedule with version 1 active later than version 2!");
        }

        return new AvmVersionSchedule(true, blockNumberToActivateVersion1, true, blockNumberToActivateVersion2, activeVersionTolerance);
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

        return new AvmVersionSchedule(true, blockNumberToActivateAvm, false, -1, activeVersionTolerance);
    }

    /**
     * Constructs a new version schedule that has NO support for the AVM.
     */
    public static AvmVersionSchedule newScheduleForNoAvmSupport() {
        return new AvmVersionSchedule(false, -1, false,  -1, 0);
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
            if (this.isSupportedVersion1 && this.isSupportedVersion2) {
                return (blockNumber < this.blockNumberToActivateVersion1 - this.activeVersionTolerance) || (blockNumber >= this.blockNumberToActivateVersion2 + this.activeVersionTolerance);
            } else if (this.isSupportedVersion1 && !this.isSupportedVersion2) {
                return blockNumber < this.blockNumberToActivateVersion1 - this.activeVersionTolerance;
            } else {
                return true;
            }
        } else if (version == AvmVersion.VERSION_2) {
            if (this.isSupportedVersion2) {
                return (blockNumber < this.blockNumberToActivateVersion2 - this.activeVersionTolerance);
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

        if (this.isSupportedVersion1 && this.isSupportedVersion2) {
            if ((blockNumber >= this.blockNumberToActivateVersion1) && (blockNumber < this.blockNumberToActivateVersion2)) {
                return AvmVersion.VERSION_1;
            } else if (blockNumber >= this.blockNumberToActivateVersion2) {
                return AvmVersion.VERSION_2;
            } else {
                return null;
            }
        } else if (this.isSupportedVersion1 && !this.isSupportedVersion2) {
            return (blockNumber >= this.blockNumberToActivateVersion1) ? AvmVersion.VERSION_1 : null;
        } else {
            return null;
        }
    }
}
