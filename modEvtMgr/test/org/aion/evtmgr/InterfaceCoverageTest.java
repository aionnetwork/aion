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
package org.aion.evtmgr;

import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.evtmgr.impl.handler.BlockHandler;
import org.junit.Test;

public class InterfaceCoverageTest {

    @Test
    public void testIEvent(){
        IEvent event = new EventBlock(EventBlock.CALLBACK.ONBLOCK0);
    }

    @Test
    public void testIHandler(){
        IHandler handler = new BlockHandler();
        IHandler.TYPE i = IHandler.TYPE.GETTYPE(1);
        IHandler.TYPE i2 = IHandler.TYPE.GETTYPE(9);
    }
}
