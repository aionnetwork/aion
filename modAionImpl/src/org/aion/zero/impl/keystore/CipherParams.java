package org.aion.zero.impl.keystore;

import java.io.UnsupportedEncodingException;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.SharedRLPList;

public class CipherParams {

    private String iv;

    // rlp

    public byte[] toRlp() {
        byte[] bytesIv = RLP.encodeString(this.iv);
        return RLP.encodeList(bytesIv);
    }

    public static CipherParams parse(byte[] bytes) throws UnsupportedEncodingException {
        RLPElement element = RLP.decode2SharedList(bytes).get(0);
        if (!element.isList()) {
            throw new IllegalArgumentException("The keystore decoded rlp element is not a list");
        }

        SharedRLPList list = (SharedRLPList) element;
        CipherParams cp = new CipherParams();
        cp.setIv(new String(list.get(0).getRLPData(), "US-ASCII"));
        return cp;
    }

    // setters

    public String getIv() {
        return iv;
    }

    // getters

    public void setIv(String iv) {
        this.iv = iv;
    }
}
