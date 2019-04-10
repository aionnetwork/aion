package org.aion.zero.impl.sync.msg;

import java.util.ArrayList;
import java.util.List;
import org.aion.interfaces.tx.Transaction;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.zero.impl.sync.Act;

/** @author chris */
public final class BroadcastTx extends Msg {

    private final List<? extends Transaction> txl;

    public BroadcastTx(final List<? extends Transaction> _txl) {
        super(Ver.V0, Ctrl.SYNC, Act.BROADCAST_TX);
        this.txl = _txl;
    }

    /* (non-Javadoc)
     * @see org.aion.net.nio.IMsg#encode()
     */
    @Override
    public byte[] encode() {
        List<byte[]> encodedTx = new ArrayList<>();
        for (Transaction tx : txl) {
            encodedTx.add(tx.getEncoded());
        }

        return RLP.encodeList(encodedTx.toArray(new byte[encodedTx.size()][]));
    }

    /* return the encodedData of the Transaction list, the caller function need to cast the return byte[] array
     */
    public static List<byte[]> decode(final byte[] _msgBytes) {
        RLPList paramsList = (RLPList) RLP.decode2(_msgBytes).get(0);
        List<byte[]> txl = new ArrayList<>();
        for (RLPElement aParamsList : paramsList) {
            RLPList rlpData = ((RLPList) aParamsList);
            txl.add(rlpData.getRLPData());
        }
        return txl;
    }
}
