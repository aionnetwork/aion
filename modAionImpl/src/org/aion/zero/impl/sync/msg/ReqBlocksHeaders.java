package org.aion.zero.impl.sync.msg;

import java.nio.ByteBuffer;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;

/** @author chris */
public final class ReqBlocksHeaders extends Msg {

    /** fromBlock(long), take(int) */
    private static final int len = 8 + 4;

    private final long fromBlock;

    private final int take;

    public ReqBlocksHeaders(final long _fromBlock, final int _take) {
        super(Ver.V0, Ctrl.SYNC, Act.REQ_BLOCKS_HEADERS);
        this.fromBlock = _fromBlock;
        this.take = _take;
    }

    public long getFromBlock() {
        return this.fromBlock;
    }

    public int getTake() {
        return this.take;
    }

    public static ReqBlocksHeaders decode(final byte[] _msgBytes) {
        if (_msgBytes == null || _msgBytes.length != len) return null;
        else {
            ByteBuffer bb = ByteBuffer.wrap(_msgBytes);
            long _fromBlock = bb.getLong();
            int _take = bb.getInt();
            return new ReqBlocksHeaders(_fromBlock, _take);
        }
    }

    @Override
    public byte[] encode() {
        ByteBuffer bb = ByteBuffer.allocate(len);
        bb.putLong(this.fromBlock);
        bb.putInt(this.take);
        return bb.array();
    }
}
