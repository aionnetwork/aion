package org.aion.vm;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;

public interface IContractFactory {

    IPrecompiledContract getPrecompiledContract(
            ExecutionContext context,
            IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track);

}
