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
import org.aion.zero.types.A0BlockHeader;

/** @author chris */
public final class ResBlocksHeaders extends Msg {

    private final List<A0BlockHeader> blockHeaders;

    public ResBlocksHeaders(final List<A0BlockHeader> _blockHeaders) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_BLOCKS_HEADERS);
        blockHeaders = _blockHeaders;
    }

    public static ResBlocksHeaders decode(final byte[] _msgBytes) {
        if (_msgBytes == null || _msgBytes.length == 0) return null;
        else {
            try {
                RLPList list = (RLPList) RLP.decode2(_msgBytes).get(0);
                List<A0BlockHeader> blockHeaders = new ArrayList<>();
                for (RLPElement aList : list) {
                    RLPList rlpData = ((RLPList) aList);
                    blockHeaders.add(A0BlockHeader.fromRLP(rlpData, true));
                }
                return new ResBlocksHeaders(blockHeaders);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public List<A0BlockHeader> getHeaders() {
        return this.blockHeaders;
    }

    @Override
    public byte[] encode() {
        List<byte[]> tempList = new ArrayList<>();
        for (A0BlockHeader blockHeader : this.blockHeaders) {
            tempList.add(blockHeader.getEncoded());
        }
        byte[][] bytesArray = tempList.toArray(new byte[tempList.size()][]);
        return RLP.encodeList(bytesArray);
    }
}
