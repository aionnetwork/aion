package org.aion.os;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UnixProcessTerminatorTest {
    private long pid = 1337;

    private UnixProcessTerminator.UnixCommandRunner unixCommandRunner;
    private KernelInstanceId kernelId = new KernelInstanceId(pid);
    private Duration timeoutUntilSigKill = Duration.ofMillis(1000);
    private Duration timeoutUntilGiveUp = Duration.ofMillis(1000);
    private Duration pollInterval = Duration.ofMillis(10);
    private Duration initialWait = Duration.ofMillis(1);

    @Before
    public void before() {
        unixCommandRunner = mock(UnixProcessTerminator.UnixCommandRunner.class);
    }

    @Test
    public void terminateAndAwaitWhenSigTermSucceeds() throws Exception {
        UnixProcessTerminator unit = new UnixProcessTerminator(
                timeoutUntilSigKill,
                timeoutUntilGiveUp,
                pollInterval,
                initialWait,
                unixCommandRunner
        );
        when(unixCommandRunner.callPs(pid))
                .thenReturn(Collections.singletonList("anyText"))
                .thenReturn(Collections.emptyList());
        unit.terminateAndAwait(kernelId);
        verify(unixCommandRunner, atLeast(2)).callPs(pid);
        verify(unixCommandRunner).sendSigterm(pid);
        verify(unixCommandRunner, never()).sendSigKill(anyInt());
    }

    @Test
    public void terminateAndAwaitWhenSigTermTimeoutSigKillSucceeds() throws Exception {
        UnixProcessTerminator unit = new UnixProcessTerminator(
                Duration.ofMillis(1) /*timeoutUntilSigKill*/,
                timeoutUntilGiveUp,
                pollInterval,
                initialWait,
                unixCommandRunner
        );
        when(unixCommandRunner.callPs(pid)).thenReturn(Collections.singletonList("anyText"));
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                when(unixCommandRunner.callPs(pid))
                        .thenReturn(Collections.singletonList("anyText"))
                        .thenReturn(Collections.emptyList());
                return null;
            }
        }).when(unixCommandRunner).sendSigKill(pid);

        unit.terminateAndAwait(kernelId);
        verify(unixCommandRunner).sendSigterm(pid);
        verify(unixCommandRunner).sendSigKill(pid);
    }

    @Test(expected = KernelControlException.class)
    public void terminateAndAwaitWhenSigTermAndSigKillTimeout() throws Exception {
        UnixProcessTerminator unit = new UnixProcessTerminator(
                Duration.ofMillis(1) /*timeoutUntilSigKill*/,
                Duration.ofMillis(1) /*timeoutUntilGiveUp*/,
                pollInterval,
                initialWait,
                unixCommandRunner
        );
        when(unixCommandRunner.callPs(pid)).thenReturn(Collections.singletonList("anyText"));
        unit.terminateAndAwait(kernelId);
        verify(unixCommandRunner).sendSigterm(pid);
        verify(unixCommandRunner).sendSigKill(pid);
        verify(unixCommandRunner, atLeast(2)).callPs(pid);
    }

    @Test(expected = KernelControlException.class)
    public void terminateAndAwaitWhenUnixCommandIoError() throws Exception {
        UnixProcessTerminator unit = new UnixProcessTerminator(
                Duration.ofMillis(1) /*timeoutUntilSigKill*/,
                Duration.ofMillis(1) /*timeoutUntilGiveUp*/,
                pollInterval,
                initialWait,
                unixCommandRunner
        );
        when(unixCommandRunner.callPs(pid)).thenThrow(new IOException("whatever"));
        unit.terminateAndAwait(kernelId);
    }
}