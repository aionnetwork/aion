package org.aion.avm.version3;

import org.aion.avm.core.dappreading.UserlibJarBuilder;
import org.aion.avm.stub.IContractFactory;
import org.aion.avm.tooling.deploy.OptimizedJarBuilder;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;
import org.aion.avm.userlib.abi.ABIException;
import org.aion.avm.userlib.abi.ABIToken;
import org.aion.avm.version3.contracts.*;

public final class ContractFactory implements IContractFactory {

    @Override
    public byte[] getDeploymentBytes(AvmContract contract) {
        if (contract == null) {
            throw new NullPointerException("Cannot get bytes of null contract!");
        }

        switch (contract) {
            case HELLO_WORLD:
                return new CodeAndArguments(getOptimizedDappBytes(HelloWorld.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
            case LOG_TARGET:
                return new CodeAndArguments(getOptimizedDappBytes(LogTarget.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
            case STATEFULNESS:
                return new CodeAndArguments(getOptimizedDappBytes(Statefulness.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
            case GENERIC_CONTRACT:
                return new CodeAndArguments(getOptimizedDappBytes(GenericContract.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
            case INTERNAL_TRANSACTION:
                return new CodeAndArguments(getOptimizedDappBytes(InternalTransaction.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
            case TRANSACTION_HASH:
                return new CodeAndArguments(getOptimizedDappBytes(TransactionHash.class), new byte[0]).encodeToBytes();
            case META_TRANSACTION_PROXY:
                return new CodeAndArguments(getOptimizedDappBytes(MetaTransactionProxy.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
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
                return getOptimizedDappBytes(HelloWorld.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            case LOG_TARGET:
                return getOptimizedDappBytes(LogTarget.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            case STATEFULNESS:
                return getOptimizedDappBytes(Statefulness.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            case GENERIC_CONTRACT:
                return getOptimizedDappBytes(GenericContract.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            case INTERNAL_TRANSACTION:
                return getOptimizedDappBytes(InternalTransaction.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            case TRANSACTION_HASH:
                return getOptimizedDappBytes(TransactionHash.class);
            case META_TRANSACTION_PROXY:
                return getOptimizedDappBytes(MetaTransactionProxy.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class);
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
