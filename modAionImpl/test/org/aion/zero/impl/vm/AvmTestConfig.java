package org.aion.zero.impl.vm;

import org.aion.zero.impl.vm.avm.AvmConfigurations;
import org.aion.zero.impl.vm.avm.schedule.AvmVersionSchedule;

public final class AvmTestConfig {

    /**
     * Uses an avm version schedule that only supports version 1 - no version 2 support at all.
     * Version 1 is active from block zero onwards.
     */
    public static void supportOnlyAvmVersion1() {
        AvmVersionSchedule schedule = new AvmVersionSchedule(new long[]{ 0 });
        String projectRoot = AvmPathManager.getPathOfProjectRootDirectory();
        AvmConfigurations.initializeConfigurationsAsReadAndWriteable(schedule, projectRoot);
    }

    /**
     * Uses an avm version schedule that supports both version 1 and 2 at the specified block
     * numbers.
     *
     * @param blockNumberToUseVersion1 The block number at which version 1 starts being used.
     * @param blockNumberToUseVersion2 The block number at which version 2 starts being used.
     */
    public static void supportBothAvmVersions(long blockNumberToUseVersion1, long blockNumberToUseVersion2) {
        AvmVersionSchedule schedule = new AvmVersionSchedule(new long[]{ blockNumberToUseVersion1, blockNumberToUseVersion2 });
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
