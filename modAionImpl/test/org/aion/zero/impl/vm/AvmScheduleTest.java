package org.aion.zero.impl.vm;

import org.aion.avm.stub.AvmVersion;
import org.aion.zero.impl.vm.avm.schedule.AvmVersionSchedule;
import org.junit.Assert;
import org.junit.Test;

public class AvmScheduleTest {

    @Test(expected = IllegalArgumentException.class)
    public void testCreateScheduleForTooManyVersions() {
        new AvmVersionSchedule(new long[]{ 1, 2, 3 });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateScheduleTwoVersionsSameBlockNumber() {
        new AvmVersionSchedule(new long[]{ 5, 5 });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateScheduleTwoVersionsOutOfOrder() {
        new AvmVersionSchedule(new long[]{ 5, 4 });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateScheduleWithNegativeBlockNumber() {
        new AvmVersionSchedule(new long[]{ -1, 0 });
    }

    @Test
    public void testEmptySchedule() {
        AvmVersionSchedule schedule = new AvmVersionSchedule(new long[0]);
        Assert.assertNull(schedule.whichVersionToRunWith(0));
        Assert.assertNull(schedule.whichVersionToRunWith(Long.MAX_VALUE));
    }

    @Test
    public void testSingleVersionSchedule() {
        AvmVersionSchedule schedule = new AvmVersionSchedule(new long[]{ 50_000 });
        Assert.assertNull(schedule.whichVersionToRunWith(0));
        Assert.assertNull(schedule.whichVersionToRunWith(49_999));
        Assert.assertEquals(AvmVersion.VERSION_1, schedule.whichVersionToRunWith(50_000));
        Assert.assertEquals(AvmVersion.VERSION_1, schedule.whichVersionToRunWith(Long.MAX_VALUE));
    }

    @Test
    public void testDoubleVersionSchedule() {
        AvmVersionSchedule schedule = new AvmVersionSchedule(new long[]{ 50_000, Integer.MAX_VALUE });
        Assert.assertNull(schedule.whichVersionToRunWith(0));
        Assert.assertNull(schedule.whichVersionToRunWith(49_999));
        Assert.assertEquals(AvmVersion.VERSION_1, schedule.whichVersionToRunWith(50_000));
        Assert.assertEquals(AvmVersion.VERSION_1, schedule.whichVersionToRunWith(Integer.MAX_VALUE - 1));
        Assert.assertEquals(AvmVersion.VERSION_2, schedule.whichVersionToRunWith(Integer.MAX_VALUE));
        Assert.assertEquals(AvmVersion.VERSION_2, schedule.whichVersionToRunWith(Long.MAX_VALUE));
    }
}
