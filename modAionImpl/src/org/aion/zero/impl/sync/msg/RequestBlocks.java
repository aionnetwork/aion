package org.aion.zero.impl.sync.msg;

import java.math.BigInteger;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.zero.impl.sync.Act;

/**
 * Request message for a range of blocks.
 *
 * @author Alexandra Roatis
 */
public final class RequestBlocks extends Msg {

    private final long start;
    private final int count;
    private final boolean descending;

    /**
     * Constructor for block range requests.
     *
     * @param start the height of the first block in the requested range
     * @param count the number of requested blocks in the range
     * @param descending boolean value indicating the order of the blocks:
     *     <ul>
     *       <li>{@code true} indicates that parent blocks are requested (i.e. with descending
     *           heights);
     *       <li>{@code false} is used to request child blocks (i.e. with ascending heights);
     *     </ul>
     */
    public RequestBlocks(final long start, final int count, final boolean descending) {
        super(Ver.V1, Ctrl.SYNC, Act.REQUEST_BLOCKS);

        this.start = start;
        this.count = count > 0 ? count : 1; // defaults to 1 when given incorrect values
        this.descending = descending;
    }

    /**
     * Decodes a message into a block range request.
     *
     * @param message a {@code byte} array representing a request for a trie node.
     * @return the decoded trie node request if valid or {@code null} when the decoding encounters
     *     invalid input
     * @implNote Ensures that the components are not {@code null}.
     */
    public static RequestBlocks decode(final byte[] message) {
        if (message == null || message.length == 0) {
            return null;
        } else {
            RLPList list = (RLPList) RLP.decode2(message).get(0);
            if (list.size() != 3) {
                return null;
            } else {
                // decode the start block
                long start = new BigInteger(1, list.get(0).getRLPData()).longValue();

                // decode the number of blocks requested
                int count = new BigInteger(1, list.get(1).getRLPData()).intValue();

                // decode the requested ordering of the block range
                if (list.get(2).getRLPData().length != 1) {
                    return null;
                }
                boolean descending = list.get(2).getRLPData()[0] == 1;

                return new RequestBlocks(start, count, descending);
            }
        }
    }

    @Override
    public byte[] encode() {
        return RLP.encodeList(
                RLP.encode(start), // not using encodeLong due to non-standard encoding
                RLP.encodeInt(count),
                RLP.encodeByte(descending ? (byte) 1 : (byte) 0));
    }

    /**
     * Returns the height of the first block in the requested range.
     *
     * @return the height of the first block in the requested range
     */
    public long getStart() {
        return start;
    }

    /**
     * Returns the number of requested blocks in the range.
     *
     * @return the number of requested blocks in the range
     */
    public int getCount() {
        return count;
    }

    /**
     * Returns a boolean value indicating the order of the blocks defined as:
     *
     * <ul>
     *   <li>{@code true} indicates that parent blocks are requested (i.e. with descending heights);
     *   <li>{@code false} is used to request child blocks (i.e. with ascending heights);
     * </ul>
     *
     * @return a boolean value indicating the order of the blocks
     */
    public boolean isDescending() {
        return descending;
    }
}
