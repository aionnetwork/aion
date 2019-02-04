package org.aion.rlp;

import static org.aion.rlp.Utils.EMPTY_BYTE_ARRAY;

/**
 * @author Roman Mandeleil 2014
 * @author modified by aion 2017
 */
public class RLPItem implements RLPElement {

    private static final long serialVersionUID = 4456602029225251666L;

    private final byte[] rlpData;

    /**
     * @Jay inside the RLP encode/decode logic, there is no difference between null obj and
     * zero-byte array Therefore, put empty array when we see the input data is null
     *
     * @param rlpData data encoded by the rlp protocol
     */
    public RLPItem(byte[] rlpData) {
        this.rlpData = (rlpData == null) ? EMPTY_BYTE_ARRAY : rlpData;
    }

    public byte[] getRLPData() {
        return rlpData;
    }
}
