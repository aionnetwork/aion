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

package org.aion.evtmgr.impl.handler;

import org.aion.evtmgr.IHandler;
import org.junit.Test;
import static junit.framework.TestCase.assertEquals;

public class EventHandlersTest {

    @Test
    public void testInstantiate(){
        IHandler blkHdr = new BlockHandler();
        IHandler txHdr = new TxHandler();
        IHandler consHdr = new ConsensusHandler();
        IHandler minerHdr = new MinerHandler();

        assertEquals(BlockHandler.class, blkHdr.getClass());
        assertEquals(TxHandler.class, txHdr.getClass());
        assertEquals(ConsensusHandler.class, consHdr.getClass());
        assertEquals(MinerHandler.class, minerHdr.getClass());
    }
}
