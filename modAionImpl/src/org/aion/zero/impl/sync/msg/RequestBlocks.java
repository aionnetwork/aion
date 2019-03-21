package org.aion.zero.impl.sync.msg;

import static org.aion.p2p.V1Constants.HASH_SIZE;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.V1Constants;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.zero.impl.sync.Act;

/**
 * Request message for a range of blocks. Allows making the request either by hash or by number.
 * Also, the range can be requested in ascending or descending order.
 *
 * @author Alexandra Roatis
 */
public final class RequestBlocks extends Msg {

    private final Long startHeight; // using Long to be able to set it to null
    private final byte[] startHash;
    private final boolean isNumber;
    private final int count;
    private final boolean isDescending;

    /**
     * Constructor for block range requests by height.
     *
     * @param startHeight the height of the first block in the requested range
     * @param count the number of requested blocks in the range
     * @param isDescending boolean value indicating the order of the blocks:
     *     <ul>
     *       <li>{@code true} indicates that parent blocks are requested (i.e. with descending
     *           heights);
     *       <li>{@code false} is used to request child blocks (i.e. with ascending heights);
     *     </ul>
     */
    public RequestBlocks(final long startHeight, final int count, final boolean isDescending) {
        super(Ver.V1, Ctrl.SYNC, Act.REQUEST_BLOCKS);

        this.startHeight = startHeight > 0 ? startHeight : 1L; // defaults to 1 when incorrect
        this.isNumber = true;
        this.startHash = null;
        this.count = count > 0 ? count : 1; // defaults to 1 when given incorrect values
        this.isDescending = isDescending;
    }

    /**
     * Constructor for block range requests by block hash.
     *
     * @param startHash the hash of the first block in the requested range
     * @param count the number of requested blocks in the range
     * @param isDescending boolean value indicating the order of the blocks:
     *     <ul>
     *       <li>{@code true} indicates that parent blocks are requested (i.e. with descending
     *           heights);
     *       <li>{@code false} is used to request child blocks (i.e. with ascending heights);
     *     </ul>
     *
     * @throws NullPointerException when the startHash parameter is null
     * @throws IllegalArgumentException when the startHash parameter is not a hash (i.e. has the
     *     correct number of elements)
     */
    public RequestBlocks(final byte[] startHash, final int count, final boolean isDescending) {
        super(Ver.V1, Ctrl.SYNC, Act.REQUEST_BLOCKS);

        Objects.requireNonNull(startHash);
        if (startHash.length != V1Constants.HASH_SIZE) {
            throw new IllegalArgumentException(
                    "The given value "
                            + Arrays.toString(startHash)
                            + " is not a correct block hash.");
        }

        this.startHeight = null;
        this.isNumber = false;
        this.startHash = startHash;
        this.count = count > 0 ? count : 1; // defaults to 1 when given incorrect values
        this.isDescending = isDescending;
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
            RLPList list = RLP.decode2(message);
            if (list.get(0) instanceof RLPList) {
                list = (RLPList) list.get(0);
            } else {
                return null;
            }

            if (list.size() != 4) {
                return null;
            } else {
                // decode the requested type (number or hash)
                byte value = new BigInteger(1, list.get(0).getRLPData()).byteValue();
                if (value < 0 || value > 1) {
                    return null;
                }
                boolean isNumber = value == 1;

                // decode the number of blocks requested
                int count = new BigInteger(1, list.get(2).getRLPData()).intValue();
                if (count <= 0) {
                    return null;
                }

                // decode the requested ordering of the block range
                value = new BigInteger(1, list.get(3).getRLPData()).byteValue();
                if (value < 0 || value > 1) {
                    return null;
                }
                boolean descending = value == 1;

                if (isNumber) {
                    // decode the start block
                    long start = new BigInteger(1, list.get(1).getRLPData()).longValue();
                    if (start <= 0) {
                        return null;
                    }
                    // number based request
                    return new RequestBlocks(start, count, descending);
                } else {
                    byte[] start = list.get(1).getRLPData();
                    if (start.length != HASH_SIZE) {
                        return null;
                    }
                    // hash based request
                    return new RequestBlocks(start, count, descending);
                }
            }
        }
    }

    @Override
    public byte[] encode() {
        return RLP.encodeList(
                RLP.encodeByte(isNumber ? (byte) 1 : (byte) 0),
                isNumber
                        ? RLP.encode(startHeight) //  encodeLong has non-standard encoding
                        : RLP.encodeElement(startHash),
                RLP.encodeInt(count),
                RLP.encodeByte(isDescending ? (byte) 1 : (byte) 0));
    }

    public boolean isNumber() {
        return isNumber;
    }

    /**
     * Returns the height of the first block in the requested range.
     *
     * @return the height of the first block in the requested range
     * @implNote The height is set to {@code null} when the hash was used in creating the request.
     *     It should only be requested when {@link #isNumber()} is {@code true}.
     */
    public Long getStartHeight() {
        return startHeight;
    }

    /**
     * Returns the hash of the first block in the requested range.
     *
     * @return the hash of the first block in the requested range
     * @implNote The hash is set to {@code null} when the height was used in creating the request.
     *     It should only be requested when {@link #isNumber()} is {@code false}.
     */
    public byte[] getStartHash() {
        return startHash;
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
        return isDescending;
    }
}
