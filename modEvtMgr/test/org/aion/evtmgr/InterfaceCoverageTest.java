package org.aion.evtmgr;

import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.evtmgr.impl.handler.BlockHandler;
import org.junit.Test;

public class InterfaceCoverageTest {

    @Test
    public void testIEvent() {
        IEvent event = new EventBlock(EventBlock.CALLBACK.ONBLOCK0);
    }

    @Test
    public void testIHandler() {
        IHandler handler = new BlockHandler();
        IHandler.TYPE i = IHandler.TYPE.GETTYPE(1);
        IHandler.TYPE i2 = IHandler.TYPE.GETTYPE(9);
    }
}
