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
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/
package org.aion.zero.impl.blockchain;

import org.aion.base.db.IRepository;
import org.aion.factort.AionTransactionFactory;
import org.aion.generic.IGenericAionChain;
import org.aion.zero.impl.IAion0Hub;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;

import java.util.List;

/**
 * Aion chain interface.
 * 
 */
public interface IAionChain extends
        IGenericAionChain<IAionBlock, A0BlockHeader, AionTransaction, AionTxReceipt, AionTxInfo> {

    IAionBlockchain getBlockchain();

    AionTransactionFactory getTransactionFactory();

    void close();

    void broadcastTransaction(AionTransaction transaction);

    AionTxReceipt callConstant(AionTransaction tx, IAionBlock block);

    IRepository<?, ?, ?> getRepository();

    IRepository<?, ?, ?> getPendingState();

    IRepository<?, ?, ?> getSnapshotTo(byte[] root);

    List<AionTransaction> getWireTransactions();

    List<AionTransaction> getPendingStateTransactions();

    IAion0Hub getAionHub();

    void exitOn(long number);

    long estimateTxNrg(AionTransaction tx, IAionBlock block);
}
