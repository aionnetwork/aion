package org.aion.zero.impl.sync.msg;

import java.util.ArrayList;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.SharedRLPList;
import org.aion.zero.impl.sync.Act;

/** @author chris */
public final class BroadcastTx extends Msg {

    private final List<AionTransaction> txl;

    public BroadcastTx(final List<AionTransaction> _txl) {
        super(Ver.V0, Ctrl.SYNC, Act.BROADCAST_TX);
        this.txl = _txl;
    }

    /* (non-Javadoc)
     * @see org.aion.net.nio.IMsg#encode()
     */
    @Override
    public byte[] encode() {
        List<byte[]> encodedTx = new ArrayList<>();
        for (AionTransaction tx : txl) {
            encodedTx.add(tx.getEncoded());
        }

        return RLP.encodeList(encodedTx.toArray(new byte[encodedTx.size()][]));
    }

    /* return the encodedData of the Transaction list, the caller function need to cast the return byte[] array
     */
    public static List<byte[]> decode(final byte[] _msgBytes) {
        SharedRLPList paramsList = (SharedRLPList) RLP.decode2SharedList(_msgBytes).get(0);
        List<byte[]> txl = new ArrayList<>();
        for (RLPElement aParamsList : paramsList) {
            txl.add(SharedRLPList.getRLPDataCopy((SharedRLPList) aParamsList));
        }
        return txl;
    }
}
