package org.aion.p2p.impl.zero.msg;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;

/** @author chris */
public class ResHandshake extends Msg {

    private final boolean success;

    public static final int LEN = 1;

    public ResHandshake(boolean _success) {
        super(Ver.V0, Ctrl.NET, Act.RES_HANDSHAKE);
        this.success = _success;
    }

    public boolean getSuccess() {
        return this.success;
    }

    public static ResHandshake decode(final byte[] _bytes) {
        if (_bytes == null || _bytes.length < LEN) return null;
        else return new ResHandshake(_bytes[0] == 0x01);
    }

    @Override
    public byte[] encode() {
        return this.success ? new byte[] {0x01} : new byte[] {0x00};
    }
}
