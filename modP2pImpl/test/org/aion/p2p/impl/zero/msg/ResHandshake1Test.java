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

import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.ThreadLocalRandom;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;
import org.junit.Test;

/** @author chris */
public class ResHandshake1Test {

    @Test
    public void test() throws UnsupportedEncodingException {

        // test over Byte.MAX_VALUE
        byte[] randomBytes = new byte[200];
        ThreadLocalRandom.current().nextBytes(randomBytes);
        String randomBinaryVersion = new String(randomBytes, "UTF-8");

        ResHandshake1 rh1 =
                new ResHandshake1(ThreadLocalRandom.current().nextBoolean(), randomBinaryVersion);

        // test route
        assertEquals(Ver.V0, rh1.getHeader().getVer());
        assertEquals(Ctrl.NET, rh1.getHeader().getCtrl());
        assertEquals(Act.RES_HANDSHAKE, rh1.getHeader().getAction());

        // test encode / decode
        byte[] mhBytes = rh1.encode();
        ResHandshake1 rh2 = ResHandshake1.decode(mhBytes);

        assertEquals(rh1.getSuccess(), rh2.getSuccess());
        assertEquals(rh1.getBinaryVersion().length(), rh2.getBinaryVersion().length());
        assertEquals(rh1.getBinaryVersion(), rh2.getBinaryVersion());
    }

    @Test
    public void testMultiple() {

        // Repeat the test multiple times to ensure validity
        for (int i = 0; i < 30; i++) {
            try {
                this.test();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }
}
