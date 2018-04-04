/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p.impl.zero.msg;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;

/**
 * @author chris
 */
public class ResHandshake1Test {

    @Test
    public void test() throws UnsupportedEncodingException {

        // test over Byte.MAX_VALUE
        byte[] randomBytes = new byte[200];
        ThreadLocalRandom.current().nextBytes(randomBytes);
        String randomBinaryVersion = new String(randomBytes, "UTF-8");
        ResHandshake1 rh1 = new ResHandshake1(ThreadLocalRandom.current().nextBoolean(), randomBinaryVersion);

        // test route
        assertEquals(Ver.V0, rh1.getHeader().getVer());
        assertEquals(Ctrl.NET, rh1.getHeader().getCtrl());
        assertEquals(Act.RES_HANDSHAKE, rh1.getHeader().getAction());

        // test encode / decode
        byte[] mhBytes = rh1.encode();
        ResHandshake1 rh2 = ResHandshake1.decode(mhBytes);
        assertEquals(rh1.getSuccess(), rh2.getSuccess());
        String v1 = rh1.getBinaryVersion();
        String v2 = rh2.getBinaryVersion();

        assertEquals(rh1.getBinaryVersion(), rh2.getBinaryVersion());

    }

}