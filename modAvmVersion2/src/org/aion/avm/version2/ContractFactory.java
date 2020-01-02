package org.aion.avm.version2;

import org.aion.avm.core.dappreading.UserlibJarBuilder;
import org.aion.avm.stub.IContractFactory;
import org.aion.avm.tooling.deploy.OptimizedJarBuilder;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.avm.version2.contracts.GenericContract;
import org.aion.avm.version2.contracts.HelloWorld;
import org.aion.avm.version2.contracts.InternalTransaction;
import org.aion.avm.version2.contracts.LogTarget;
import org.aion.avm.version2.contracts.MetaTransactionProxy;
import org.aion.avm.version2.contracts.Statefulness;
import org.aion.avm.version2.contracts.TransactionHash;

public final class ContractFactory implements IContractFactory {

    @Override
    public byte[] getDeploymentBytes(AvmContract contract) {
        if (contract == null) {
            throw new NullPointerException("Cannot get bytes of null contract!");
        }

        switch (contract) {
            case HELLO_WORLD:
                return new CodeAndArguments(getOptimizedDappBytes(HelloWorld.class), new byte[0]).encodeToBytes();
            case LOG_TARGET:
                return new CodeAndArguments(getOptimizedDappBytes(LogTarget.class), new byte[0]).encodeToBytes();
            case STATEFULNESS:
                return new CodeAndArguments(getOptimizedDappBytes(Statefulness.class), new byte[0]).encodeToBytes();
            case GENERIC_CONTRACT:
                return new CodeAndArguments(getOptimizedDappBytes(GenericContract.class), new byte[0]).encodeToBytes();
            case INTERNAL_TRANSACTION:
                return new CodeAndArguments(getOptimizedDappBytes(InternalTransaction.class), new byte[0]).encodeToBytes();
            case TRANSACTION_HASH:
                return new CodeAndArguments(getOptimizedDappBytes(TransactionHash.class), new byte[0]).encodeToBytes();
            case META_TRANSACTION_PROXY:
                return new CodeAndArguments(getOptimizedDappBytes(MetaTransactionProxy.class), new byte[0]).encodeToBytes();
            default : throw new IllegalStateException("The following contract is not supported by version 2 of the avm: " + contract);
        }
    }

    @Override
    public byte[] getJarBytes(AvmContract contract) {
        if (contract == null) {
            throw new NullPointerException("Cannot get bytes of null contract!");
        }

        switch (contract) {
            case HELLO_WORLD:
                return getOptimizedDappBytes(HelloWorld.class);
            case LOG_TARGET:
                return getOptimizedDappBytes(LogTarget.class);
            case STATEFULNESS:
                return getOptimizedDappBytes(Statefulness.class);
            case GENERIC_CONTRACT:
                return getOptimizedDappBytes(GenericContract.class);
            case INTERNAL_TRANSACTION:
                return getOptimizedDappBytes(InternalTransaction.class);
            case TRANSACTION_HASH:
                return getOptimizedDappBytes(TransactionHash.class);
            case META_TRANSACTION_PROXY:
                return getOptimizedDappBytes(MetaTransactionProxy.class);
            default : throw new IllegalStateException("The following contract is not supported by version 2 of the avm: " + contract);
        }
    }
    
    public byte[] getOptimizedDappBytes(Class<?> mainClass, Class<?> ...otherClasses) {
        byte[] jarBytes = UserlibJarBuilder.buildJarForMainAndClassesAndUserlib(mainClass, otherClasses);
        return new OptimizedJarBuilder(false, jarBytes, 1)
                .withUnreachableMethodRemover()
                .withRenamer()
                .withConstantRemover()
                .getOptimizedBytes();
    }
}
