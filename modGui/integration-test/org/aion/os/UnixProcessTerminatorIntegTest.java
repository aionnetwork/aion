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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.time.Duration;
import org.junit.Test;

public class UnixProcessTerminatorIntegTest {

    @Test
    public void testTerminateAndAwaitWhenSigterm() throws Exception {
        // run cat command with no inputs so it goes forever
        Process fakeAionProc = new ProcessBuilder()
            .command("cat")
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
        try {
            assertThat(fakeAionProc.isAlive(), is(true)); // sanity check of test

            KernelInstanceId fakeKernelId = new KernelInstanceId(fakeAionProc.pid());
            UnixProcessTerminator processTerminator = new UnixProcessTerminator();
            processTerminator.terminateAndAwait(fakeKernelId);

            assertThat(fakeAionProc.isAlive(), is(false));
        } finally {
            // clean up
            fakeAionProc.destroyForcibly();
        }
    }

    @Test
    public void testTerminateAndAwaitWhenSigkill() throws Exception {
        // run cat command with no inputs so it goes forever and make it immune to SIGTERMs
        Process fakeAionProc = new ProcessBuilder()
            .command("/bin/bash", "-c", "trap \"\" SIGTERM; cat")
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
        try {
            assertThat(fakeAionProc.isAlive(), is(true)); // sanity check the test

            KernelInstanceId fakeKernelId = new KernelInstanceId(fakeAionProc.pid());
            UnixProcessTerminator processTerminator = new UnixProcessTerminator(
                Duration.ofMillis(1), // timeout until sigkill
                UnixProcessTerminator.DEFAULT_TIMEOUT_UNTIL_GIVE_UP,
                UnixProcessTerminator.DEFAULT_POLL_INTERVAL,
                Duration.ofMillis(1) // initial wait
            );
            processTerminator.terminateAndAwait(fakeKernelId);

            assertThat(fakeAionProc.isAlive(), is(false));
        } finally {
            fakeAionProc.destroyForcibly();
        }
    }
}