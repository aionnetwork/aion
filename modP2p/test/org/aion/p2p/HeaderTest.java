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

package org.aion.p2p;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.ThreadLocalRandom;
import org.junit.Before;
import org.junit.Test;

public class HeaderTest {

    private short version;
    private byte ctl;
    private byte action;
    private int length;
    private Header hd;
    private int route;

    @Before
    public void Setup() {
        version = (short) ThreadLocalRandom.current().nextInt();
        ctl = 0;
        action = 4;
        length = 8;
        hd = new Header(version, ctl, action, length);
        route = (version << 16) | (ctl << 8) | action;
    }

    @Test
    public void testHeader() {
        assertEquals(version, hd.getVer());
        assertEquals(ctl, hd.getCtrl());
        assertEquals(action, hd.getAction());
        assertEquals(length, hd.getLen());
        assertEquals(route, hd.getRoute());
    }

    @Test
    public void testHeaderLen() {
        hd.setLen(40);
        assertEquals(40, hd.getLen());
    }

    @Test
    public void encodeDecode() {
        byte[] bytes = hd.encode();
        Header hdr = Header.decode(bytes);
        assertEquals(version, hdr.getVer());
        assertEquals(ctl, hd.getCtrl());
        assertEquals(action, hd.getAction());
        assertEquals(length, hd.getLen());
        assertEquals(route, hd.getRoute());
    }

    @Test
    public void encodeDecode2() {
        hd.setLen(P2pConstant.MAX_BODY_SIZE);
        byte[] bytes = hd.encode();
        Header hdr = Header.decode(bytes);
        assertEquals(version, hdr.getVer());
        assertEquals(ctl, hd.getCtrl());
        assertEquals(action, hd.getAction());
        assertEquals(P2pConstant.MAX_BODY_SIZE, hd.getLen());
        assertEquals(route, hd.getRoute());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void encodeDecode3() {
        hd.setLen(P2pConstant.MAX_BODY_SIZE + 1);
        byte[] bytes = hd.encode();
        Header.decode(bytes);
    }

    @Test
    public void repeatEncodeDecode() {
        for (int i = 0; i < 100; i++) {
            encodeDecode();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodeThrow() {
        Header.decode(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodeThrow2() {
        byte[] data = new byte[length - 1];
        Header.decode(data);
    }
}
