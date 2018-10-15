package org.aion.zero.impl.sync.msg;

import com.google.common.base.Preconditions;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.types.AionTxInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/** Response for transaction receipts request */
public class ResTxReceipts extends Msg {
    private final List<AionTxInfo> txInfo;

    /**
     * Constructor
     *
     * @param txInfo list of transaction receipts
     */
    public ResTxReceipts(List<AionTxInfo> txInfo) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_TX_RECEIPT_HEADERS);
        this.txInfo = new LinkedList<>(txInfo);
    }

    /**
     * Constructor
     *
     * @param msg RLP-encoded representation of a ResTxReceipts (or equivalently, list of transaction
     *            receipts).  Must not be null.
     */
    public ResTxReceipts(byte[] msg) {
        this(decode(msg));
    }

    /**
     * Decode byte array into list of tx receipts (inverse operation of {@link #encode()}
     *
     * @param msgBytes ReqTxReceipts message encoded by {@link #encode()}
     * @return list of transaction hashes of a ReqTxReceipts
     */
    private static List<AionTxInfo> decode(byte[] msgBytes) {
        Preconditions.checkNotNull(msgBytes, "Cannot decode null message bytes to ResTxReceipts");

        RLPList list = (RLPList) RLP.decode2(msgBytes).get(0);
        List<AionTxInfo> infos = new LinkedList<>();

        for (RLPElement elem : list) {
            byte[] elemData = elem.getRLPData();
            infos.add(new AionTxInfo(elemData));
        }
        return infos;
    }

    /** @return the list of transaction receipts */
    public List<AionTxInfo> getTxInfo() {
        return Collections.unmodifiableList(txInfo);
    }

    @Override
    public byte[] encode() {
        List<byte[]> receipts = new ArrayList<>();
        for (AionTxInfo txr : this.txInfo) {
            receipts.add(txr.getEncoded());
        }
        byte[][] bytesArray = receipts.toArray(new byte[receipts.size()][]);
        return RLP.encodeList(bytesArray);
    }
}
