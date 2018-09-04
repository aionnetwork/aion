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
package org.aion.generic;

import org.aion.base.db.IRepository;
import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.generic.query.QueryInterface;
import org.aion.mcf.core.AbstractTxInfo;
import org.aion.mcf.core.IBlockchain;
import org.aion.mcf.types.AbstractBlockHeader;
import org.aion.mcf.types.AbstractTxReceipt;

import java.util.List;

/**
 * Aion chain interface.
 * 
 */
public interface IGenericAionChain<
        BLK extends IBlock,
        BH extends AbstractBlockHeader,
        TX extends ITransaction,
        TR extends AbstractTxReceipt,
        INFO extends AbstractTxInfo> extends QueryInterface {

    IBlockchain getBlockchain();

    ITransactionFactory<TX> getTransactionFactory();

    void close();

    void broadcastTransaction(TX transaction);

    TR callConstant(TX tx, BLK block);

    IRepository<?, ?, ?> getRepository();

    IRepository<?, ?, ?> getPendingState();

    IRepository<?, ?, ?> getSnapshotTo(byte[] root);

    List<TX> getWireTransactions();

    List<TX> getPendingStateTransactions();

    IGenericAionHub getAionHub();

    void exitOn(long number);

    long estimateTxNrg(TX tx, BLK block);
}
