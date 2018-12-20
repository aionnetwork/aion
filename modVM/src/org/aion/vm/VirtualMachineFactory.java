package org.aion.vm;

import org.aion.vm.api.interfaces.VirtualMachine;

public final class VirtualMachineFactory {
    private static final VirtualMachineFactory SINGLETON = new VirtualMachineFactory();
    private MachineState state = MachineState.ALL_MACHINES_DEAD;

    // All long-lived supported virtual machines.
    private VirtualMachine aionVirtualMachine = null;

    private VirtualMachineFactory(){}

    /**
     * Returns the singleton instance of this factory class.
     *
     * @return This factory class as a singleton.
     */
    public static VirtualMachineFactory getFactorySingleton() {
        return SINGLETON;
    }

    /**
     * The list of all virtual machines that the kernel currently supports.
     */
    public enum VM { FVM, AVM }

    // Internal state tracking, mostly for correctness assurance.
    private enum MachineState { ALL_MACHINES_DEAD, ALL_MACHINES_LIVE }

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
    public void initializeAllVirtualMachines() {
        if (this.state == MachineState.ALL_MACHINES_LIVE) {
            throw new IllegalStateException("All Virtual Machines are already live. Cannot re-initialize.");
        }
        //TODO
    }

    /**
     * Performs any shutdown logic required to kill a {@link VirtualMachine}, for all supported
     * virtual machines.
     *
     * This method can only be called if {@code initializeAllVirtualMachines()} has already been
     * called and the machines have not yet been shutdown using this method.
     *
     * @throws IllegalStateException If the virtual machines are already dead.
     */
    public void shutdownAllVirtualMachines() {
        if (this.state == MachineState.ALL_MACHINES_DEAD) {
            throw new IllegalStateException("All Virtual Machines are already shutdown.");
        }
        //TODO
    }

    /**
     * Returns an instance of the {@link VirtualMachine} that is requested by the specified VM
     * request type.
     *
     * In the case of long-lived machines, the same instance will be returned each time.
     *
     * This method <b>must</b> be called after {@code initializeAllVirtualMachines()} and before
     * {@code shutdownAllVirtualMachines()}.
     *
     * @param request The virtual machine to be requested.
     * @return An instance of the virtual machine.
     * @throws IllegalStateException If the virtual machines are not currently live.
     */
    public VirtualMachine getVirtualMachineInstance(VM request) {
        if (this.state == MachineState.ALL_MACHINES_DEAD) {
            throw new IllegalStateException("Virtual Machines have not yet been initialized. Cannot get an instance.");
        }
        //TODO
        return null;
    }

}
