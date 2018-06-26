package org.aion.os;

import com.google.common.eventbus.EventBus;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.KernelProcEvent;
import org.aion.mcf.config.CfgGuiLauncher;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import java.io.ByteArrayInputStream;
import java.io.File;

import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KernelLauncherTest {
    @Test
    public void testCtorWhenConfigHasPidFileOverride() {

    }

    @Test
    public void testCapturePid() throws Exception {
        KernelLaunchConfigurator klc = mock(KernelLaunchConfigurator.class);
        EventBusRegistry ebr = mock(EventBusRegistry.class);
        KernelLauncher unit = new KernelLauncher(
                CfgGuiLauncher.AUTODETECTING_CONFIG, klc, ebr, mock(File.class) /* not used */);

        String expectedPid = "1337";
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(expectedPid.getBytes()));

        assertThat(unit.waitAndCapturePid(process), is(Long.valueOf(expectedPid)));
    }

    @Test(expected = KernelControlException.class)
    public void testCapturePidWhenInterrupted() throws Exception {
        KernelLaunchConfigurator klc = mock(KernelLaunchConfigurator.class);
        EventBusRegistry ebr = mock(EventBusRegistry.class);
        KernelLauncher unit = new KernelLauncher(
                CfgGuiLauncher.AUTODETECTING_CONFIG, klc, ebr, mock(File.class) /* not used */);

        Process process = mock(Process.class);
        when(process.waitFor()).thenThrow(new InterruptedException());
        unit.waitAndCapturePid(process);
    }

    @Test(expected = KernelControlException.class)
    public void testCapturePidWhenStdoutReadError() throws Exception {
        KernelLaunchConfigurator klc = mock(KernelLaunchConfigurator.class);
        EventBusRegistry ebr = mock(EventBusRegistry.class);
        KernelLauncher unit = new KernelLauncher(
                CfgGuiLauncher.AUTODETECTING_CONFIG, klc, ebr, mock(File.class) /* not used */);

        String stdout = "something_that_is_not_a_number";
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(stdout.getBytes()));

        unit.waitAndCapturePid(process);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTryResumeWhenAlreadyHaveInstance() throws Exception {
        KernelLaunchConfigurator klc = mock(KernelLaunchConfigurator.class);
        EventBusRegistry ebr = mock(EventBusRegistry.class);
        when(ebr.getBus(EventBusRegistry.KERNEL_BUS)).thenReturn(mock(EventBus.class));
        KernelLauncher unit = new KernelLauncher(
                CfgGuiLauncher.AUTODETECTING_CONFIG, klc, ebr, mock(File.class) /* not used */);

        unit.setCurrentInstance(mock(KernelInstanceId.class));
        unit.tryResume();
    }

    @Test
    public void testTryResumeWhenPidFileNotPresent() throws Exception {
        KernelLaunchConfigurator klc = mock(KernelLaunchConfigurator.class);
        EventBusRegistry ebr = mock(EventBusRegistry.class);
        File pidFile = mock(File.class);
        KernelLauncher unit = new KernelLauncher(
                CfgGuiLauncher.AUTODETECTING_CONFIG, klc, ebr, pidFile);

        when(pidFile.exists()).thenReturn(false);
        assertThat(unit.tryResume(), is(false));
    }

    @Test
    public void testTerminateWhenProcessExists() throws Exception {

    }

    @Test
    public void testTerminateWhenProcessNotExists() throws Exception {

    }

    @Test
    public void testSetCurrentInstanceNonNull() throws Exception {
        KernelLaunchConfigurator klc = mock(KernelLaunchConfigurator.class);
        EventBusRegistry ebr = mock(EventBusRegistry.class);
        EventBus eb = mock(EventBus.class);
        when(ebr.getBus(EventBusRegistry.KERNEL_BUS)).thenReturn(eb);
        KernelLauncher unit = new KernelLauncher(
                CfgGuiLauncher.AUTODETECTING_CONFIG, klc, ebr, mock(File.class) /* not used */);

        unit.setCurrentInstance(mock(KernelInstanceId.class));
        verify(eb).post(ArgumentMatchers.any(KernelProcEvent.KernelLaunchedEvent.class));
        assertThat(unit.hasLaunchedInstance(), is(true));
    }

    @Test
    public void testSetCurrentInstanceNull() throws Exception {
        KernelLaunchConfigurator klc = mock(KernelLaunchConfigurator.class);
        EventBusRegistry ebr = mock(EventBusRegistry.class);
        EventBus eb = mock(EventBus.class);
        when(ebr.getBus(EventBusRegistry.KERNEL_BUS)).thenReturn(eb);
        KernelLauncher unit = new KernelLauncher(
                CfgGuiLauncher.AUTODETECTING_CONFIG, klc, ebr, mock(File.class) /* not used */);

        unit.setCurrentInstance(null);
        verify(eb).post(ArgumentMatchers.any(KernelProcEvent.KernelTerminatedEvent.class));
        assertThat(unit.hasLaunchedInstance(), is(false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTerminateWhenNoCurrentInstance() throws Exception {
        EventBusRegistry ebr = mock(EventBusRegistry.class);
        KernelLauncher unit = new KernelLauncher(CfgGuiLauncher.AUTODETECTING_CONFIG, ebr);
        unit.terminate();
    }

    @Test
    public void testRemovePersistedPid() throws Exception {
        KernelLaunchConfigurator klc = mock(KernelLaunchConfigurator.class);
        EventBusRegistry ebr = mock(EventBusRegistry.class);
        File pidFile = mock(File.class);
        KernelLauncher unit = new KernelLauncher(
                CfgGuiLauncher.AUTODETECTING_CONFIG, klc, ebr, pidFile);

        when(pidFile.exists()).thenReturn(true);
        when(pidFile.isFile()).thenReturn(true);
        unit.removePersistedPid();
        verify(pidFile).delete();
    }

    /*
    @Test
    public void test() throws Exception {
        //Process proc = new KernelLauncher(CfgGuiLauncher.AUTODETECTING_CONFIG).launch();
        Thread.sleep(15000);
    }
    */

    /*
    @Test
    public void testLaunchAuto() throws Exception {
        KernelLaunchConfigurator klc = mock(KernelLaunchConfigurator.class);
        KernelLauncher unit = new KernelLauncher(CfgGuiLauncher.AUTODETECTING_CONFIG, klc);

        ProcessBuilder processBuilder = mock(ProcessBuilder.class);
        unit.launch(processBuilder);
        verify(klc).configureAutomatically(processBuilder);
        verify(processBuilder).start();
    }

    @Test
    public void testLaunchManual() throws Exception {
        KernelLaunchConfigurator klc = mock(KernelLaunchConfigurator.class);
        CfgGuiLauncher cfg = new CfgGuiLauncher();
        cfg.setAutodetectJavaRuntime(false);
        KernelLauncher unit = new KernelLauncher(CfgGuiLauncher.AUTODETECTING_CONFIG, klc);

        ProcessBuilder processBuilder = mock(ProcessBuilder.class);
        unit.launch(processBuilder);
        verify(klc).configureManually(eq(processBuilder), any(CfgGuiLauncher.class));
        verify(processBuilder).start();
    }
    */
}