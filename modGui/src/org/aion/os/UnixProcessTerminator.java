package org.aion.os;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Terminates UNIX processes.
 */
public class UnixProcessTerminator {
    private final UnixCommandRunner unixCommandRunner;
    private final Duration timeoutUntilSigKill;
    private final Duration timeoutUntilGiveUp;
    private final Duration pollInterval;
    private final Duration initialWait;

    /** default timeout until using forcing termination (SIGKILL) */
    public static final Duration DEFAULT_TIMEOUT_UNTIL_SIGKILL;
    /** default timeout until giving up */
    public static final Duration DEFAULT_TIMEOUT_UNTIL_GIVE_UP;
    /** default polling interval */
    public static final Duration DEFAULT_POLL_INTERVAL;
    /** default intitial wait */
    public static final Duration DEFAULT_INITIAL_WAIT;
    static {
        DEFAULT_TIMEOUT_UNTIL_SIGKILL = Duration.ofMinutes(3);
        DEFAULT_TIMEOUT_UNTIL_GIVE_UP = Duration.ofMinutes(3);
        DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);
        DEFAULT_INITIAL_WAIT = Duration.ofSeconds(5);
    }

    private static final Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.GUI.name());

    /**
     * Constructor with default parameters.
     */
    public UnixProcessTerminator() {
        this(DEFAULT_TIMEOUT_UNTIL_GIVE_UP,
                DEFAULT_TIMEOUT_UNTIL_SIGKILL,
                DEFAULT_POLL_INTERVAL,
                DEFAULT_INITIAL_WAIT);
    }

    /**
     * Constructor
     *
     * @param timeoutUntilSigKill Duration to wait after initial wait until we try to force
     *                            termination with SIGKILL
     * @param timeoutUntilGiveUp Duration to wait after SIGKILL until giving up
     * @param pollInterval Duration to wait when polling for process termination
     * @param initialWait Duration to wait before starting polling
     */
    public UnixProcessTerminator(Duration timeoutUntilSigKill,
                                 Duration timeoutUntilGiveUp,
                                 Duration pollInterval,
                                 Duration initialWait) {
        this(timeoutUntilSigKill,
                timeoutUntilGiveUp,
                pollInterval,
                initialWait,
                new UnixCommandRunner());
    }

    @VisibleForTesting
    UnixProcessTerminator(Duration timeoutUntilSigKill,
                          Duration timeoutUntilGiveUp,
                          Duration pollInterval,
                          Duration initialWait,
                          UnixCommandRunner unixCommandRunner) {
        this.timeoutUntilSigKill = timeoutUntilSigKill;
        this.timeoutUntilGiveUp = timeoutUntilGiveUp;
        this.pollInterval = pollInterval;
        this.initialWait = initialWait;
        this.unixCommandRunner = unixCommandRunner;
    }

    /**
     * Terminate the kernel OS process given by the kernelInstanceId and block until completion.
     *
     * Specifically, send SIGTERM to that process, then poll the UNIX ps program to verify that
     * the process has stopped.  Keep checking until it has either stopped or the "timeout until
     * SIGKILL" duration is reached.  If timeout is reached, but process is still running, send
     * SIGKILL to that process.  Keep checking until it has either stopped or until the "timeout
     * until give up" duration is reached.  If the latter, throw exception.
     *
     * @param kernelInstanceId kernel instance that needs to be killed
     * @throws KernelControlException if an error happens while trying to kill process, or if both
     *                                timeouts are reached but process still running
     */
    public void terminateAndAwait(KernelInstanceId kernelInstanceId)
            throws KernelControlException {
        long unixPid = kernelInstanceId.getPid();
        boolean terminated = false;

        try {
            unixCommandRunner.sendSigterm(unixPid);
            LOGGER.info("Killing (SIGTERM) kernel PID {}", kernelInstanceId.getPid());
            terminated = blockUntilProcessDeadOrTimeout(unixPid, timeoutUntilSigKill);

            if (terminated) {
                LOGGER.info("Kernel successfully exited after SIGTERM");
                return;
            }

            LOGGER.info("Kernel still running after timeout ({}) reached.  Sending SIGKILL.");
            unixCommandRunner.sendSigKill(unixPid); // Hasta la vista bb
            terminated = blockUntilProcessDeadOrTimeout(unixPid, timeoutUntilGiveUp);
        } catch (IOException | InterruptedException ex) {
            throw new KernelControlException(
                    "Error while trying to terminate kernel process.", ex);
        }

        if (terminated) {
            LOGGER.info("Kernel successfully exited after SIGKILL");
        } else {
            throw new KernelControlException(
                    "SIGTERM and SIGKILL both failed to terminate the kernel process after the allotted timeout durations."
            );
        }
    }

    private boolean blockUntilProcessDeadOrTimeout(long unixPid, Duration timeout) throws IOException, InterruptedException {
        TimeUnit.MILLISECONDS.sleep(initialWait.toMillis());

        Duration waited = Duration.ZERO;
        boolean terminated = false;
        do {
            TimeUnit.MILLISECONDS.sleep(pollInterval.toMillis());
            List<String> psOutput = unixCommandRunner.callPs(unixPid);
            terminated = psOutput.isEmpty();
            waited = waited.plus(pollInterval);
        } while(!terminated && waited.compareTo(timeout) < 0);
        return terminated;
    }

    /**
     * Put all the work of actually running OS commands into this class, so that UnixProcessTerminator
     * unit tests can test the rest of the code in isolation by mocking this class.  Logic of this class
     * will be covered by integ tests.
     */
    @VisibleForTesting
    static class UnixCommandRunner {
        int sendSigterm(long unixPid) throws IOException, InterruptedException {
            ProcessBuilder processBuilder = new ProcessBuilder()
                    .command("kill",
                            String.valueOf(unixPid))
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT);
            return processBuilder.start().waitFor();
        }

        int sendSigKill(long unixPid) throws IOException, InterruptedException {
            ProcessBuilder processBuilder = new ProcessBuilder()
                    .command("kill", "-9",
                            String.valueOf(unixPid))
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT);
            return processBuilder.start().waitFor();
        }

        List<String> callPs(long pid) throws IOException, InterruptedException {
            final String[] command = new String[]{"ps", "-o", "pid=", "--pid", String.valueOf(pid)};
            Process proc = new ProcessBuilder().command(command).start();
            proc.waitFor();

            try (
                    final InputStream is = proc.getInputStream();
                    final InputStreamReader isr = new InputStreamReader(is, Charsets.UTF_8);
                    final BufferedReader br = new BufferedReader(isr);
            ) {
                return br.lines().collect(Collectors.toList());
            } catch (IOException ioe) {
                throw new IOException("Could not get the output of ps program", ioe);
            }
        }
    }
}
