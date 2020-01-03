package org.aion.api.server;

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
     * Clears the current avm configurations.
     */
    public static void clearConfigurations() {
        AvmConfigurations.clear();
    }
}
