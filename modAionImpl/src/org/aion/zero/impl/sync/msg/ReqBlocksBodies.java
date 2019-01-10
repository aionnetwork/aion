package org.aion.zero.impl.sync.msg;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;

/** @author chris */
public final class ReqBlocksBodies extends Msg {

    private final List<byte[]> blocksHashes;

    public ReqBlocksBodies(final List<byte[]> _blocksHashes) {
        super(Ver.V0, Ctrl.SYNC, Act.REQ_BLOCKS_BODIES);
        blocksHashes = _blocksHashes;
    }

    public static ReqBlocksBodies decode(final byte[] _msgBytes) {
        if (_msgBytes == null) return null;
        else {
            /*
             * _msgBytes % 32 needs to be equal to 0 TODO: need test & catch
             */
            List<byte[]> blocksHashes = new ArrayList<>();
            ByteBuffer bb = ByteBuffer.wrap(_msgBytes);
            int count = _msgBytes.length / 32;
            while (count > 0) {
                byte[] blockHash = new byte[32];
                bb.get(blockHash);
                blocksHashes.add(blockHash);
                count--;
            }
            return new ReqBlocksBodies(blocksHashes);
        }
    }

    public List<byte[]> getBlocksHashes() {
        return this.blocksHashes;
    }

    @Override
    public byte[] encode() {
        ByteBuffer bb = ByteBuffer.allocate(this.blocksHashes.size() * 32);
        for (byte[] blockHash : this.blocksHashes) {
            bb.put(blockHash);
        }
        return bb.array();
    }
}
