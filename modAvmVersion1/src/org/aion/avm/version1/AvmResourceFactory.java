package org.aion.avm.version1;

import org.aion.avm.stub.IAionVirtualMachine;
import org.aion.avm.stub.IAvmResourceFactory;
import org.aion.avm.stub.IAvmExternalStateBuilder;
import org.aion.avm.stub.IContractFactory;
import org.aion.avm.stub.IDecoder;
import org.aion.avm.stub.IStreamingEncoder;

/**
 * A factory class that is able to produce the various resources offered by this avm version.
 */
public final class AvmResourceFactory implements IAvmResourceFactory {

    @Override
    public IAionVirtualMachine createAndInitializeNewAvm() {
        return AionVirtualMachine.createAndInitializeNewAvm();
    }

    @Override
    public IAvmExternalStateBuilder newExternalStateBuilder() {
        return new AvmExternalStateBuilder();
    }

    @Override
    public ClassLoader verifyAndReturnClassloader() {
        ClassLoader loader = this.getClass().getClassLoader();

        // Verify that all of the avm jars were loaded by the same classloader as this class.
        // We simply pick random classes in each of the jars to test, there is nothing special about these classes in particular.
        if (org.aion.avm.core.AvmImpl.class.getClassLoader() != loader) {
            throw new IllegalStateException("Avm core jar was loaded by the wrong classloader: " + org.aion.avm.core.AvmImpl.class.getClassLoader());
        }
        if (avm.Blockchain.class.getClassLoader() != loader) {
            throw new IllegalStateException("Avm api jar was loaded by the wrong classloader: " + avm.Blockchain.class.getClassLoader());
        }
        if (p.avm.Blockchain.class.getClassLoader() != loader) {
            throw new IllegalStateException("Avm rt jar was loaded by the wrong classloader: " + p.avm.Blockchain.class.getClassLoader());
        }
        if (org.aion.avm.userlib.AionBuffer.class.getClassLoader() != loader) {
            throw new IllegalStateException("Avm userlib jar was loaded by the wrong classloader: " + org.aion.avm.userlib.AionBuffer.class.getClassLoader());
        }
        if (org.aion.avm.tooling.deploy.OptimizedJarBuilder.class.getClassLoader() != loader) {
            throw new IllegalStateException("Avm tooling jar was loaded by the wrong classloader: " + org.aion.avm.tooling.deploy.OptimizedJarBuilder.class.getClassLoader());
        }

        return loader;
    }

    @Override
    public IContractFactory newContractFactory() {
        return new ContractFactory();
    }

    @Override
    public IStreamingEncoder newStreamingEncoder() {
        return new StreamingEncoder();
    }

    @Override
    public IDecoder newDecoder(byte[] encoding) {
        return new Decoder(encoding);
    }
}
