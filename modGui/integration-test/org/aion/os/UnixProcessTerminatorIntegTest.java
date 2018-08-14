package org.aion.os;

import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

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