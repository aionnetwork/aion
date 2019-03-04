package org.aion.zero.impl.sync.msg;

import org.aion.interfaces.block.Block;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.zero.impl.sync.Act;

/** @author chris */
public final class BroadcastNewBlock extends Msg {

    @SuppressWarnings("rawtypes")
    private final Block block;

    public BroadcastNewBlock(@SuppressWarnings("rawtypes") final Block __newblock) {
        super(Ver.V0, Ctrl.SYNC, Act.BROADCAST_BLOCK);
        this.block = __newblock;
    }

    @Override
    public byte[] encode() {
        return this.block.getEncoded();
    }

    public static byte[] decode(final byte[] _msgBytes) {
        return RLP.decode2OneItem(_msgBytes, 0).getRLPData();
    }
}
