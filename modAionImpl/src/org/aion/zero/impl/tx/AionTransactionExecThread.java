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
package org.aion.zero.impl.tx;

import org.aion.mcf.tx.ITransactionExecThread;
import org.aion.mcf.tx.TransactionExecThread;
import org.aion.zero.impl.blockchain.AionPendingStateImpl;
import org.aion.zero.types.AionTransaction;

/**
 * Transaction execution thread for Aion, allows us to inject AionPendingStateImpl
 *
 * @author yao
 */
public class AionTransactionExecThread
        extends TransactionExecThread<AionPendingStateImpl, AionTransaction>
        implements ITransactionExecThread<AionTransaction> {

    private static class AionTransactionExecThreadHolder {
        public static final AionTransactionExecThread INSTANCE =
                new AionTransactionExecThread(AionPendingStateImpl.inst());
    }

    private AionTransactionExecThread(AionPendingStateImpl pendingState) {
        super(pendingState);
    }

    public static AionTransactionExecThread createForTesting(AionPendingStateImpl _pendingState) {
        return new AionTransactionExecThread(_pendingState);
    }

    public static AionTransactionExecThread getInstance() {
        return AionTransactionExecThreadHolder.INSTANCE;
    }
}
