package org.aion.vm;

import org.aion.vm.api.interfaces.TransactionContext;

public interface IContractFactory {

    // Using FastVM interface for now... until full solution in place.

    IPrecompiledContract getPrecompiledContract(
            TransactionContext context,
            KernelInterfaceForFastVM track);
}
