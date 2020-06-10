package org.aion.rlp;

import java.util.ArrayList;

/**
 * @author Roman Mandeleil
 * @since 21.04.14
 */
public class RLPList extends ArrayList<RLPElement> implements RLPElement {

    private static final long serialVersionUID = -2855280911054117106L;

    private byte[] rlpData;

    public void setRLPData(byte[] rlpData) {
        this.rlpData = rlpData;
    }

    @Override
    public byte[] getRLPData() {
        return rlpData;
    }

    @Override
    public boolean isList() {
        throw  new UnsupportedOperationException();
    }
}
