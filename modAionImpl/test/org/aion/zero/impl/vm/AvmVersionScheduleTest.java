package org.aion.zero.impl.vm;

import org.aion.vm.avm.schedule.AvmVersionSchedule;
import org.aion.avm.stub.AvmVersion;
import org.junit.Assert;
import org.junit.Test;

public class AvmVersionScheduleTest {

    @Test(expected = IllegalArgumentException.class)
    public void testTwoVersionsAtSameBlock() {
        AvmVersionSchedule.newScheduleForBothVersions(3, 3, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVersion1AfterVersion2() {
        AvmVersionSchedule.newScheduleForBothVersions(2, 1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeBlockNumber() {
        AvmVersionSchedule.newScheduleForBothVersions(-1, 1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeActiveTolerance() {
        AvmVersionSchedule.newScheduleForBothVersions(0, 1, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testActiveToleranceOverflow() {
        AvmVersionSchedule.newScheduleForBothVersions(0, Long.MAX_VALUE, 1);
    }

    @Test
    public void testWhichVersionToRun1() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForBothVersions(0, 10, 3);
        Assert.assertEquals(AvmVersion.VERSION_1, schedule.whichVersionToRunWith(0));
        Assert.assertEquals(AvmVersion.VERSION_1, schedule.whichVersionToRunWith(9));
        Assert.assertEquals(AvmVersion.VERSION_2, schedule.whichVersionToRunWith(10));
        Assert.assertEquals(AvmVersion.VERSION_2, schedule.whichVersionToRunWith(Long.MAX_VALUE));
    }

    @Test
    public void testWhichVersionToRun2() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForBothVersions(12, 31, 3);
        Assert.assertNull(schedule.whichVersionToRunWith(0));
        Assert.assertNull(schedule.whichVersionToRunWith(11));
        Assert.assertEquals(AvmVersion.VERSION_1, schedule.whichVersionToRunWith(12));
        Assert.assertEquals(AvmVersion.VERSION_1, schedule.whichVersionToRunWith(30));
        Assert.assertEquals(AvmVersion.VERSION_2, schedule.whichVersionToRunWith(31));
        Assert.assertEquals(AvmVersion.VERSION_2, schedule.whichVersionToRunWith(Long.MAX_VALUE));
    }

    @Test
    public void testWhichVersionToRun3() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForBothVersions(61, 62, 30);
        Assert.assertNull(schedule.whichVersionToRunWith(0));
        Assert.assertNull(schedule.whichVersionToRunWith(60));
        Assert.assertEquals(AvmVersion.VERSION_1, schedule.whichVersionToRunWith(61));
        Assert.assertEquals(AvmVersion.VERSION_2, schedule.whichVersionToRunWith(62));
        Assert.assertEquals(AvmVersion.VERSION_2, schedule.whichVersionToRunWith(Long.MAX_VALUE));
    }

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
    public void testProhibitionWithZeroTolerance() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForBothVersions(0, 10, 0);
        // Version 1 is permitted over blocks [0, 9]
        // Version 2 is permitted over blocks 10 onwards.

        // Verify when version 1 is prohibited.
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 0));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 9));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 10));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, Long.MAX_VALUE));

        // Verify when version 2 is prohibited.
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 0));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 9));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 10));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, Long.MAX_VALUE));
    }

    @Test
    public void testProhibitionWithZeroToleranceUsingSingleAvmSupport() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForOnlySingleVersionSupport(0, 0);
        // Version 1 is permitted over blocks 0 onwards.

        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 0));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 1));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, Long.MAX_VALUE));

        // Ensure that version 2 is always prohibited.
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 0));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 1));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, Long.MAX_VALUE));
    }

    @Test
    public void testProhibitionWithOneTolerance() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForBothVersions(0, 16, 1);
        // Version 1 is permitted over blocks [0, 16]
        // Version 2 is permitted over blocks 15 onwards.

        // Verify when version 1 is prohibited.
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 0));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 15));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 16));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 17));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, Long.MAX_VALUE));

        // Verify when version 2 is prohibited.
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 0));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 14));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 15));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 16));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, Long.MAX_VALUE));
    }

    @Test
    public void testProhibitionWithOneToleranceUsingSingleAvmSupport() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForOnlySingleVersionSupport(16, 1);
        // Version 1 is permitted over blocks 15 onwards.

        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 0));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 15));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 16));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, Long.MAX_VALUE));

        // Ensure that version 2 is always prohibited.
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 0));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 15));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 16));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, Long.MAX_VALUE));
    }

    @Test
    public void testProhibitionWhenForksAreBlockNeighboursWithLargeTolerance() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForBothVersions(11, 12, 8);
        // Version 1 is permitted over blocks [3, 19]
        // Version 2 is permitted over blocks 4 onwards.

        // Verify when version 1 is prohibited.
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 0));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 2));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 3));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 10));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 11));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 12));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 13));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 19));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 20));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, Long.MAX_VALUE));

        // Verify when version 2 is prohibited.
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 0));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 3));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 4));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 10));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 11));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 12));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 13));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, Long.MAX_VALUE));
    }

    @Test
    public void testProhibitionWithToleranceLargerThanNumOfFork2() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForBothVersions(100, 1200, 8000);
        // Version 1 is permitted over blocks [0, 9199]
        // Version 2 is permitted over blocks 0 onwards.

        // Verify when version 1 is prohibited.
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 0));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 100));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 1199));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 1200));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 1201));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 9199));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 9200));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, Long.MAX_VALUE));

        // Verify when version 2 is prohibited.
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 0));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 1199));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 1200));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 1201));
        Assert.assertFalse(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, Long.MAX_VALUE));
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

        // Ensure that version 2 is always prohibited.
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 0));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 6999));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 7000));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 7001));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, Long.MAX_VALUE));
    }

    @Test
    public void testProhibitionUnderNoAvmSupport() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForNoAvmSupport();

        // Ensure both versions are always prohibited.
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, 0));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, Long.MAX_VALUE / 2));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_1, Long.MAX_VALUE));

        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, 0));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, Long.MAX_VALUE / 2));
        Assert.assertTrue(schedule.isVersionProhibitedAtBlockNumber(AvmVersion.VERSION_2, Long.MAX_VALUE));
    }
}
