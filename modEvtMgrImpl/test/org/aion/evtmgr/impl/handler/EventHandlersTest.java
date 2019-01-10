package org.aion.evtmgr.impl.handler;

import static junit.framework.TestCase.assertEquals;

import org.aion.evtmgr.IHandler;
import org.junit.Test;

public class EventHandlersTest {

    @Test
    public void testInstantiate() {
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
