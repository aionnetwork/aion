/*
 * Copyright (c) 2017-2018 Aion foundation.
 *      This file is part of the aion network project.
 *
 *      The aion network project is free software: you can redistribute it
 *      and/or modify it under the terms of the GNU General Public License
 *      as published by the Free Software Foundation, either version 3 of
 *      the License, or any later version.
 *
 *      The aion network project is distributed in the hope that it will
 *      be useful, but WITHOUT ANY WARRANTY; without even the implied
 *      warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *      See the GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with the aion network project source files.
 *      If not, see <https://www.gnu.org/licenses/>.
 *
 *  Contributors:
 *      Aion foundation.
 */

package org.aion.p2p;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class MsgTest {
    class MockMsg extends Msg {

        /**
         * @param _ver short
         * @param _ctrl byte
         * @param _act byte
         * @warning: at the msg construction phase, len of msg is unknown therefore right before
         * socket.write, we need to figure out len before preparing the byte[]
         */
        public MockMsg(short _ver, byte _ctrl, byte _act) {
            super(_ver, _ctrl, _act);
        }

        @Override
        public byte[] encode() {
            return new byte[0];
        }
    }

    MockMsg mockMsg;

    @Test
    public void testConstruct() {
        mockMsg = new MockMsg((short)0, (byte)0, (byte)0);
        Header hdr = mockMsg.getHeader();
        assertNotNull(hdr);
        assertEquals(hdr.getVer(), (short)0);
        assertEquals(hdr.getCtrl(), (byte)0);
        assertEquals(hdr.getAction(), (byte)0);
        assertEquals(hdr.getLen(), (byte)0);
    }

    @Test
    public void testConstruct_corner() {
        mockMsg = new MockMsg(Short.MIN_VALUE, (byte)0, (byte)0);
        Header hdr = mockMsg.getHeader();
        assertNotNull(hdr);
        assertEquals(hdr.getVer(), Short.MIN_VALUE);
        assertEquals(hdr.getCtrl(), (byte)0);
        assertEquals(hdr.getAction(), (byte)0);
        assertEquals(hdr.getLen(), (byte)0);
    }

    @Test
    public void testConstruct_corner2() {
        mockMsg = new MockMsg(Short.MAX_VALUE, (byte)255, (byte)255);
        Header hdr = mockMsg.getHeader();
        assertNotNull(hdr);
        assertEquals(hdr.getVer(), Short.MAX_VALUE);
        assertEquals(hdr.getCtrl(), (byte)255);
        assertEquals(hdr.getAction(), (byte)255);
        assertEquals(hdr.getLen(), (byte)0);
    }
}
