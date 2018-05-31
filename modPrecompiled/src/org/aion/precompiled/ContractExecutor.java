/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.precompiled;

import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;

/**
 * The executor of pre-compiled contracts.
 */
public class ContractExecutor {
    private static final Object lock = new Object();
    private final Logger txLogger;
    private boolean isLocalCall;
    private long blockRemainingNrg;
    private boolean askNonce = true;
    private IRepository<AccountState, IDataWord, IBlockStoreBase<?, ?>> repo;
    private IAionBlock block;

    private long nrgLimit;

    private AionTransaction tx;
    private IRepositoryCache repoTrack;
    private ContractExecutionResult exeResult;

    public ContractExecutor(AionTransaction tx, IAionBlock block,
        IRepository<AccountState, IDataWord, IBlockStoreBase<?, ?>> repo, boolean isLocalCall,
        long blockRemainingNrg, Logger logger) {

        txLogger = logger;
        if (logger.isDebugEnabled()) {
            logger.debug("Executing transaction: {}", tx);
        }

        this.tx = tx;
        this.repo = repo;
        this.repoTrack = this.repo.startTracking();
        this.isLocalCall = isLocalCall;
        this.blockRemainingNrg = blockRemainingNrg;
        this.block = block;
        this.nrgLimit = tx.nrgLimit() - tx.transactionCost(block.getNumber());
        this.exeResult = new ContractExecutionResult(ResultCode.SUCCESS, this.nrgLimit);
    }

    //TODO

}
