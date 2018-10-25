/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.os;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

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
        UnixProcessTerminator unit =
                new UnixProcessTerminator(
                        timeoutUntilSigKill,
                        timeoutUntilGiveUp,
                        pollInterval,
                        initialWait,
                        unixCommandRunner);
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
        UnixProcessTerminator unit =
                new UnixProcessTerminator(
                        Duration.ofMillis(1) /*timeoutUntilSigKill*/,
                        timeoutUntilGiveUp,
                        pollInterval,
                        initialWait,
                        unixCommandRunner);
        when(unixCommandRunner.callPs(pid)).thenReturn(Collections.singletonList("anyText"));
        Mockito.doAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocationOnMock)
                                    throws Exception {
                                when(unixCommandRunner.callPs(pid))
                                        .thenReturn(Collections.singletonList("anyText"))
                                        .thenReturn(Collections.emptyList());
                                return null;
                            }
                        })
                .when(unixCommandRunner)
                .sendSigKill(pid);

        unit.terminateAndAwait(kernelId);
        verify(unixCommandRunner).sendSigterm(pid);
        verify(unixCommandRunner).sendSigKill(pid);
    }

    @Test(expected = KernelControlException.class)
    public void terminateAndAwaitWhenSigTermAndSigKillTimeout() throws Exception {
        UnixProcessTerminator unit =
                new UnixProcessTerminator(
                        Duration.ofMillis(1) /*timeoutUntilSigKill*/,
                        Duration.ofMillis(1) /*timeoutUntilGiveUp*/,
                        pollInterval,
                        initialWait,
                        unixCommandRunner);
        when(unixCommandRunner.callPs(pid)).thenReturn(Collections.singletonList("anyText"));
        unit.terminateAndAwait(kernelId);
        verify(unixCommandRunner).sendSigterm(pid);
        verify(unixCommandRunner).sendSigKill(pid);
        verify(unixCommandRunner, atLeast(2)).callPs(pid);
    }

    @Test(expected = KernelControlException.class)
    public void terminateAndAwaitWhenUnixCommandIoError() throws Exception {
        UnixProcessTerminator unit =
                new UnixProcessTerminator(
                        Duration.ofMillis(1) /*timeoutUntilSigKill*/,
                        Duration.ofMillis(1) /*timeoutUntilGiveUp*/,
                        pollInterval,
                        initialWait,
                        unixCommandRunner);
        when(unixCommandRunner.callPs(pid)).thenThrow(new IOException("whatever"));
        unit.terminateAndAwait(kernelId);
    }
}
