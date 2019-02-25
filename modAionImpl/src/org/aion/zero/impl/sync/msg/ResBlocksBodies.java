package org.aion.zero.impl.sync.msg;

import java.util.ArrayList;
import java.util.List;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.zero.impl.sync.Act;

/**
 * @author chris TODO: follow same construction, decode & encode rule as ResBlocksHeaders in future.
 *     Need to update INcBlockchain
 */
public final class ResBlocksBodies extends Msg {

    private final List<byte[]> blocksBodies;

    public ResBlocksBodies(final List<byte[]> _blocksBodies) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_BLOCKS_BODIES);
        blocksBodies = _blocksBodies;
    }

    public static ResBlocksBodies decode(final byte[] _msgBytes) {
        RLPList paramsList = (RLPList) RLP.decode2(_msgBytes).get(0);
        List<byte[]> blocksBodies = new ArrayList<>();
        for (RLPElement aParamsList : paramsList) {
            RLPList rlpData = ((RLPList) aParamsList);
            blocksBodies.add(rlpData.getRLPData());
        }
        return new ResBlocksBodies(blocksBodies);
    }

    public List<byte[]> getBlocksBodies() {
        return this.blocksBodies;
    }

    @Override
    public byte[] encode() {
        return RLP.encodeList(this.blocksBodies.toArray(new byte[this.blocksBodies.size()][]));
    }
}
