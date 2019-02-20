package org.aion.zero.impl.sync.msg;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.google.common.base.Preconditions;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;

/** Request for transaction receipts */
public class RequestReceipts extends Msg {

    private final List<byte[]> txHashes;
    private static final int TX_RECEIPT_LENGTH = 32;

    /**
     * Constructor
     *
     * @param txHashes hashes of the transaction receipts requested
     */
    public RequestReceipts(List<byte[]> txHashes) {
        super(Ver.V0, Ctrl.SYNC, Act.REQUEST_RECEIPTS);
        this.txHashes = new LinkedList<>(txHashes);
    }

    /**
     * Constructor
     *
     * @param msgBytes List of transaction hashes, as encoded by {@link #encode())} (or
     *     equivalently, a ReqTxReceipts). Must not be null.
     */
    public RequestReceipts(byte[] msgBytes) {
        this(decode(msgBytes));
    }

    /**
     * Decode byte array into list of tx hashes (inverse operation of {@link #encode()}
     *
     * @param msgBytes ReqTxReceipts message encoded by {@link #encode()}
     * @return list of transaction hashes of a ReqTxReceipts
     */
    private static List<byte[]> decode(byte[] msgBytes) {
        Preconditions.checkNotNull(msgBytes, "Cannot decode null message bytes to ReqTxReceipts");
        Preconditions.checkArgument(
                msgBytes.length % TX_RECEIPT_LENGTH == 0,
                "Invalid encoding of ReqTxReceipts; length must be a multiple of 32, but was "
                        + msgBytes.length);

        List<byte[]> blocksHashes = new LinkedList<>();
        ByteBuffer bb = ByteBuffer.wrap(msgBytes);

        for (int ix = 0; ix < msgBytes.length; ix += TX_RECEIPT_LENGTH) {
            byte[] receipt = new byte[TX_RECEIPT_LENGTH];
            bb.get(receipt);
            blocksHashes.add(receipt);
        }

        return blocksHashes;
    }

    /** @return the hashes of the requested transaction receipts. */
    public List<byte[]> getTxHashes() {
        return Collections.unmodifiableList(txHashes);
    }

    @Override
    public byte[] encode() {
        ByteBuffer bb = ByteBuffer.allocate(txHashes.size() * TX_RECEIPT_LENGTH);
        for (byte[] blockHash : this.txHashes) {
            bb.put(blockHash);
        }
        return bb.array();
    }
}
