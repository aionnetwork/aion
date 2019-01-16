package org.aion.vm;

import org.aion.vm.VmFactoryImplementation.VM;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.VirtualMachine;

/**
 * A virtual machine factory is a stateful factory that allows a caller to fetch a specified
 * instance of a {@link VirtualMachine}.
 *
 * The order of calls to a VirtualMachineManager must be done in the following order:
 *
 *    1. initializeAllVirtualMachines
 *    2. getVirtualMachineInstance
 *    3. shutdownAllVirtualMachines
 *
 * where the second call can be repeated multiple times before a shutdown.
 *
 * This ordering is to ensure that when getVirtualMachineInstance is called, the
 * {@link VirtualMachine} it returns is initialized and ready to use.
 */
public interface VirtualMachineManager {

    /**
     * Performs any initialization required to run a {@link VirtualMachine}, for all supported
     * virtual machines.
     *
     * It is guaranteed that all instances returned by {@code getVirtualMachineInstance()} will
     * return a {@link VirtualMachine} that is ready to be used if that method is called <b>after</b>
     * this method.
     *
     * This method can only be called if no virtual machines have been initialized yet, using this
     * method, without being shutdown.
     *
     * @throws IllegalStateException If the virtual machines are already live.
     */
    void initializeAllVirtualMachines();

    /**
     * Performs any shutdown logic required to kill a {@link VirtualMachine}, for all supported
     * virtual machines.
     *
     * This method can only be called if {@code initializeAllVirtualMachines()} has already been
     * called and the machines have not yet been shutdown using this method.
     *
     * @throws IllegalStateException If the virtual machines are already dead.
     */
    void shutdownAllVirtualMachines();

    /**
     * Returns an instance of the {@link VirtualMachine} that is requested by the specified VM
     * request type.
     *
     * In the case of long-lived machines, the same instance will be returned each time.
     *
     * Also in the case of long-lived machines: this method <b>must</b> be called after
     * {@code initializeAllVirtualMachines()} and before {@code shutdownAllVirtualMachines()}. In
     * the case of non-long-lived machines this invariant does not apply.
     *
     * @param request The virtual machine to be requested.
     * @param  kernel The kernel interface to hand off to the requested virtual machine.
     * @return An instance of the virtual machine.
     * @throws IllegalStateException If the requested virtual machine is not currently live.
     */
    VirtualMachine getVirtualMachineInstance(VM request, KernelInterface kernel);

}
