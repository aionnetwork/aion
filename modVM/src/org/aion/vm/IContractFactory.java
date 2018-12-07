package org.aion.vm;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.vm.api.interfaces.TransactionContext;

public interface IContractFactory {

    IPrecompiledContract getPrecompiledContract(
            TransactionContext context,
            IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track);
}
