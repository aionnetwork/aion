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
package org.aion.evtmgr.impl.abs;

import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.evtmgr.impl.evt.EventConsensus;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class AbstractEventTest {

    @Test
    public void testSetFuncArgs(){
        List<Object> funcArgsList = new ArrayList<>();
        funcArgsList.add(1);

        EventBlock eventBlock = new EventBlock(EventBlock.CALLBACK.ONBLOCK0);

        eventBlock.setFuncArgs(funcArgsList);
        assertEquals(funcArgsList, eventBlock.getFuncArgs());
    }

    @Test
    public void testEventEquality(){
        EventBlock eventBlock1 = new EventBlock(EventBlock.CALLBACK.ONBEST0);
        EventBlock eventBlock2 = new EventBlock(EventBlock.CALLBACK.ONBEST0);
        EventBlock eventBlock3 = new EventBlock(EventBlock.CALLBACK.ONTRACE0);

        assertTrue(eventBlock1.equals(eventBlock2));
        assertFalse(eventBlock1.equals(eventBlock3));
        assertFalse(eventBlock1.equals(123));
    }

    @Test
    public void testHashCode(){
        EventBlock eventBlock1 = new EventBlock(EventBlock.CALLBACK.ONBLOCK0);
        EventBlock eventBlock2 = new EventBlock(EventBlock.CALLBACK.ONBEST0);

        EventConsensus eventConsensus1 = new EventConsensus(EventConsensus.CALLBACK.ON_SYNC_DONE);
        EventConsensus eventConsensus2 = new EventConsensus(EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE);

        // check the hash codes
        System.out.println(eventBlock1.hashCode());
        System.out.println(eventBlock2.hashCode());
        System.out.println(eventConsensus1.hashCode());
        System.out.println(eventConsensus2.hashCode());
    }
}

