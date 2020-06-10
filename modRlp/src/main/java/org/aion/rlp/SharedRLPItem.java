package org.aion.rlp;

import org.aion.util.bytes.ByteUtil;

/**
 * The shared RLP list will only keep raw rlp data reference from the top level of the SharedRLPList
 * and the data index
 * @author Jay Tseng
 */
public class SharedRLPItem implements RLPElement {

    final byte[] rlpData;
    final int pos;
    final int length;

    public SharedRLPItem(byte[] rlpData) {
        this.pos = 0;
        if (rlpData == null) {
            this.rlpData = ByteUtil.EMPTY_BYTE_ARRAY;
            length = 0;
        } else {
            this.rlpData = rlpData;
            this.length = rlpData.length;
        }
    }

    public SharedRLPItem(byte[] rlpData, int pos, int length) {
        if (rlpData == null) {
            this.rlpData = ByteUtil.EMPTY_BYTE_ARRAY;
            this.pos = 0;
            this.length = 0;
        } else {
            if (pos < 0 || length < 0 || (length + pos > rlpData.length)) {
                throw new IllegalArgumentException("invalid pos: " + pos + ", or length: " + length + "rlpDataLength: " + rlpData.length);
            }

            this.pos = pos;
            this.rlpData = rlpData;
            this.length = length;
        }
    }

    /**
     * Return the rlp data by given SharedRLPItem
     * @return the rlp encode byte array represent this RLP item
     */
    @Override
    public byte[] getRLPData() {
        if (length == 0) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        } else {
            byte[] result = new byte[length];
            System.arraycopy(rlpData, pos, result, 0, length);
            return result;
        }
    }

    @Override
    public boolean isList() {
        return false;
    }
}
