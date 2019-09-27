package org.aion.vm.avm;

import java.util.concurrent.TimeUnit;
import org.aion.vm.avm.resources.PathManager;
import org.aion.vm.avm.schedule.AvmVersionSchedule;
import org.aion.avm.stub.AvmVersion;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Note these tests are not concerned with testing out transactions, since that is done higher up
 * as integ tests, but with the behaviour of this class. In particular, we want to ensure that
 * transactions are processed correctly as we bounce between versions.
 */
public class AvmTransactionExecutorTest {
    private static final long VERSION_1_FORK = 10;
    private static final long VERSION_2_FORK = 20;
    private static final long TOLERANCE = 5;

    private static String projectRootDir;

    @BeforeClass
    public static void setupClass() {
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForBothVersions(VERSION_1_FORK, VERSION_2_FORK, TOLERANCE);
        AvmConfigurations.initializeConfigurationsAsReadOnly(schedule, PathManager.fetchProjectRootDir(), (t, l) -> {return true;});
        projectRootDir = PathManager.fetchProjectRootDir();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        // Ensure we never exit with enabled avm versions.
        if (!AvmProvider.holdsLock()) {
            Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));
        }
        AvmProvider.disableAvmVersion(AvmVersion.VERSION_1);
        AvmProvider.disableAvmVersion(AvmVersion.VERSION_2);
        AvmProvider.releaseLock();
    }

    @Before
    public void setup() throws Exception {
        if (!AvmProvider.holdsLock()) {
            Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));
        }
        disableAndShutdownAllVersions();

        // Verify the state of the versions.
        Assert.assertFalse(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertFalse(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        AvmProvider.releaseLock();
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testUpdateAvmsWithoutOwningLock() throws Exception {
        AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, VERSION_1_FORK);
    }

    @Test(expected = IllegalStateException.class)
    public void testUpdateAvmsBeforeAnyVersionExists() throws Exception {
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));
        AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, 0);
        AvmProvider.releaseLock();
    }

    @Test
    public void testUpdateAvmsWhenBothDisabled() throws Exception {
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));

        // 1. at a block where version 1 is the canonical version. Only version 1 should be enabled and started.
        Assert.assertEquals(AvmVersion.VERSION_1, AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, VERSION_1_FORK));

        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertFalse(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        // Reset state.
        disableAndShutdownAllVersions();
        Assert.assertFalse(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertFalse(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        // 2. at a block where version 2 is the canonical version. Only version 2 should be enabled and started.
        Assert.assertEquals(AvmVersion.VERSION_2, AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, VERSION_2_FORK));

        Assert.assertFalse(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        AvmProvider.releaseLock();
    }

    @Test
    public void testUpdateAvmsWhenOneEnabledAndShutdownOtherDisabled() throws Exception {
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));

        // We choose version 1 to be the one that's enabled.
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_1, projectRootDir);
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));

        // a) at a block where version 2 is the canonical version but when version 1 is still permitted to exist.
        Assert.assertEquals(AvmVersion.VERSION_2, AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, VERSION_2_FORK));

        // We expect version 2 to be enabled and running, and nothing to happen to version 1 (so it is still enabled)
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        // Reset state.
        disableAndShutdownAllVersions();
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_1, projectRootDir);
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));

        // b) at a block where version 2 is the canonical version but when version 1 is NOT permitted to exist.
        Assert.assertEquals(AvmVersion.VERSION_2, AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, VERSION_2_FORK + TOLERANCE));

        // Now we expect version 1 to be disabled.
        Assert.assertFalse(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        AvmProvider.releaseLock();
    }

    @Test
    public void testUpdateAvmsWhenBothEnabledAndShutdown() throws Exception {
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));

        // Set up state.
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_1, projectRootDir);
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_2, projectRootDir);
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));

        // a) at a block where version 1 is the canonical version but when version 2 is still permitted to exist.
        Assert.assertEquals(AvmVersion.VERSION_1, AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, VERSION_2_FORK - TOLERANCE));

        // We expect version 1 to be enabled and running, and nothing to happen to version 2 (so it is still enabled)
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        // Reset state.
        disableAndShutdownAllVersions();
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_1, projectRootDir);
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_2, projectRootDir);
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));

        // b) at a block where version 1 is the canonical version but when version 2 is NOT permitted to exist.
        Assert.assertEquals(AvmVersion.VERSION_1, AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, VERSION_2_FORK - TOLERANCE - 1));

        // We expect version 2 to be disabled.
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertFalse(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        AvmProvider.releaseLock();
    }

    @Test
    public void testUpdateAvmsWhenOneEnabledAndShutdownOtherEnabledAndRunning() throws Exception {
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));

        // Choose version 1 as enabled and shutdown, version 2 as enabled and running.
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_1, projectRootDir);
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_2, projectRootDir);
        AvmProvider.startAvm(AvmVersion.VERSION_2);
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        // a) at a block where version 2 is the canonical version but when version 1 is still permitted to exist.
        Assert.assertEquals(AvmVersion.VERSION_2, AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, VERSION_2_FORK + TOLERANCE - 1));

        // We expect version 1 to be left in whatever state it was in.
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        // reset state.
        disableAndShutdownAllVersions();
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_1, projectRootDir);
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_2, projectRootDir);
        AvmProvider.startAvm(AvmVersion.VERSION_2);
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        // b) at a block where version 1 is the canonical version but when version 2 is still permitted to exist.
        Assert.assertEquals(AvmVersion.VERSION_1, AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, VERSION_2_FORK - TOLERANCE));

        // We expect version 2 to be left in whatever state it was in.
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        AvmProvider.releaseLock();
    }

    @Test
    public void testUpdateAvmsWhenOneEnabledAndRunningOtherDisabled() throws Exception {
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));

        // Choose version 1 disabled, version 2 enabled and running.
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_2, projectRootDir);
        AvmProvider.startAvm(AvmVersion.VERSION_2);
        Assert.assertFalse(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        // a) at a block where version 1 is the canonical version but when version 2 is still permitted to exist.
        Assert.assertEquals(AvmVersion.VERSION_1, AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, VERSION_2_FORK - TOLERANCE));

        // We expect version 2 to be left in whatever state it was in.
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        // Reset state.
        disableAndShutdownAllVersions();
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_2, projectRootDir);
        AvmProvider.startAvm(AvmVersion.VERSION_2);
        Assert.assertFalse(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        // b) at a block where version 2 is the canonical version but when version 1 is NOT permitted to exist.
        Assert.assertEquals(AvmVersion.VERSION_2, AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, VERSION_2_FORK + TOLERANCE));

        // We expect version 1 to shutdown and disabled.
        Assert.assertFalse(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        AvmProvider.releaseLock();
    }

    @Test
    public void testUpdateAvmsWhenOneEnabledAndRunningOtherEnabledAndShutdown() throws Exception {
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));

        // Choose version 1 enabled and running, version 2 enabled and shutdown.
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_1, projectRootDir);
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_2, projectRootDir);
        AvmProvider.startAvm(AvmVersion.VERSION_1);
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        // a) at a block where version 1 is the canonical version but where version 2 is still permitted to exist.
        Assert.assertEquals(AvmVersion.VERSION_1, AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, VERSION_2_FORK - TOLERANCE));

        // We expect version 2 to be left in whatever state it was in.
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        // Reset state.
        disableAndShutdownAllVersions();
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_1, projectRootDir);
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_2, projectRootDir);
        AvmProvider.startAvm(AvmVersion.VERSION_1);
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        // b) at a block where version 1 is the canonical version but where version 2 is NOT permitted to exist.
        Assert.assertEquals(AvmVersion.VERSION_1, AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, VERSION_2_FORK - TOLERANCE - 1));

        // We expect version 2 to be shutdown and disabled.
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertFalse(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        AvmProvider.releaseLock();
    }

    @Test
    public void testUpdateAvmsWhenBothEnabledAndRunning() throws Exception {
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));

        // setup state.
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_1, projectRootDir);
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_2, projectRootDir);
        AvmProvider.startAvm(AvmVersion.VERSION_1);
        AvmProvider.startAvm(AvmVersion.VERSION_2);
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        // a) at a block where version 2 is the canonical version but where version 1 is still permitted to exist.
        Assert.assertEquals(AvmVersion.VERSION_2, AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, VERSION_2_FORK + TOLERANCE - 1));

        // We expect version 1 to be left in whatever state it was in.
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        // b) at a block where version 2 is the canonical version but where version 1 is NOT permitted to exist.
        Assert.assertEquals(AvmVersion.VERSION_2, AvmTransactionExecutor.updateAvmsAndGetVersionToUse(projectRootDir, VERSION_2_FORK + TOLERANCE));

        // We expect version 1 to be shutdown and disabled.
        Assert.assertFalse(AvmProvider.isVersionEnabled(AvmVersion.VERSION_1));
        Assert.assertFalse(AvmProvider.isAvmRunning(AvmVersion.VERSION_1));
        Assert.assertTrue(AvmProvider.isVersionEnabled(AvmVersion.VERSION_2));
        Assert.assertTrue(AvmProvider.isAvmRunning(AvmVersion.VERSION_2));

        AvmProvider.releaseLock();
    }

    private static void disableAndShutdownAllVersions() throws Exception {
        // Disable will also shutdown for us.
        AvmProvider.disableAvmVersion(AvmVersion.VERSION_1);
        AvmProvider.disableAvmVersion(AvmVersion.VERSION_2);
    }
}
