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
 *     Centrys Inc. <https://centrys.io>
 */

package org.aion.zero.impl;

import org.aion.base.db.IRepository;
import org.aion.evtmgr.IEventMgr;
import org.aion.generic.IGenericAionHub;
import org.aion.mcf.blockchain.IPendingStateInternal;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.db.IBlockStorePow;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.handler.BlockPropagationHandler;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;

public interface IAion0Hub extends IGenericAionHub<AionBlock, A0BlockHeader, AionTransaction, AionTxReceipt, AionTxInfo> {
    boolean isRunning();

    IRepository getRepository();

    IAionBlockchain getBlockchain();

    IBlockStorePow<AionBlock, A0BlockHeader> getBlockStore();

    IPendingStateInternal<AionBlock, AionTransaction> getPendingState();

    IEventMgr getEventMgr();

    BlockPropagationHandler getPropHandler();

    void close();

    SyncMgr getSyncMgr();

    IP2pMgr getP2pMgr();

    AionBlock getStartingBlock();
}
