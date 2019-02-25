package org.aion.zero.impl.sync.msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.types.AionBlock;

/**
 * Response message to a request for a block range.
 *
 * @author Alexandra Roatis
 */
public final class ResponseBlocks extends Msg {

    private final List<AionBlock> blocks;

    /**
     * Constructor for block range responses.
     *
     * @param blocks a list of consecutive blocks representing the response to a requested range
     * @implNote The given blocks are purposefully not deep copied to minimize resource
     *     instantiation. This is a reasonable choice given that these objects are created to be
     *     encoded and transmitted over the network and therefore there is the expectation that they
     *     will not be utilized further.
     */
    public ResponseBlocks(final List<AionBlock> blocks) {
        super(Ver.V1, Ctrl.SYNC, Act.RESPONSE_BLOCKS);

        // ensure input is not null
        Objects.requireNonNull(blocks);

        this.blocks = blocks;
    }

    /**
     * Decodes a message into a block range response.
     *
     * @param message a {@code byte} array representing a response to a block range request.
     * @return the decoded block range response
     * @implNote The decoder must return {@code null} if any of the component values are {@code
     *     null} or invalid.
     */
    public static ResponseBlocks decode(final byte[] message) {
        if (message == null || message.length == 0) {
            return null;
        } else {
            RLPList list = (RLPList) RLP.decode2(message).get(0);

            List<AionBlock> blocks = new ArrayList<>();
            for (RLPElement encoded : list) {
                blocks.add(new AionBlock(encoded.getRLPData()));
            }
            return new ResponseBlocks(blocks);
        }
    }

    @Override
    public byte[] encode() {
        byte[][] toEncode = new byte[this.blocks.size()][];

        int i = 0;
        for (AionBlock block : blocks) {
            toEncode[i] = block.getEncoded();
            i++;
        }

        return RLP.encodeList(toEncode);
    }
}
