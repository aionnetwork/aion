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

package org.aion.p2p.impl.zero.msg;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;
import org.junit.Before;
import org.junit.Test;

/** @author chris */
public class ResHandshake1Test {

    @Before
    public void setup () {
        Map<String, String> logMap = new HashMap<>();
        logMap.put(LogEnum.P2P.name(), LogLevel.TRACE.name());
        AionLoggerFactory.init(logMap);
    }

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
        for (int i = 0; i < 10000; i++) {
            try {
                this.test();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testDecodeNull() {
        assertNull(ResHandshake1.decode(null));
        assertNull(ResHandshake1.decode(new byte[1]));

        byte[] msg = new byte[2];
        msg[1] = 2;
        assertNull(ResHandshake1.decode(msg));
    }

    @Test
    public void testEncode() {
        String bv = "0.2.9";
        ResHandshake1 rs1 = new ResHandshake1(true, bv);
        assertNotNull(rs1);

        byte[] ec = rs1.encode();
        assertNotNull(ec);
        assertEquals( 7, ec.length);
        assertEquals( 0x01, ec[0]);
        assertEquals( bv.length(), (int)ec[1]);
        byte[] cmp = Arrays.copyOfRange(ec, 2, ec.length);

        assertArrayEquals(bv.getBytes(), cmp);
    }

    @Test
    public void testEncodeVerTruncated() {
        StringBuilder bv = new StringBuilder();
        String truncatedBv;

        for (int i=0; i< Byte.MAX_VALUE; i++) {
            bv.append("1");
        }
        truncatedBv = bv.toString();
        bv.append("2");

        ResHandshake1 rs1 = new ResHandshake1(true, bv.toString());
        assertNotNull(rs1);

        assertEquals(truncatedBv, rs1.getBinaryVersion());
    }
}
