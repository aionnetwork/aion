package org.aion.vm;

import org.aion.avm.core.AvmImpl;
import org.aion.avm.core.CommonAvmFactory;
import org.aion.fastvm.FastVirtualMachine;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.VirtualMachine;

/**
 * A Singleton factory class that is responsible for:
 *
 * <p>- Initializing the state of all the supported virtual machines. - Returning any requested
 * instances of any supported virtual machines. - Shutting down all the supported virtual machines.
 */
public final class VmFactoryImplementation implements VirtualMachineManager {
    private static final VmFactoryImplementation SINGLETON = new VmFactoryImplementation();
    private MachineState state = MachineState.ALL_MACHINES_DEAD;

    // All long-lived supported virtual machines.
    private AvmImpl aionVirtualMachine = null;

    private VmFactoryImplementation() {}

    /**
     * Returns the singleton instance of this factory class.
     *
     * @return This factory class as a singleton.
     */
    public static VmFactoryImplementation getFactorySingleton() {
        return SINGLETON;
    }

    private enum MachineLifeCycle {
        LONG_LIVED,
        NOT_LONG_LIVED
    }

    /** The list of all virtual machines that the kernel currently supports. */
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
    private enum MachineState {
        ALL_MACHINES_DEAD,
        ALL_MACHINES_LIVE
    }

    /** {@inheritDoc} */
    @Override
    public void initializeAllVirtualMachines() {
        if (this.state == MachineState.ALL_MACHINES_LIVE) {
            throw new IllegalStateException(
                    "All Virtual Machines are already live. Cannot re-initialize.");
        }

        // initialize the Avm. This buildAvmInstance method already calls start() for us.
        this.aionVirtualMachine = CommonAvmFactory.buildAvmInstance(null);
        this.state = MachineState.ALL_MACHINES_LIVE;
    }

    /** {@inheritDoc} */
    @Override
    public void shutdownAllVirtualMachines() {
        if (this.state == MachineState.ALL_MACHINES_DEAD) {
            throw new IllegalStateException("All Virtual Machines are already shutdown.");
        }

        // shutdown the Avm.
        this.aionVirtualMachine.shutdown();
        this.state = MachineState.ALL_MACHINES_DEAD;
    }

    /** {@inheritDoc} */
    @Override
    public VirtualMachine getVirtualMachineInstance(VM request, KernelInterface kernel) {
        if ((request.isLongLivedVirtualMachine())
                && (this.state == MachineState.ALL_MACHINES_DEAD)) {
            throw new IllegalStateException(
                    "The requested VM: "
                            + request
                            + " is long-lived and has not yet been initialized.");
        }

        switch (request) {
            case FVM:
                FastVirtualMachine fvm = new FastVirtualMachine();
                fvm.setKernelInterface(kernel);
                return fvm;
            case AVM:
                this.aionVirtualMachine.setKernelInterface(kernel);
                return this.aionVirtualMachine;
            default:
                throw new UnsupportedOperationException("Unsupported VM request.");
        }
    }
}
