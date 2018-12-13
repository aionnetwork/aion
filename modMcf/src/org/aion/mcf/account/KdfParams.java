/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.mcf.account;

import java.io.UnsupportedEncodingException;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
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
        RLPList list = (RLPList) RLP.decode2(bytes).get(0);
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
