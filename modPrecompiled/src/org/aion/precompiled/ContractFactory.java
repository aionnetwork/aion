package org.aion.precompiled;

import org.aion.precompiled.contracts.ATB.TokenBridgeContract;
import org.aion.precompiled.contracts.Blake2bHashContract;
import org.aion.precompiled.contracts.EDVerifyContract;
import org.aion.precompiled.contracts.TXHashContract;
import org.aion.precompiled.contracts.TotalCurrencyContract;
import org.aion.precompiled.type.IExternalStateForPrecompiled;
import org.aion.precompiled.type.PrecompiledContract;
import org.aion.precompiled.type.PrecompiledTransactionContext;
import org.aion.types.AionAddress;

/** A factory class that produces pre-compiled contract instances. */
public class ContractFactory {

    /**
     * Returns a new pre-compiled contract such that the address of the new contract is address.
     * Returns null if address does not map to any known contracts.
     *
     * @param context Passed in execution context.
     * @param externalState The current state of the world.
     * @return the specified pre-compiled address.
     */
    public PrecompiledContract getPrecompiledContract(
            PrecompiledTransactionContext context, IExternalStateForPrecompiled externalState) {

        boolean fork_032 = externalState.isFork032Enabled();

        // TODO: need to provide a real solution for the repository here ....

        AionAddress destination = context.destinationAddress;
        if (destination.equals(ContractInfo.TOKEN_BRIDGE.contractAddress)) {
            TokenBridgeContract contract =
                    new TokenBridgeContract(
                            context,
                            externalState,
                            ContractInfo.TOKEN_BRIDGE.ownerAddress,
                            ContractInfo.TOKEN_BRIDGE.contractAddress);

            if (!context.originAddress.equals(ContractInfo.TOKEN_BRIDGE.ownerAddress)
                    && !contract.isInitialized()) {
                return null;
            }

            return contract;
        } else if (destination.equals(ContractInfo.ED_VERIFY.contractAddress)) {
            return fork_032 ? new EDVerifyContract() : null;
        } else if (destination.equals(ContractInfo.BLAKE_2B.contractAddress)) {
            return fork_032 ? new Blake2bHashContract() : null;
        } else if (destination.equals(ContractInfo.TRANSACTION_HASH.contractAddress)) {
            return fork_032 ? new TXHashContract(context) : null;
        } else if (destination.equals(ContractInfo.TOTAL_CURRENCY.contractAddress)) {
            return fork_032
                    ? null
                    : new TotalCurrencyContract(
                            externalState,
                            context.senderAddress,
                            ContractInfo.TOTAL_CURRENCY.ownerAddress);
        } else {
            return null;
        }
    }
}
