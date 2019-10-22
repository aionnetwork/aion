package org.aion.avm.version2;

import org.aion.avm.core.dappreading.UserlibJarBuilder;
import org.aion.avm.stub.IContractFactory;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;
import org.aion.avm.userlib.abi.ABIException;
import org.aion.avm.userlib.abi.ABIToken;
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
                return new CodeAndArguments(UserlibJarBuilder.buildJarForMainAndClasses(HelloWorld.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
            case LOG_TARGET:
                return new CodeAndArguments(UserlibJarBuilder.buildJarForMainAndClasses(LogTarget.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
            case STATEFULNESS:
                return new CodeAndArguments(UserlibJarBuilder.buildJarForMainAndClasses(Statefulness.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
            case GENERIC_CONTRACT:
                return new CodeAndArguments(UserlibJarBuilder.buildJarForMainAndClasses(GenericContract.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
            case INTERNAL_TRANSACTION:
                return new CodeAndArguments(UserlibJarBuilder.buildJarForMainAndClasses(InternalTransaction.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
            case TRANSACTION_HASH:
                return new CodeAndArguments(UserlibJarBuilder.buildJarForMainAndClasses(TransactionHash.class), new byte[0]).encodeToBytes();
            case META_TRANSACTION_PROXY:
                return new CodeAndArguments(UserlibJarBuilder.buildJarForMainAndClasses(MetaTransactionProxy.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes();
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
                return UserlibJarBuilder.buildJarForMainAndClasses(HelloWorld.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            case LOG_TARGET:
                return UserlibJarBuilder.buildJarForMainAndClasses(LogTarget.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            case STATEFULNESS:
                return UserlibJarBuilder.buildJarForMainAndClasses(Statefulness.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            case GENERIC_CONTRACT:
                return UserlibJarBuilder.buildJarForMainAndClasses(GenericContract.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            case INTERNAL_TRANSACTION:
                return UserlibJarBuilder.buildJarForMainAndClasses(InternalTransaction.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            case TRANSACTION_HASH:
                return UserlibJarBuilder.buildJarForMainAndClasses(TransactionHash.class);
            case META_TRANSACTION_PROXY:
                return UserlibJarBuilder.buildJarForMainAndClasses(MetaTransactionProxy.class, ABIEncoder.class, ABIDecoder.class, ABIException.class, ABIToken.class);
            default : throw new IllegalStateException("The following contract is not supported by version 2 of the avm: " + contract);
        }
    }
}
