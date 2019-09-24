package org.aion.avm.provider;

import org.aion.avm.provider.schedule.AvmVersionSchedule;
import org.aion.avm.stub.AvmVersion;
import org.junit.Assert;
import org.junit.Test;

public class AvmVersionScheduleTest {

    @Test
    public void testWhichVersionUnderSingleAvmSupport() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForOnlySingleVersionSupport(15, 7);
        Assert.assertNull(schedule.whichVersionToRunWith(0));
        Assert.assertNull(schedule.whichVersionToRunWith(14));
        Assert.assertEquals(AvmVersion.VERSION_1, schedule.whichVersionToRunWith(15));
        Assert.assertEquals(AvmVersion.VERSION_1, schedule.whichVersionToRunWith(Long.MAX_VALUE));
    }

    @Test
    public void testWhichVersionUnderNoAvmSupport() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForNoAvmSupport();
        Assert.assertNull(schedule.whichVersionToRunWith(0));
        Assert.assertNull(schedule.whichVersionToRunWith(Long.MAX_VALUE));
    }

    @Test
    public void testProhibitionWithZeroToleranceUsingSingleAvmSupport() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForOnlySingleVersionSupport(0, 0);
        // Version 1 is permitted over blocks 0 onwards.

        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 0));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 1));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, Long.MAX_VALUE));
    }

    @Test
    public void testProhibitionWithOneToleranceUsingSingleAvmSupport() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForOnlySingleVersionSupport(16, 1);
        // Version 1 is permitted over blocks 15 onwards.

        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 0));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 15));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 16));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, Long.MAX_VALUE));
    }

    @Test
    public void testProhibitionWithToleranceLArgerThanNumOfFork1UnderSingleAvmSupport() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForOnlySingleVersionSupport(7000, 8000);
        // Version 1 is permitted over blocks 0 onwards.

        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 0));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 6999));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 7000));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 7001));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, Long.MAX_VALUE));
    }

    @Test
    public void testProhibitionUnderNoAvmSupport() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForNoAvmSupport();

        // Ensure version 1 is always prohibited.
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 0));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, Long.MAX_VALUE / 2));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, Long.MAX_VALUE));
    }
}
