package org.aion.avm.version1;

import org.aion.avm.core.AvmImpl;
import org.aion.avm.stub.IAionVirtualMachine;
import org.aion.avm.stub.IAvmResourceFactory;
import org.aion.avm.stub.IExternalStateBuilder;
import org.aion.avm.userlib.AionBuffer;

/**
 * A factory class that is able to produce the various resources offered by this avm version.
 */
public final class AvmResourceFactory implements IAvmResourceFactory {

    @Override
    public IAionVirtualMachine createAndInitializeNewAvm() {
        return AionVirtualMachine.createAndInitializeNewAvm();
    }

    @Override
    public IExternalStateBuilder newExternalStateBuilder() {
        return new AvmExternalStateBuilder();
    }

    @Override
    public ClassLoader verifyAndReturnClassloader() {
        ClassLoader loader = this.getClass().getClassLoader();

        // Verify that all of the avm jars were loaded by the same classloader as this class.
        // We simply pick random classes in each of the jars to test, there is nothing special about these classes in particular.
        if (AvmImpl.class.getClassLoader() != loader) {
            throw new IllegalStateException("Avm core jar was loaded by the wrong classloader: " + AvmImpl.class.getClassLoader());
        }
        if (avm.Blockchain.class.getClassLoader() != loader) {
            throw new IllegalStateException("Avm api jar was loaded by the wrong classloader: " + avm.Blockchain.class.getClassLoader());
        }
        if (p.avm.Blockchain.class.getClassLoader() != loader) {
            throw new IllegalStateException("Avm rt jar was loaded by the wrong classloader: " + p.avm.Blockchain.class.getClassLoader());
        }
        if (AionBuffer.class.getClassLoader() != loader) {
            throw new IllegalStateException("Avm userlib jar was loaded by the wrong classloader: " + AionBuffer.class.getClassLoader());
        }

        return loader;
    }
}
