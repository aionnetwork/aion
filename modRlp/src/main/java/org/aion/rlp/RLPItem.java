package org.aion.rlp;

import org.aion.util.bytes.ByteUtil;

/**
 * @author Roman Mandeleil 2014
 * @author modified by aion 2017
 */
public class RLPItem implements RLPElement {

    private static final long serialVersionUID = 4456602029225251666L;

    private final byte[] rlpData;

    /**
     * @implNote Inside the RLP encode/decode logic, there is no difference between null obj and
     * zero-byte array Therefore, put empty array when we see the input data is null
     *
     * @param rlpData byte array represent the encoded rlp data
     */
    public RLPItem(byte[] rlpData) {
        this.rlpData = (rlpData == null) ? ByteUtil.EMPTY_BYTE_ARRAY : rlpData;
    }

    public byte[] getRLPData() {
        // @Jay
        // TODO: the ethereumJ implement the comment code piece, it will make
        // ambiguous with the null RLPItem and the
        // Empty byte array
        // if (rlpData.length == 0) {
        // return null;
        // }
        return rlpData;
    }
}
