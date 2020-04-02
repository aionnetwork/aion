package org.aion.avm.version1;

import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.stub.IContractFactory;
import org.aion.avm.tooling.deploy.OptimizedJarBuilder;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;
import org.aion.avm.userlib.abi.ABIException;
import org.aion.avm.userlib.abi.ABIToken;
import org.aion.avm.version1.contracts.DeployAsInternalTransaction;
import org.aion.avm.version1.contracts.GenericContract;
import org.aion.avm.version1.contracts.HelloWorld;
import org.aion.avm.version1.contracts.InternalTransaction;
import org.aion.avm.version1.contracts.LogTarget;
import org.aion.avm.version1.contracts.Statefulness;
import org.aion.avm.version1.contracts.unity.StakerRegistry;
import org.aion.avm.version1.contracts.unity.StakerRegistryEvents;
import org.aion.avm.version1.contracts.unity.StakerRegistryStorage;
import org.aion.avm.version1.contracts.unity.StakerStorageObjects;

public final class ContractFactory implements IContractFactory {

    @Override
    public byte[] getDeploymentBytes(AvmContract contract) {
        if (contract == null) {
            throw new NullPointerException("Cannot get bytes of null contract!");
        }

        switch (contract) {
            case HELLO_WORLD:
                return new CodeAndArguments(JarBuilder.buildJarForMainAndClasses(HelloWorld.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
            case LOG_TARGET:
                return new CodeAndArguments(JarBuilder.buildJarForMainAndClasses(LogTarget.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
            case STATEFULNESS:
                return new CodeAndArguments(JarBuilder.buildJarForMainAndClasses(Statefulness.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
            case GENERIC_CONTRACT:
                return new CodeAndArguments(JarBuilder.buildJarForMainAndClasses(GenericContract.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
            case INTERNAL_TRANSACTION:
                return new CodeAndArguments(JarBuilder.buildJarForMainAndClasses(InternalTransaction.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
            case DEPLOY_INTERNAL:
                return new CodeAndArguments(getOptimizedDappBytes(DeployAsInternalTransaction.class), new byte[0]).encodeToBytes();
            case UNITY_STAKER_REGISTRY:
                byte[] jar = JarBuilder.buildJarForMainAndClasses(StakerRegistry.class, StakerRegistryEvents.class, StakerStorageObjects.class, StakerRegistryStorage.class);
                byte[] compiledJar = new OptimizedJarBuilder(false, jar, 1).withUnreachableMethodRemover().withConstantRemover().getOptimizedBytes();
                // alternative without optimizations; can be used for debugging
                // byte[] compiledJar = ABICompiler.compileJarBytes(jar, 1).getJarFileBytes();
                return new CodeAndArguments(compiledJar, new byte[0]).encodeToBytes();
            default : throw new IllegalStateException("The following contract is not supported by version 1 of the avm: " + contract);
        }
    }

    @Override
    public byte[] getJarBytes(AvmContract contract) {
        if (contract == null) {
            throw new NullPointerException("Cannot get bytes of null contract!");
        }

        switch (contract) {
            case HELLO_WORLD:
                return JarBuilder.buildJarForMainAndClasses(HelloWorld.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            case LOG_TARGET:
                return JarBuilder.buildJarForMainAndClasses(LogTarget.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            case STATEFULNESS:
                return JarBuilder.buildJarForMainAndClasses(Statefulness.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            case GENERIC_CONTRACT:
                return JarBuilder.buildJarForMainAndClasses(GenericContract.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            case INTERNAL_TRANSACTION:
                return JarBuilder.buildJarForMainAndClasses(InternalTransaction.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            default : throw new IllegalStateException("The following contract is not supported by version 1 of the avm: " + contract);
        }
    }

    public byte[] getOptimizedDappBytes(Class<?> mainClass, Class<?> ...otherClasses) {
        byte[] jarBytes = JarBuilder.buildJarForMainAndClassesAndUserlib(mainClass, otherClasses);
        return new OptimizedJarBuilder(false, jarBytes, 1)
                .withUnreachableMethodRemover()
                .withRenamer()
                .withConstantRemover()
                .getOptimizedBytes();
    }
}
