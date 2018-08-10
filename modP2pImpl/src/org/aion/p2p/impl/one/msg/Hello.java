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

package org.aion.p2p.impl.one.msg;

import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import java.io.UnsupportedEncodingException;

/**
 *
 * @author chris
 *
 * Test versioning
 *
 */
public final class Hello extends Msg {

    private String msg;

    public Hello(String _msg){
        super(Ver.V1, (byte)0, (byte)0);
        this.msg = _msg;
    }

    public String getMsg(){
        return this.msg;
    }

    public static Hello decode(final byte[] _bytes) throws UnsupportedEncodingException {
        String msg;
        try{
            msg = new String(_bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw e;
        }
        return new Hello(msg);
    }

    @Override
    public byte[] encode() {
        return this.msg.getBytes();
    }

}
