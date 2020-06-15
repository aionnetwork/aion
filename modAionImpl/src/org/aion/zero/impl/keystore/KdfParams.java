package org.aion.zero.impl.keystore;

import java.io.UnsupportedEncodingException;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.SharedRLPList;
import org.aion.util.bytes.ByteUtil;

public class KdfParams {

    private int c;
    private int dklen;
    private int n;
    private int p;
    private int r;
    private String salt;

    // rlp

    public byte[] toRlp() {
        byte[] bytesC = RLP.encodeInt(this.c);
        byte[] bytesDklen = RLP.encodeInt(this.dklen);
        byte[] bytesN = RLP.encodeInt(this.n);
        byte[] bytesP = RLP.encodeInt(this.p);
        byte[] bytesR = RLP.encodeInt(this.r);
        byte[] bytesSalt = RLP.encodeString(this.salt);
        return RLP.encodeList(bytesC, bytesDklen, bytesN, bytesP, bytesR, bytesSalt);
    }

    public static KdfParams parse(byte[] bytes) throws UnsupportedEncodingException {
        RLPElement element = RLP.decode2SharedList(bytes).get(0);
        if (!element.isList()) {
            throw new IllegalArgumentException("The keystore decoded rlp element is not a list");
        }

        SharedRLPList list = (SharedRLPList) element;
        KdfParams kdfParams = new KdfParams();
        kdfParams.setC(ByteUtil.byteArrayToInt(list.get(0).getRLPData()));
        kdfParams.setDklen(ByteUtil.byteArrayToInt(list.get(1).getRLPData()));
        kdfParams.setN(ByteUtil.byteArrayToInt(list.get(2).getRLPData()));
        kdfParams.setP(ByteUtil.byteArrayToInt(list.get(3).getRLPData()));
        kdfParams.setR(ByteUtil.byteArrayToInt(list.get(4).getRLPData()));
        kdfParams.setSalt(new String(list.get(5).getRLPData(), "US-ASCII"));
        return kdfParams;
    }

    // setters

    public void setC(int c) {
        this.c = c;
    }

    public void setDklen(int dklen) {
        this.dklen = dklen;
    }

    public void setN(int n) {
        this.n = n;
    }

    public void setP(int p) {
        this.p = p;
    }

    public void setR(int r) {
        this.r = r;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    // getters

    public int getC() {
        return c;
    }

    public int getDklen() {
        return dklen;
    }

    public int getN() {
        return n;
    }

    public int getP() {
        return p;
    }

    public int getR() {
        return r;
    }

    public String getSalt() {
        return salt;
    }
}
