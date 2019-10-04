package org.aion.zero.impl.vm;

import java.util.concurrent.TimeUnit;
import org.aion.vm.avm.AvmProvider;
import org.aion.avm.stub.AvmVersion;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the {@link AvmProvider} class by making specific calls to version 2 of the avm.
 */
public class AvmProviderVersion2Test {
    private String projectRootDir;

    @Before
    public void setup() throws Exception {
        // Ensure we always begin with a disabled avm version 2.
        if (!AvmProvider.holdsLock()) {
            Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));
        }
        AvmProvider.disableAvmVersion(AvmVersion.VERSION_2);
        AvmProvider.releaseLock();
        this.projectRootDir = AvmPathManager.getPathOfProjectRootDirectory();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        // Ensure we never exit with an enabled avm version 2.
        if (!AvmProvider.holdsLock()) {
            Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));
        }
        AvmProvider.disableAvmVersion(AvmVersion.VERSION_2);
        AvmProvider.releaseLock();
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testEnableAvmWithoutOwningLock() throws Exception {
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_2, this.projectRootDir);
    }

    @Test(expected =  IllegalMonitorStateException.class)
    public void testStartAvmWithoutOwningLock() {
        AvmProvider.startAvm(AvmVersion.VERSION_2);
    }

    @Test(expected =  IllegalMonitorStateException.class)
    public void testGetAvmWithoutOwningLock() {
        AvmProvider.getAvm(AvmVersion.VERSION_2);
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testShutdownAvmWithoutOwningLock() {
        AvmProvider.shutdownAvm(AvmVersion.VERSION_2);
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testNewExternalStateBuilderWithoutOwningLock() {
        AvmProvider.newExternalStateBuilder(AvmVersion.VERSION_2);
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testDisableAvmWithoutOwningLock() throws Exception {
        AvmProvider.disableAvmVersion(AvmVersion.VERSION_2);
    }

    @Test
    public void testEnableVersionTwice() throws Exception {
        // This should be fine, the second enable does nothing.
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_2, this.projectRootDir);
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_2, this.projectRootDir);
        AvmProvider.releaseLock();
    }

    @Test(expected = IllegalStateException.class)
    public void testStartAvmWhenDisabled() throws Exception {
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));

        try {
            AvmProvider.startAvm(AvmVersion.VERSION_2);
        } catch (IllegalStateException e) {
            throw e;
        } finally{
            AvmProvider.releaseLock();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testGetAvmWhenDisabled() throws Exception {
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));

        // Wrap in a try catch so we can unlock, we still want to ensure that the exception was thrown though.
        try {
            AvmProvider.getAvm(AvmVersion.VERSION_2);
        } catch (IllegalStateException e) {
            throw e;
        } finally{
            AvmProvider.releaseLock();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testShutdownAvmWhenDisabled() throws Exception {
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));

        try {
            AvmProvider.shutdownAvm(AvmVersion.VERSION_2);
        } catch (IllegalStateException e) {
            throw e;
        } finally{
            AvmProvider.releaseLock();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testNewExternalStateBuilderWhenDisabled() throws Exception {
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));

        try {
            AvmProvider.newExternalStateBuilder(AvmVersion.VERSION_2);
        } catch (IllegalStateException e) {
            throw e;
        } finally{
            AvmProvider.releaseLock();
        }
    }

    @Test
    public void testDisableNonEnabledVersion() throws Exception {
        // This should be fine, disabling an already disabled version does nothing.
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));
        AvmProvider.disableAvmVersion(AvmVersion.VERSION_2);
        AvmProvider.releaseLock();
    }

    @Test
    public void testNewExternalStateBuilderWhenEnabled() throws Exception {
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_2, this.projectRootDir);
        Assert.assertNotNull(AvmProvider.newExternalStateBuilder(AvmVersion.VERSION_2));
        AvmProvider.releaseLock();
    }

    @Test
    public void testMultipleStartsAndShutdownsWhenEnabled() throws Exception {
        // We only want to verify that we can start and shutdown multiple times without getting an exception.
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_2, this.projectRootDir);
        AvmProvider.startAvm(AvmVersion.VERSION_2);
        AvmProvider.shutdownAvm(AvmVersion.VERSION_2);
        AvmProvider.startAvm(AvmVersion.VERSION_2);
        AvmProvider.shutdownAvm(AvmVersion.VERSION_2);
        AvmProvider.disableAvmVersion(AvmVersion.VERSION_2);
        AvmProvider.releaseLock();
    }
}
