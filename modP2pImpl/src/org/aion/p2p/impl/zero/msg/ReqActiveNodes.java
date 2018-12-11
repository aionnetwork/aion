package org.aion.p2p.impl.zero.msg;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;

/** @author chris */
public final class ReqActiveNodes extends Msg {

    public ReqActiveNodes() {
        super(Ver.V0, Ctrl.NET, Act.REQ_ACTIVE_NODES);
    }

    @Override
    public byte[] encode() {
        return null;
    }
}
