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

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Future;

import org.aion.base.db.IRepository;
import org.aion.base.type.Address;
import org.aion.mcf.blockchain.IChainInstancePOW;
import org.aion.mcf.blockchain.IPowChain;
import org.aion.zero.impl.AionHub;
import org.aion.zero.impl.query.QueryInterface;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;

/**
 * Aion chain interface.
 * 
 */
public interface IAionChain extends IChainInstancePOW, QueryInterface {

    IPowChain<AionBlock, A0BlockHeader> getBlockchain();

    void close();

    AionTransaction createTransaction(BigInteger nonce, Address to, BigInteger value, byte[] data);

    void broadcastTransaction(AionTransaction transaction);

    AionTxReceipt callConstant(AionTransaction tx, IAionBlock block);

    IRepository<?, ?, ?> getRepository();

    IRepository<?, ?, ?> getPendingState();

    IRepository<?, ?, ?> getSnapshotTo(byte[] root);

    List<AionTransaction> getWireTransactions();

    List<AionTransaction> getPendingStateTransactions();

    AionHub getAionHub();

    void exitOn(long number);

    long estimateTxNrg(AionTransaction tx, IAionBlock block);

}
