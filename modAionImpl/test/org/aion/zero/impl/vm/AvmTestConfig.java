package org.aion.zero.impl.vm;

import org.aion.zero.impl.vm.avm.AvmConfigurations;
import org.aion.zero.impl.vm.avm.schedule.AvmVersionSchedule;

public final class AvmTestConfig {

    /**
     * Uses an avm version schedule that only supports version 1 - no version 2 support at all.
     * Version 1 is active from block zero onwards.
     */
    public static void supportOnlyAvmVersion1() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForOnlySingleVersionSupport(0, 0);
        String projectRoot = AvmPathManager.getPathOfProjectRootDirectory();
        AvmConfigurations.initializeConfigurationsAsReadAndWriteable(schedule, projectRoot);
    }

    /**
     * Uses an avm version schedule that supports both version 1 and 2 at the specified block
     * numbers.
     *
     * The tolerance parameter is the amount of blocks (plus/minus) to keep an avm version alive
     * at a block where it is no longer used.
     *
     * @param blockNumberToUseVersion1 The block number at which version 1 starts being used.
     * @param blockNumberToUseVersion2 The block number at which version 2 starts being used.
     * @param activeVersionTolerance The amount of blocks to keep an old version alive.
     */
    public static void supportBothAvmVersions(long blockNumberToUseVersion1, long blockNumberToUseVersion2, long activeVersionTolerance) {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForBothVersions(blockNumberToUseVersion1, blockNumberToUseVersion2, activeVersionTolerance);
        String projectRoot = AvmPathManager.getPathOfProjectRootDirectory();
        AvmConfigurations.initializeConfigurationsAsReadAndWriteable(schedule, projectRoot);
    }

    /**
     * Clears the current avm configurations.
     */
    public static void clearConfigurations() {
        AvmConfigurations.clear();
    }
}
