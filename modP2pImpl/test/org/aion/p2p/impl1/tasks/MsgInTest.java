package org.aion.p2p.impl1.tasks;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.UUID;
import org.aion.p2p.P2pConstant;
import org.junit.Test;

public class MsgInTest {

    private Random r = new Random();

    @Test
    public void testGetNodeId() {
        int id = r.nextInt();
        MsgIn msg = new MsgIn(id, "", 1, new byte[0]);
        assertEquals(id, msg.getNodeId());
    }

    @Test
    public void testGetDisPlayId() {
        String dsp = UUID.randomUUID().toString();
        MsgIn msg = new MsgIn(1, dsp, 1, new byte[0]);
        assertEquals(dsp, msg.getDisplayId());
    }

    @Test
    public void testGetRoute() {
        int rut = r.nextInt();
        MsgIn msg = new MsgIn(1, "", rut, new byte[0]);
        assertEquals(rut, msg.getRoute());
    }

    @Test
    public void testGetMsg() {
        int len = r.nextInt(P2pConstant.MAX_BODY_SIZE);
        MsgIn msg = new MsgIn(1, "", 1, new byte[len]);
        assertEquals(len, msg.getMsg().length);
    }
}
