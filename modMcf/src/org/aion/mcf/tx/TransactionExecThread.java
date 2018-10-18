/*******************************************************************************
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
 *
 * Contributors:
 *     Aion foundation.

 ******************************************************************************/
package org.aion.mcf.tx;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.aion.base.type.ITransaction;
import org.aion.mcf.blockchain.IPendingStateInternal;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

/**
 * Thread is responsible for execution of all transactions coming from API
 * (only), Blockchain thread execution is done separately
 * <p>
 *
 * @author yao
 */
public abstract class TransactionExecThread<PS extends IPendingStateInternal, TX extends ITransaction> {

    private final PS pendingState;
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.TX.toString());
    private static final Logger LOGGEN = AionLoggerFactory.getLogger(LogEnum.GEN.toString());

    private final ExecutorService txExec = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable arg0) {
            return new Thread(arg0, "TransactionExecThread");
        }
    });

    protected TransactionExecThread(PS pendingState) {
        this.pendingState = pendingState;
    }

    public Future<List<TX>> submitTransaction(TX tx) {
        Future<List<TX>> txListFuture = txExec.submit(() -> {
            LOG.debug("TransactionExecThread.broadcastTransaction: " + tx.toString());
            return this.pendingState.addPendingTransaction(tx);
        });

        return txListFuture;
    }

    public Future<List<TX>> submitTransaction(List<TX> tx) {
        Future<List<TX>> txListFuture = txExec.submit(() -> {
            return this.pendingState.addPendingTransactions(tx);
        });
        return txListFuture;
    }

    public void shutdown() {
        LOGGEN.info("TransactionExecThread shutting down...");
        txExec.shutdown();
        try {
            LOGGEN.info("TransactionExecThread waiting termination.");
            txExec.awaitTermination(10, TimeUnit.SECONDS);
            LOGGEN.info("TransactionExecThread shutdown... Finished!");
        } catch (Exception e) {
            LOGGEN.error("TransactionExecThread shutdown failed! {}", e.getMessage());
        }
    }
}
