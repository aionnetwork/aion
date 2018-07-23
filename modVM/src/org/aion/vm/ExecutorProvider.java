package org.aion.vm;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;

public interface ExecutorProvider {
    IPrecompiledContract getPrecompiledContract(Address to, Address from, IRepositoryCache track);
    VirtualMachine getVM();
}
