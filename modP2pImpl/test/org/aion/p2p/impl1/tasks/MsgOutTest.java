package org.aion.p2p.impl1.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import java.util.UUID;
import org.aion.p2p.Msg;
import org.aion.p2p.impl1.P2pMgr.Dest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class MsgOutTest {

    @Mock private Msg msg;

    private Random r = new Random();

    @Before
    public void Setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetNodeId() {
        int id = r.nextInt();
        MsgOut msgOut = new MsgOut(id, "", msg, Dest.ACTIVE);
        assertEquals(id, msgOut.getNodeId());
    }

    @Test
    public void testGetDisPlayId() {
        String dsp = UUID.randomUUID().toString();
        MsgOut msgOut = new MsgOut(1, dsp, msg, Dest.ACTIVE);
        assertEquals(dsp, msgOut.getDisplayId());
    }

    @Test
    public void testGetMsg() {
        MsgOut msgOut = new MsgOut(1, "", msg, Dest.ACTIVE);
        assertEquals(msg, msgOut.getMsg());
    }

    @Test
    public void testGetDest() {
        MsgOut msgOut = new MsgOut(1, "", msg, Dest.ACTIVE);
        assertEquals(Dest.ACTIVE, msgOut.getDest());

        msgOut = new MsgOut(1, "", msg, Dest.INBOUND);
        assertEquals(Dest.INBOUND, msgOut.getDest());

        msgOut = new MsgOut(1, "", msg, Dest.OUTBOUND);
        assertEquals(Dest.OUTBOUND, msgOut.getDest());
    }

    @Test
    public void testGetTimeStamp() {
        MsgOut msgOut = new MsgOut(1, "", msg, Dest.ACTIVE);
        assertTrue(msgOut.getTimestamp() <= System.currentTimeMillis());
        assertTrue(0 < msgOut.getTimestamp());
    }

    @Test
    public void testGetLane() {
        for (int i = 0; i < 1000; i++) {
            int id = r.nextInt();
            MsgOut msgOut = new MsgOut(id, "", msg, Dest.ACTIVE);
            assertEquals(TaskSend.hash2Lane(id), msgOut.getLane());
        }
    }
}
