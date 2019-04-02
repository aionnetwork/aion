package org.aion.vm;

import org.aion.vm.VmFactoryImplementation.VM;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.VirtualMachine;
import com.google.common.annotations.VisibleForTesting;

/**
 * A class that providers a caller with a {@link VirtualMachine} class.
 *
 * <p>This class exists primarily so that different {@link VirtualMachineManager} implementations
 * can be used for testing purposes.
 *
 * <p>The default {@link VirtualMachineManager} implementation that this provider uses is: {@link
 * VmFactoryImplementation}.
 *
 * <p>This provider is a state machine that requires its caller to fetch {@link VirtualMachine}
 * instances using the following ordering of calls:
 *
 * <p>1. initializeAllVirtualMachines 2. getVirtualMachineInstance 3. shutdownAllVirtualMachines
 *
 * <p>where the second call can be repeated multiple times before a shutdown.
 *
 * <p>Note that setting a new factory is only possible if all virtual machines have been shut down
 * and none are live.
 */
public final class VirtualMachineProvider {
    private static VirtualMachineManager factory = VmFactoryImplementation.getFactorySingleton();
    private static boolean machinesAreLive = false;

    /**
     * Performs any initialization required to run a {@link VirtualMachine}, for all supported
     * virtual machines.
     *
     * <p>It is guaranteed that all instances returned by {@code getVirtualMachineInstance()} will
     * return a {@link VirtualMachine} that is ready to be used if that method is called
     * <b>after</b> this method.
     *
     * <p>This method can only be called if no virtual machines have been initialized yet, using
     * this method, without being shutdown.
     *
     * @throws IllegalStateException If the virtual machines are already live.
     */
    public static void initializeAllVirtualMachines() {
        factory.initializeAllVirtualMachines();
        machinesAreLive = true;
    }

    /**
     * Performs any shutdown logic required to kill a {@link VirtualMachine}, for all supported
     * virtual machines.
     *
     * <p>This method can only be called if {@code initializeAllVirtualMachines()} has already been
     * called and the machines have not yet been shutdown using this method.
     *
     * @throws IllegalStateException If the virtual machines are already dead.
     */
    public static void shutdownAllVirtualMachines() {
        factory.shutdownAllVirtualMachines();
        machinesAreLive = false;
    }

    /**
     * Returns an instance of the {@link VirtualMachine} that is requested by the specified VM
     * request type.
     *
     * <p>In the case of long-lived machines, the same instance will be returned each time.
     *
     * <p>Also in the case of long-lived machines: this method <b>must</b> be called after {@code
     * initializeAllVirtualMachines()} and before {@code shutdownAllVirtualMachines()}. In the case
     * of non-long-lived machines this invariant does not apply.
     *
     * @param request The virtual machine to be requested.
     * @param kernel The kernel interface to hand off to the requested virtual machine.
     * @return An instance of the virtual machine.
     * @throws IllegalStateException If the requested virtual machine is not currently live.
     */
    public static VirtualMachine getVirtualMachineInstance(VM request, KernelInterface kernel) {
        return factory.getVirtualMachineInstance(request, kernel);
    }

    /**
     * Sets a new {@link VirtualMachineManager} instance that this provider will return.
     *
     * <p>This method only exists for testing purposes and should not be used in production.
     *
     * @param vmFactory The new instance to provide the caller with.
     * @throws IllegalStateException If virtual machines have been initialized prior to calling
     *     this.
     * @throws NullPointerException If vmFactory is null.
     */
    @VisibleForTesting
    public static void setVirtualMachineFactory(VirtualMachineManager vmFactory) {
        if (machinesAreLive) {
            throw new IllegalStateException(
                    "Cannot set a new VirtualMachineManager while machines are live!");
        }
        if (vmFactory == null) {
            throw new NullPointerException("Cannot set a null VirtualMachineManager.");
        }
        factory = vmFactory;
    }

    /**
     * Check vm init status
     *
     * <p>This method only exists for testing purposes and should not be used in production.
     */
    @VisibleForTesting
    public static boolean isMachinesAreLive() {
        return machinesAreLive;
    }
}
