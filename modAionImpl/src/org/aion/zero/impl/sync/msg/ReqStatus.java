package org.aion.zero.impl.sync.msg;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;

/** @author chris */
public final class ReqStatus extends Msg {

    public ReqStatus() {
        super(Ver.V0, Ctrl.SYNC, Act.REQ_STATUS);
    }

    @Override
    public byte[] encode() {
        return null;
    }
}
