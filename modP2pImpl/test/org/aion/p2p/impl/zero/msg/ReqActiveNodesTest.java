package org.aion.p2p.impl.zero.msg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;
import org.junit.Test;

/** @author chris */
public class ReqActiveNodesTest {
    @Test
    public void testRoute() {

        ReqActiveNodes req = new ReqActiveNodes();
        assertEquals(Ver.V0, req.getHeader().getVer());
        assertEquals(Ctrl.NET, req.getHeader().getCtrl());
        assertEquals(Act.REQ_ACTIVE_NODES, req.getHeader().getAction());
    }

    @Test
    public void testEncode() {
        byte[] result = new ReqActiveNodes().encode();
        assertNull(result);
    }
}
