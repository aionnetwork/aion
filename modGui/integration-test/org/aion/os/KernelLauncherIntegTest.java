package org.aion.os;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.EventPublisher;
import org.aion.mcf.config.CfgGuiLauncher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.when;

public class KernelLauncherIntegTest {
    private List<Long> cleanupPids;

    private static final long POLL_TERMINATION_INTERVAL_MILLIS = 3000;
    private static final long POLL_TERMINATION_MAX_MILLIS = 300_000;

    @Before
    public void before() {
        cleanupPids = new LinkedList<>();
    }

    @After
    public void after() throws Exception {
        cleanupPids.stream().forEach(
                pid -> {
                    try {
                        new ProcessBuilder()
                                .command("kill", "-9", String.valueOf(pid))
                                .start()
                                .waitFor(150, TimeUnit.SECONDS);
                    } catch (IOException | InterruptedException ex) {
                        // can't do much if this fails
                        ex.printStackTrace();
                    }
                });
        File maybePidFile = new File("/tmp/kernel-pid");
        maybePidFile.delete();
    }

    @Test
    public void test() throws Exception {
        CfgGuiLauncher cfg = new CfgGuiLauncher();
        cfg.setAutodetectJavaRuntime(false);
        cfg.setJavaHome(System.getProperty("java.home"));
        cfg.setAionSh("aion.sh");
        cfg.setWorkingDir(System.getProperty("user.dir") + "/../");

        List<Long> aionPidsInitial = pgrep("Aion$");
        System.out.println("Before kernel launch, PIDs for Aion kernel process: " + aionPidsInitial);

        // launch a kernel instance
        KernelLauncher kl = new KernelLauncher(cfg, EventBusRegistry.INSTANCE,
                new UnixProcessTerminator(), new UnixKernelProcessHealthChecker());
        Process kernelProc = kl.launch();
        Long nohupWrapperPid = kernelProc.pid();

        // see if a new Aion process was launched by checking unix pgrep program
        // test will not work properly if there's two instances of it running simultaneously from the same user
        Thread.sleep(3000); // pause because new process doesn't immediately show up in pgrep
        List<Long> aionPidsAfterLaunch = pgrep("Aion$");
        System.out.println("After kernel launch, PIDs for Aion kernel process: " + aionPidsAfterLaunch);
        List<Long> difference = new LinkedList<>(aionPidsAfterLaunch);
        difference.removeAll(aionPidsInitial);
        assertThat("Expected exactly one new Aion process", difference.size(), is(1));
        long aionKernelPid = difference.get(0);
        cleanupPids.add(aionKernelPid);
        System.out.println("Found new Aion process: " + aionKernelPid);

        // terminate the kernel instance using a different launcher (simulates exit GUI / reopen GUI case)
        // and make sure that PID went away
        KernelLauncher kl2 = new KernelLauncher(cfg, EventBusRegistry.INSTANCE,
                new UnixProcessTerminator(), new UnixKernelProcessHealthChecker());
        kl2.tryResume();
        kl2.terminate();

        // after receiving TERM signal, process does some clean-up before exiting, so check periodically
        // until its pid goes away, up until a maximum wait time.
        long waited = 0;
        boolean terminated = false;
        do {
            System.out.println(String.format(
                    "Waiting for kernel to terminate.  Waited for %d msec so for (timeout: %d msec)",
                    waited, POLL_TERMINATION_MAX_MILLIS));
            Thread.sleep(POLL_TERMINATION_INTERVAL_MILLIS);
            List<Long> aionPidsAfterTerm = pgrep("Aion$");
            System.out.println("After kernel launch, PIDs for Aion kernel process: " + aionPidsAfterTerm);
            terminated = !aionPidsAfterTerm.contains(aionKernelPid);
            waited += POLL_TERMINATION_INTERVAL_MILLIS;
        } while(!terminated && waited < POLL_TERMINATION_MAX_MILLIS);

        assertThat("Expected PID that was launched to no longer be running after KernelLauncher#terminate",
                terminated, is(true));
    }

    @Test
    public void testKernelKilledExternallyWhenGuiRestarts() throws Exception {
        CfgGuiLauncher cfg = new CfgGuiLauncher();
        cfg.setAutodetectJavaRuntime(false);
        cfg.setJavaHome(System.getProperty("java.home"));
        cfg.setAionSh("aion.sh");
        cfg.setWorkingDir(System.getProperty("user.dir") + "/../");

        // create the pid file, but don't run any process to simulate
        // a launched kernel that got killed
        UnixKernelProcessHealthChecker healthChecker = mock(UnixKernelProcessHealthChecker.class);
        when(healthChecker.checkIfKernelRunning(1)).thenReturn(false);
        KernelLauncher kl = new KernelLauncher(cfg, EventBusRegistry.INSTANCE,
                new UnixProcessTerminator(), healthChecker);
        kl.setAndPersistPid(1);
        kl.setCurrentInstance(null);

        kl.tryResume();

        // should notice that kernel is not actually running
        assertThat(kl.hasLaunchedInstance(), is(false));
    }

    private List<Long> pgrep(String pattern) throws IOException, InterruptedException {
        String maybeUser = System.getProperty("user.name");
        final String[] command;
        if (maybeUser != null) {
            command = new String[]{"pgrep", "-f", "-u", maybeUser, pattern};
        } else {
            command = new String[]{"pgrep", "-f", pattern};
        }
        Process proc = new ProcessBuilder().command(command).start();
        proc.waitFor(30, TimeUnit.SECONDS);

        try (
                final InputStream is = proc.getInputStream();
                final InputStreamReader isr = new InputStreamReader(is, Charsets.UTF_8);
                final BufferedReader br = new BufferedReader(isr);
        ) {
            return br.lines().flatMap(pid -> {
                try {
                    return Stream.of(Long.valueOf(pid));
                } catch (NumberFormatException nfe) {
                    System.out.println(String.format("Ignored unparseable line from pgrep: '%s'", pid));
                    return Stream.empty();
                }
            }).collect(Collectors.toList());
        }
    }

}