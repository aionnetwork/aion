package org.aion.p2p.v0.msg;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Version;
import org.aion.p2p.v0.Act;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ReqActiveNodesTest {
    @Test
    public void testRoute() {

        ReqActiveNodes req = new ReqActiveNodes();
        assertEquals(Version.V0, req.getHeader().getVer());
        assertEquals(Ctrl.NET, req.getHeader().getCtrl());
        assertEquals(Act.REQ_ACTIVE_NODES, req.getHeader().getAction());

    }
}
