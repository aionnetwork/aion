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

package org.aion.generic;

import org.aion.base.db.IRepository;
import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.evtmgr.IEventMgr;
import org.aion.mcf.blockchain.IPendingStateInternal;
import org.aion.mcf.core.AbstractTxInfo;
import org.aion.mcf.core.IBlockchain;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.types.AbstractBlockHeader;
import org.aion.mcf.types.AbstractTxReceipt;
import org.aion.p2p.IP2pMgr;

public interface IGenericAionHub<
        BLK extends IBlock<?, ?>,
        BH extends AbstractBlockHeader,
        TX extends ITransaction,
        TR extends AbstractTxReceipt,
        INFO extends AbstractTxInfo> {
    boolean isRunning();

    IRepository getRepository();

    IBlockchain<BLK, BH, TX, TR, INFO> getBlockchain();

    IBlockStoreBase<BLK, BH> getBlockStore();

    IPendingStateInternal<BLK, TX> getPendingState();

    IEventMgr getEventMgr();

    IBlockPropagationHandler<BLK> getPropHandler();

    void close();

    //TODO: add a sync mgr

    IP2pMgr getP2pMgr();

    BLK getStartingBlock();
}
