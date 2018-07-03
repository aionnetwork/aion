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

package org.aion.mcf.vm;

import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.slf4j.Logger;

public class AbstractExecutor {
    protected static Logger LOGGER;

    protected IRepository repo;
    protected IRepositoryCache repoTrack;
    protected boolean isLocalCall;
    protected long blockRemainingNrg;

    protected AbstractExecutionResult exeResult;
    protected static Object lock = new Object();
    protected boolean askNonce = true;

    public AbstractExecutor(IRepository _repo,
        boolean _localCall, long _blkRemainingNrg, Logger _logger) {
        this.repo = _repo;
        this.repoTrack = _repo.startTracking();
        this.isLocalCall = _localCall;
        this.blockRemainingNrg = _blkRemainingNrg;
        LOGGER = _logger;
    }


    protected long getNrgUsed() {
        throw new UnsupportedOperationException();
    }

    /**
     * Tells the ContractExecutor to bypass incrementing the account's nonce when execute is called.
     */
    public void setBypassNonce() {
        this.askNonce = false;
    }

    /**
     * Returns the nrg left after execution.
     */
    protected long getNrgLeft() {
        return exeResult.getNrgLeft();
    }

    /**
     * Returns the nrg used after execution.
     */
    protected long getNrgUsed(long limit) {
        return limit - exeResult.getNrgLeft();
    }
}
