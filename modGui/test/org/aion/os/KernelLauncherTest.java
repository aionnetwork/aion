package org.aion.os;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import java.io.ByteArrayInputStream;
import java.io.File;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.KernelProcEvent;
import org.aion.mcf.config.CfgGuiLauncher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

public class KernelLauncherTest {

    private KernelLaunchConfigurator klc;
    private EventBusRegistry ebr;
    private File storageLoc;
    private File pidFile;
    private UnixProcessTerminator processTerminator;
    private UnixKernelProcessHealthChecker healthChecker;

    @Before
    public void before() {
        klc = mock(KernelLaunchConfigurator.class);
        ebr = mock(EventBusRegistry.class);
        storageLoc = mock(File.class);
        pidFile = mock(File.class);
        processTerminator = mock(UnixProcessTerminator.class);
        healthChecker = mock(UnixKernelProcessHealthChecker.class);
    }

    @Test
    public void testCapturePid() throws Exception {
        KernelLauncher unit = new KernelLauncher(
            CfgGuiLauncher.DEFAULT_CONFIG, klc, ebr, processTerminator, healthChecker, storageLoc,
            pidFile);

        String expectedPid = "1337";
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(expectedPid.getBytes()));

        assertThat(unit.waitAndCapturePid(process), is(Long.valueOf(expectedPid)));
    }

    @Test(expected = KernelControlException.class)
    public void testCapturePidWhenInterrupted() throws Exception {
        KernelLauncher unit = new KernelLauncher(
            CfgGuiLauncher.DEFAULT_CONFIG, klc, ebr, processTerminator, healthChecker, storageLoc,
            pidFile);

        Process process = mock(Process.class);
        when(process.waitFor()).thenThrow(new InterruptedException());
        unit.waitAndCapturePid(process);
    }

    @Test(expected = KernelControlException.class)
    public void testCapturePidWhenStdoutReadError() throws Exception {
        KernelLauncher unit = new KernelLauncher(
            CfgGuiLauncher.DEFAULT_CONFIG, klc, ebr, processTerminator, healthChecker, storageLoc,
            pidFile);

        String stdout = "something_that_is_not_a_number";
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(stdout.getBytes()));

        unit.waitAndCapturePid(process);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTryResumeWhenAlreadyHaveInstance() throws Exception {
        when(ebr.getBus(EventBusRegistry.KERNEL_BUS)).thenReturn(mock(EventBus.class));
        KernelLauncher unit = new KernelLauncher(
            CfgGuiLauncher.DEFAULT_CONFIG, klc, ebr, processTerminator, healthChecker, storageLoc,
            pidFile);

        unit.setCurrentInstance(mock(KernelInstanceId.class));
        unit.tryResume();
    }

    @Test
    public void testTryResumeWhenPidFileNotPresent() throws Exception {
        KernelLauncher unit = new KernelLauncher(
            CfgGuiLauncher.DEFAULT_CONFIG, klc, ebr, processTerminator, healthChecker, storageLoc,
            pidFile);

        when(pidFile.exists()).thenReturn(false);
        assertThat(unit.tryResume(), is(false));
    }

    @Test
    public void testSetCurrentInstanceNonNull() throws Exception {
        EventBus eb = mock(EventBus.class);
        when(ebr.getBus(EventBusRegistry.KERNEL_BUS)).thenReturn(eb);
        KernelLauncher unit = new KernelLauncher(
            CfgGuiLauncher.DEFAULT_CONFIG, klc, ebr, processTerminator, healthChecker, storageLoc,
            pidFile);

        unit.setCurrentInstance(mock(KernelInstanceId.class));
        verify(eb).post(ArgumentMatchers.any(KernelProcEvent.KernelLaunchedEvent.class));
        assertThat(unit.hasLaunchedInstance(), is(true));
    }

    @Test
    public void testSetCurrentInstanceNull() throws Exception {
        EventBus eb = mock(EventBus.class);
        when(ebr.getBus(EventBusRegistry.KERNEL_BUS)).thenReturn(eb);
        KernelLauncher unit = new KernelLauncher(
            CfgGuiLauncher.DEFAULT_CONFIG, klc, ebr, processTerminator, healthChecker, storageLoc,
            pidFile);

        unit.setCurrentInstance(null);
        verify(eb).post(ArgumentMatchers.any(KernelProcEvent.KernelTerminatedEvent.class));
        assertThat(unit.hasLaunchedInstance(), is(false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTerminateWhenNoCurrentInstance() throws Exception {
        KernelLauncher unit = new KernelLauncher(
            CfgGuiLauncher.DEFAULT_CONFIG, klc, ebr, processTerminator, healthChecker, storageLoc,
            pidFile);
        unit.terminate();
    }

    @Test
    public void testRemovePersistedPid() throws Exception {

        KernelLauncher unit = new KernelLauncher(
            CfgGuiLauncher.DEFAULT_CONFIG, klc, ebr, processTerminator, healthChecker, storageLoc,
            pidFile);

        when(pidFile.exists()).thenReturn(true);
        when(pidFile.isFile()).thenReturn(true);
        unit.removePersistedPid();
        verify(pidFile).delete();
    }
}
