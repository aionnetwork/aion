/*******************************************************************************
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
 *
 * Contributors:
 *     Aion foundation.
 *
 ******************************************************************************/
package org.aion.mcf.account;

import java.io.UnsupportedEncodingException;

import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;

public class CipherParams {

    private String iv;

    // rlp

    public byte[] toRlp() {
        byte[] bytesIv = RLP.encodeString(this.iv);
        return RLP.encodeList(bytesIv);
    }

    public static CipherParams parse(byte[] bytes) throws UnsupportedEncodingException {
        RLPList list = (RLPList) RLP.decode2(bytes).get(0);
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