package org.aion.zero.impl.sync.msg;

import java.util.ArrayList;
import java.util.List;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.SharedRLPList;
import org.aion.zero.impl.sync.Act;

/**
 * @author chris TODO: follow same construction, decode & encode rule as ResBlocksHeaders in future.
 *     Need to update INcBlockchain
 */
public final class ResBlocksBodies extends Msg {

    private final Object[] blocksBodies;

    public ResBlocksBodies(final Object[] _blocksBodies) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_BLOCKS_BODIES);
        blocksBodies = _blocksBodies;
    }

    public static ResBlocksBodies decodeUsingRef(final byte[] _msgBytes) {
        SharedRLPList list = RLP.decode2SharedList(_msgBytes);
        if (list.isEmpty()) {
            return new ResBlocksBodies(null);
        } else {
            RLPElement element = list.get(0);
            if (element.isList()) {
                return new ResBlocksBodies(((SharedRLPList)element).toArray());
            } else {
                return new ResBlocksBodies(null);
            }
        }
    }

    public List<SharedRLPList> getBlocksBodies() {
        List<SharedRLPList> res = new ArrayList<>();
        for (Object o : blocksBodies) {
            if (o instanceof byte[]) {
                res.add(RLP.decode2SharedList((byte[]) o));
            } else {
                res.add((SharedRLPList) o);
            }
        }

        return res;
    }

    @Override
    public byte[] encode() {
        byte[][] rawContain = new byte[blocksBodies.length][];
        for (int i=0; i<blocksBodies.length; i++) {
            Object o = blocksBodies[i];
            if (o instanceof byte[]) {
                rawContain[i] = (byte[]) o;
            } else {
                rawContain[i] = SharedRLPList.getRLPDataCopy((SharedRLPList) o);
            }
        }
        return RLP.encodeList(rawContain);
    }
}
