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

package org.aion.evtmgr.impl.mgr;

import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.handler.BlockHandler;
import org.aion.evtmgr.impl.handler.ConsensusHandler;
import org.aion.evtmgr.impl.handler.MinerHandler;
import org.aion.evtmgr.impl.handler.TxHandler;

/**
 * @author jay
 *
 */
public class EventMgrA0 extends EventManager {

    public EventMgrA0() {
        super();

        IHandler txHdr = new TxHandler();
        this.handlers.put(((TxHandler) txHdr).getName(), txHdr);

        IHandler consHdr = new ConsensusHandler();
        this.handlers.put(((ConsensusHandler) consHdr).getName(), consHdr);

        IHandler blkHdr = new BlockHandler();
        this.handlers.put(((BlockHandler) blkHdr).getName(), blkHdr);

        IHandler minerHdr = new MinerHandler();
        this.handlers.put(((MinerHandler) minerHdr).getName(), minerHdr);
    }
}
