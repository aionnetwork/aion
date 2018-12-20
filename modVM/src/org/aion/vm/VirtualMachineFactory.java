package org.aion.vm;

import org.aion.avm.core.AvmImpl;
import org.aion.avm.core.CommonAvmFactory;
import org.aion.fastvm.FastVirtualMachine;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.VirtualMachine;

/**
 * A Singleton factory class that is responsible for:
 *
 * - Initializing the state of all the supported virtual machines.
 * - Returning any requested instances of any supported virtual machines.
 * - Shutting down all the supported virtual machines.
 */
public final class VirtualMachineFactory {
    private static final VirtualMachineFactory SINGLETON = new VirtualMachineFactory();
    private MachineState state = MachineState.ALL_MACHINES_DEAD;

    // All long-lived supported virtual machines.
    private AvmImpl aionVirtualMachine = null;

    private VirtualMachineFactory(){}

    /**
     * Returns the singleton instance of this factory class.
     *
     * @return This factory class as a singleton.
     */
    public static VirtualMachineFactory getFactorySingleton() {
        return SINGLETON;
    }

    private enum MachineLifeCycle { LONG_LIVED, NOT_LONG_LIVED }

    /**
     * The list of all virtual machines that the kernel currently supports.
     */
    public enum VM {
        FVM(MachineLifeCycle.NOT_LONG_LIVED),
        AVM(MachineLifeCycle.LONG_LIVED);

        private MachineLifeCycle lifeCycle;

        VM(MachineLifeCycle lifeCycle) {
            this.lifeCycle = lifeCycle;
        }

        public boolean isLongLivedVirtualMachine() {
            return this.lifeCycle == MachineLifeCycle.LONG_LIVED;
        }
    }

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

        // initialize the Avm. This buildAvmInstance method already calls start() for us.
        this.aionVirtualMachine = CommonAvmFactory.buildAvmInstance(null);
        this.state = MachineState.ALL_MACHINES_LIVE;
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

        // shutdown the Avm.
        this.aionVirtualMachine.shutdown();
        this.state = MachineState.ALL_MACHINES_DEAD;
    }

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
    public VirtualMachine getVirtualMachineInstance(VM request, KernelInterface kernel) {
        if ((request.isLongLivedVirtualMachine()) && (this.state == MachineState.ALL_MACHINES_DEAD)) {
            throw new IllegalStateException("The requested VM: " + request + " is long-lived and has not yet been initialized.");
        }

        switch (request) {
            case FVM:
                FastVirtualMachine fvm = new FastVirtualMachine();
                fvm.setKernelInterface(kernel);
                return fvm;
            case AVM:
                this.aionVirtualMachine.setKernelInterface(kernel);
                return this.aionVirtualMachine;
            default: throw new UnsupportedOperationException("Unsupported VM request.");
        }
    }

}
