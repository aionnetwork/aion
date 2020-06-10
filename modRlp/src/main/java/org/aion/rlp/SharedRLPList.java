package org.aion.rlp;

import java.util.ArrayList;
import java.util.Objects;

/**
 * The shared RLP list will only keep raw rlp data reference from the top level of the SharedRLPList
 * and the data index
 * @author Jay Tseng
 */
public class SharedRLPList extends ArrayList<RLPElement> implements RLPElement {

    final byte[] rlpData;
    final int pos;
    final int length;

    public SharedRLPList(byte[] rlpData) {
        Objects.requireNonNull(rlpData);
        this.rlpData = rlpData;
        pos = 0;
        length = rlpData.length;
    }

    public SharedRLPList(byte[] rlpData, int pos, int length) {
        Objects.requireNonNull(rlpData);
        if (pos < 0 || length < 0 || (length + pos > rlpData.length)) {
            throw new IllegalArgumentException("invalid pos: " + pos + ", or length: " + length + "rlpDataLength: " + rlpData.length);
        }

        this.rlpData = rlpData;
        this.pos = pos;
        this.length = length;
    }

    @Override
    public byte[] getRLPData() {
        return rlpData;
    }

    @Override
    public boolean isList() {
        return true;
    }

    /**
     * Helper method for identifying the data type by given SharedRLPList and position
     * @param list SharedRLPList
     * @param pos the index of the rlpEncode data of the SharedRLPList
     * @return the rlp encode type
     */
    public static int getDataType(SharedRLPList list, int pos) {
        Objects.requireNonNull(list);
        if (pos >= list.pos + list.length) {
            throw new IndexOutOfBoundsException();
        }

        return  list.rlpData[pos] & 0xFF;
    }

    /**
     * Helper method for returning the rlp data by given SharedRLPList
     * @param list SharedRLPList
     * @return the rlp encode byte array represent this RLP List
     */
    public static byte[] getRLPDataCopy(SharedRLPList list) {
        Objects.requireNonNull(list);
        byte[] result = new byte[list.length];
        System.arraycopy(list.rlpData, list.pos, result, 0, list.length);
        return result;
    }
}
