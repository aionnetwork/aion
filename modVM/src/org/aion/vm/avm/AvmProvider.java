package org.aion.vm.avm;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.vm.avm.internal.AvmResourcesVersion1;
import org.aion.vm.avm.internal.AvmResourcesVersion2;
import org.aion.avm.stub.AvmVersion;
import org.aion.avm.stub.IAionVirtualMachine;
import org.aion.avm.stub.IAvmResourceFactory;
import org.aion.avm.stub.IExternalStateBuilder;

/**
 * A class that provides access to multi-versioned AVM-related resources.
 *
 * There is a single lock that must be acquired using the {@code tryAcquireLock()} method before any
 * of the other methods can be called. Every method is protected by this lock. When finished with
 * the lock the caller must call {@code releaseLock()}.
 *
 * Before any particular AVM version can be used it must first be enabled, and once a particular
 * version is done being used it must be disabled to prevent resource leaking.
 *
 * Note that enabling a particular version of the avm is not equivalent to starting that avm version,
 * and disabling is not equivalent to shutting it down.
 *
 * Enabling and disabling a version tells the provider when resources relating to that version are
 * allowed to survive and when they are to be destroyed. The avm itself is not the only resource
 * provided, this class also provides an external state builder. Even if the avm was the only
 * resource provided, it should still be possible to start and shut it down multiple times while
 * only having to enable and disable it once (at the start and end).
 *
 * This class is thread-safe.
 */
public final class AvmProvider {
    // Note: we use only a single lock for all versions. In the future we may decide to make a lock per version.
    private static final ReentrantLock LOCK = new ReentrantLock();

    // If a particular avm resources version is non-null then it is enabled. If null it is disabled.
    private static AvmResourcesVersion1 avmResourcesVersion1 = null;
    private static AvmResourcesVersion2 avmResourcesVersion2 = null;

    /**
     * Returns {@code true} only if the specified avm version is enabled, otherwise {@code false}.
     *
     * @param version The version to query.
     * @return whether the version is currently enabled or not.
     */
    public static boolean isVersionEnabled(AvmVersion version) {
        if (!LOCK.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("The calling thread does not own the lock!");
        }

        if (version == AvmVersion.VERSION_1) {
            return avmResourcesVersion1 != null;
        } else if (version == AvmVersion.VERSION_2) {
            return avmResourcesVersion2 != null;
        } else {
            throw new IllegalStateException("Unknown avm version: " + version);
        }
    }

    /**
     * Returns {@code true} only if the specified version of the avm is running, otherwise
     * {@code false}.
     *
     * @param version The version to query.
     * @return whether the avm is currently running or not.
     */
    public static boolean isAvmRunning(AvmVersion version) {
        if (!LOCK.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("The calling thread does not own the lock!");
        }

        if (version == AvmVersion.VERSION_1) {
            return avmResourcesVersion1 != null  && avmResourcesVersion1.isAvmRunning();
        } else if (version == AvmVersion.VERSION_2) {
            return avmResourcesVersion2 != null && avmResourcesVersion2.isAvmRunning();
        } else {
            throw new IllegalStateException("Unknown avm version: " + version);
        }
    }

    /**
     * Returns {@code true} only if the calling thread is the owner of the avm provider's lock.
     * Otherwise {@code false}.
     *
     * @return true only if the caller owns the lock.
     */
    public static boolean holdsLock() {
        return LOCK.isHeldByCurrentThread();
    }

    /**
     * Returns the avm resource factory for the specified version of the avm.
     *
     * @param version The avm version of the factory.
     * @return the factory.
     */
    public static IAvmResourceFactory getResourceFactory(AvmVersion version) {
        if (!LOCK.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("The calling thread does not own the lock!");
        }

        if (version == AvmVersion.VERSION_1) {
            return avmResourcesVersion1.resourceFactory;
        } else if (version == AvmVersion.VERSION_2) {
            return avmResourcesVersion2.resourceFactory;
        } else {
            throw new IllegalStateException("Unknown avm version: " + version);
        }
    }

    /**
     * Enables the specified version of the AVM.
     *
     * @param version The version to enable.
     * @param projectRootDir The path to the root directory of the aion project.
     * @throws IllegalMonitorStateException If the calling thread does not own the lock.
     */
    public static void enableAvmVersion(AvmVersion version, String projectRootDir) throws IllegalAccessException, InstantiationException, ClassNotFoundException, IOException {
        if (!LOCK.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("The calling thread does not own the lock!");
        }

        if (version == AvmVersion.VERSION_1) {
            if (avmResourcesVersion1 == null) {
                avmResourcesVersion1 = AvmResourcesVersion1.loadResources(projectRootDir);
            }
        } else if (version == AvmVersion.VERSION_2) {
            if (avmResourcesVersion2 == null) {
                avmResourcesVersion2 = AvmResourcesVersion2.loadResources(projectRootDir);
            }
        } else {
            throw new IllegalStateException("Unknown avm version: " + version);
        }
    }

    /**
     * Disables the specified version of the AVM. If that version is currently running then it will
     * be shutdown.
     *
     * @param version The version to disable.
     * @throws IllegalMonitorStateException If the calling thread does not own the lock.
     */
    public static void disableAvmVersion(AvmVersion version) throws IOException {
        if (!LOCK.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("The calling thread does not own the lock!");
        }

        if (version == AvmVersion.VERSION_1) {
            if (avmResourcesVersion1 != null) {
                avmResourcesVersion1.shutdownAvm();
                avmResourcesVersion1.close();
                avmResourcesVersion1 = null;
            }
        } else if (version == AvmVersion.VERSION_2) {
            if (avmResourcesVersion2 != null) {
                avmResourcesVersion2.shutdownAvm();
                avmResourcesVersion2.close();
                avmResourcesVersion2 = null;
            }
        } else {
            throw new IllegalStateException("Unknown avm version: " + version);
        }
    }

    /**
     * Retruns the specified version of the avm.
     *
     * @param version The version to get.
     * @throws IllegalMonitorStateException If the calling thread does not own the lock.
     * @throws IllegalStateException If the avm version has not been enabled or is not currently running.
     * @return the avm.
     */
    public static IAionVirtualMachine getAvm(AvmVersion version) {
        if (!LOCK.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("The calling thread does not own the lock!");
        }

        if (version == AvmVersion.VERSION_1) {

            if (avmResourcesVersion1 == null) {
                throw new IllegalStateException("Cannot get avm version 1 - verison has not been enabled yet!");
            }
            return avmResourcesVersion1.getAvm();

        } else if (version == AvmVersion.VERSION_2) {

            if (avmResourcesVersion2 == null) {
                throw new IllegalStateException("Cannot get avm version 2 - verison has not been enabled yet!");
            }
            return avmResourcesVersion2.getAvm();

        } else {
            throw new IllegalStateException("Unknown avm version: " + version);
        }
    }

    /**
     * Initializes and starts up the specified version of the avm.
     *
     * @param version The version to start.
     * @throws IllegalMonitorStateException If the calling thread does not own the lock.
     * @throws IllegalStateException If the avm version has not been enabled or is already running.
     */
    public static void startAvm(AvmVersion version) {
        if (!LOCK.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("The calling thread does not own the lock!");
        }

        if (version == AvmVersion.VERSION_1) {

            if (avmResourcesVersion1 == null) {
                throw new IllegalStateException("Cannot start avm version 1 - verison has not been enabled yet!");
            }
            avmResourcesVersion1.initializeAndStartNewAvm();

        } else if (version == AvmVersion.VERSION_2) {

            if (avmResourcesVersion2 == null) {
                throw new IllegalStateException("Cannot start avm version 2 - verison has not been enabled yet!");
            }
            avmResourcesVersion2.initializeAndStartNewAvm();

        } else {
            throw new IllegalStateException("Unknown avm version: " + version);
        }
    }

    /**
     * Shuts down the specified version of the avm.
     *
     * @param version The version to shutdown.
     * @throws IllegalMonitorStateException If the calling thread does not own the lock.
     * @throws IllegalStateException If the avm version has not been enabled.
     */
    public static void shutdownAvm(AvmVersion version) {
        if (!LOCK.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("The calling thread does not own the lock!");
        }

        if (version == AvmVersion.VERSION_1) {

            if (avmResourcesVersion1 == null) {
                throw new IllegalStateException("Cannot shutdown avm version 1 - verison has not been enabled yet!");
            }
            avmResourcesVersion1.shutdownAvm();

        } else if (version == AvmVersion.VERSION_2) {

            if (avmResourcesVersion2 == null) {
                throw new IllegalStateException("Cannot shutdown avm version 2 - verison has not been enabled yet!");
            }
            avmResourcesVersion2.shutdownAvm();

        } else {
            throw new IllegalStateException("Unknown avm version: " + version);
        }
    }

    /**
     * Attempts to acquire the lock.
     *
     * Returns {@code true} if the lock is successfully obtained by the caller, {@code false} otherwise.
     *
     * @param timeout The timeout duration.
     * @param unit The time units the duration is specified in.
     * @throws IllegalMonitorStateException If the caller already owns the lock.
     * @return whether or not the lock was acquired.
     */
    public static boolean tryAcquireLock(long timeout, TimeUnit unit) {
        if (LOCK.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("Cannot acquire lock - calling thread already owns the lock!");
        }

        try {
            return LOCK.tryLock(timeout, unit);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interruption is not supported!");
        }
    }

    /**
     * Releases the lock.
     *
     * @throws IllegalMonitorStateException If the caller does not own the lock.
     */
    public static void releaseLock() {
        if (!LOCK.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("Cannot release lock - calling thread does not own the lock!");
        }

        LOCK.unlock();
    }

    /**
     * Returns a new instance of an external state builder for the specified avm version.
     *
     * @param version The version of the builder to acquire.
     * @throws IllegalStateException If the avm version has not been enabled.
     * @throws IllegalMonitorStateException If the calling thread does not own the lock.
     * @return a new builder.
     */
    public static IExternalStateBuilder newExternalStateBuilder(AvmVersion version) {
        if (!LOCK.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("The calling thread does not own the lock!");
        }

        if (version == AvmVersion.VERSION_1) {

            if (avmResourcesVersion1 == null) {
                throw new IllegalStateException("Cannot get builder for version 1 - version has not been enabled yet!");
            }
            return avmResourcesVersion1.resourceFactory.newExternalStateBuilder();

        } else if (version == AvmVersion.VERSION_2) {

            if (avmResourcesVersion2 == null) {
                throw new IllegalStateException("Cannot get builder for version 2 - version has not been enabled yet!");
            }
            return avmResourcesVersion2.resourceFactory.newExternalStateBuilder();

        } else {
            throw new IllegalStateException("Unknown avm version: " + version);
        }
    }
}
