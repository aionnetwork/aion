package org.aion.os;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.KernelProcEvent;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.config.CfgGuiLauncher;
import org.slf4j.Logger;

import java.io.*;

/** Facilitates launching an instance of the Kernel and managing the launched instance. */
public class KernelLauncher {
    private final CfgGuiLauncher config;
    private final KernelLaunchConfigurator kernelLaunchConfigurator;
    private final EventBusRegistry eventBusRegistry;
    private final UnixProcessTerminator unixProcessTerminator;
    private final UnixKernelProcessHealthChecker healthChecker;
    private final File pidFile;

    private KernelInstanceId currentInstance = null;

    private static final Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.GUI.name());

    /**
     * Constructor.
     *
     * @see {@link CfgGuiLauncher#DEFAULT_CONFIG} if you want Kernel Launcher to auto-detect
     *      the parameters
     */
    public KernelLauncher(CfgGuiLauncher config,
                          EventBusRegistry eventBusRegistry,
                          UnixProcessTerminator terminator,
                          UnixKernelProcessHealthChecker healthChecker) {
        this(config,
                new KernelLaunchConfigurator(),
                eventBusRegistry,
                terminator,
                healthChecker,
                (config.getKernelPidFile() != null ?
                        new File(config.getKernelPidFile()) :
                        choosePidStorageLocation())
        );
    }

    /** Ctor with injectable parameters for unit testing */
    @VisibleForTesting KernelLauncher(CfgGuiLauncher config,
                                      KernelLaunchConfigurator klc,
                                      EventBusRegistry ebr,
                                      UnixProcessTerminator terminator,
                                      UnixKernelProcessHealthChecker healthChecker,
                                      File pidFile) {
        this.config = config;
        this.kernelLaunchConfigurator = klc;
        this.eventBusRegistry = ebr;
        this.unixProcessTerminator = terminator;
        this.healthChecker = healthChecker;
        this.pidFile = pidFile;
    }

    /**
     * Launch a separate JVM in a new OS process and within it, run the Aion kernel.  PID of process
     * is persisted to disk.  The process is started as a background process (i.e. this method will
     * not block waiting for the Aion kernel process to end)
     *
     * @return if successful, a {@link Process} whose value is the Process of the nohup_wrapper.sh
     *         script used to then call the aion.sh script
     * @throws KernelControlException if process could not be launched
     */
    public Process launch() throws KernelControlException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        kernelLaunchConfigurator.configure(config, processBuilder);

        try {
            Process proc = processBuilder.start();
            setAndPersistPid(waitAndCapturePid(proc));
            return proc;
        } catch (IOException ioe) {
            final String message;
            if(ioe.getCause() instanceof IOException) {
                message = "Could not find the aion.sh script for launching the Aion Kernel.  " +
                        "Check your configuration; or if auto-detection is used, please manually configure.";
            } else {
                message = "Could not start kernel.";
            }
            LOGGER.error(message, ioe);
            throw new KernelControlException(message, ioe);
        }
    }

    @VisibleForTesting long waitAndCapturePid(Process proc) throws KernelControlException {
        try {
            // Note: proc is a reference to a shell script that calls the kernel
            // as a background task.  So here we're blocking until the shell script
            // exits, not until the kernel Java process exits.
            proc.waitFor();
        } catch (InterruptedException ie) {
            String message = "Nohup wrapper launch interrupted; aborting.  The kernel " +
                    "may have already been launched, but we could not capture the PID.";
            LOGGER.error(message, ie);
            throw new KernelControlException(message, ie);
        }

        String pid = null;
        try (
                InputStream is = proc.getInputStream();
                InputStreamReader isr = new InputStreamReader(is, Charsets.UTF_8);
        ) {
            pid = CharStreams.toString(isr).replace("\n", "");
            LOGGER.info("Started kernel with pid = {}", pid);
            return Long.valueOf(pid);
        } catch (IOException | NumberFormatException ex) {
            // If we get here, the stdout from the nohup wrapper script was not what we expected.
            // Either there was a bug in the script or it failed to spawn the Aion kernel.
            String message = String.format("Failed to capture the PID of the Aion kernel " +
                    "process.  Wrapper script exited with shell code %d", proc.exitValue());
            LOGGER.error(message, ex);
            LOGGER.info(String.format("wrapper script stdout was: %s", pid));
            throw new KernelControlException(message, ex);
        }
    }

    /**
     * Look for a Kernel PID that we previously launched and persisted to disk.  If successful,
     * set that PID as the launched kernel instance.
     *
     * @return true if old kernel PID found; false otherwise
     * @throws IOException if old kernel PID file found, but error occurred while trying to read it
     * @throws ClassNotFoundException if old kernel PID file found, but error occurred while trying to read it
     */
    public boolean tryResume() throws ClassNotFoundException, IOException {
        if(hasLaunchedInstance()) {
            throw new IllegalArgumentException(
                    "Can't try to resume because there is already an associated instance.");
        }

        if(pidFile.exists() && !pidFile.isDirectory()) {
            try {
                KernelInstanceId pid = retrievePid(pidFile);
                if (!healthChecker.checkIfKernelRunning(pid.getPid())) {
                    LOGGER.debug("Found old kernel pid, but the process is no longer running.  Will clean up.");
                    cleanUpDeadProcess();
                    return false;
                }

                setCurrentInstance(pid);
                LOGGER.debug("Found old kernel pid = {}", currentInstance.getPid());
                return true;
            } catch (KernelControlException kce) {
                // if we got here, we somehow couldn't figure out if process was running or not
                // assume that it is -- if API fails to connect, DashboardController will
                // handle that later.
                LOGGER.warn(
                        "Found old kernel pid = {} but could not determine if it is really running.  Assuming that it is.",
                        currentInstance.getPid());
                return true;
            } catch (ClassNotFoundException | IOException ex) {
                LOGGER.error("Found old kernel pid file at {}, but failed to deserialize it, " +
                                "so it was ignored.", pidFile.getAbsolutePath(), ex);
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Kill the OS process that the kernel is running in.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public void terminate() throws KernelControlException {
        // Implemented by calling UNIX `kill` program on the pid of the process we launched.
        // For the future, should add a terminate function in Aion API that we can call in
        // order for this to be less platform-dependent and more reliable.
        if(!hasLaunchedInstance()) {
            throw new IllegalArgumentException("Trying to terminate when there is no running instance");
        }
        unixProcessTerminator.terminateAndAwait(currentInstance);
        removePersistedPid();
        setCurrentInstance(null);
    }

    /**
     * Whether the launcher is associated with a running kernel instance.  This includes instances
     * that KernelLauncher recovered through {@link #tryResume()}.
     *
     * @return whether the launcher is associated with a running kernel instance
     */
    public boolean hasLaunchedInstance() {
        return currentInstance != null;
    }

    /** @return if kernel launched, return its instance id; otherwise, null */
    public KernelInstanceId getLaunchedInstance() {
        return this.currentInstance;
    }

    /**
     * Resets the state of kernel launcher (and any resources it has persisted to disk) to as if the kernel
     * is not launched.  Intended to be called when we detect that the process died through some mechanism
     * that is not {@link #terminate()}, i.e. someone killed the process with their OS task manager.
     */
    public void cleanUpDeadProcess() {
        removePersistedPid();
        setCurrentInstance(null);
    }

    private KernelInstanceId retrievePid(File pidFile) throws IOException, ClassNotFoundException {
        FileInputStream fis = new FileInputStream(pidFile);
        ObjectInputStream ois = new ObjectInputStream(fis);
        return (KernelInstanceId) ois.readObject();
    }

    @VisibleForTesting KernelInstanceId setAndPersistPid(long pid) throws IOException {
        KernelInstanceId kernel = new KernelInstanceId(pid);
        FileOutputStream fos = new FileOutputStream(pidFile);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(kernel);
        setCurrentInstance(kernel);
        return kernel;
    }

    @VisibleForTesting void removePersistedPid() {
        if(pidFile.exists() && pidFile.isFile()) {
            String cannotRemoveMessage = String.format(
                    "Failed to remove Kernel pid file '%s'.  " +
                            "GUI may start in an incorrect next time it is run." +
                            "  Please remove the file manually to correct this.",
                    pidFile.getAbsolutePath()
            );
            try {
                if (!pidFile.delete()) {
                    LOGGER.error(cannotRemoveMessage);
                }
            } catch (SecurityException se) {
                LOGGER.error(cannotRemoveMessage);
            }
        } else {
            LOGGER.warn("Could not find Kernel pid file '{}' when trying to delete it.",
                    pidFile.getAbsolutePath());
        }
    }

    @VisibleForTesting void setCurrentInstance(KernelInstanceId instance) {
        this.currentInstance = instance;
        if(null == instance) {
            eventBusRegistry.getBus(EventBusRegistry.KERNEL_BUS)
                    .post(new KernelProcEvent.KernelTerminatedEvent());
        } else {
            eventBusRegistry.getBus(EventBusRegistry.KERNEL_BUS)
                    .post(new KernelProcEvent.KernelLaunchedEvent());
        }
    }

    static File choosePidStorageLocation() {
        return new File("/tmp/kernel-pid");
    }
}