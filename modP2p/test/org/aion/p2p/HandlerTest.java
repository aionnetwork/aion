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

package org.aion.p2p;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class HandlerTest {

    @Test
    public void testConstructor() {
        TestHandler mockHandler = new TestHandler((short) 1, (byte) 1, (byte) 1);
        assertNotNull(mockHandler);
        Header hdr = mockHandler.getHeader();
        assertNotNull(hdr);
        assertEquals(hdr.getVer(), (short) 1);
        assertEquals(hdr.getCtrl(), (byte) 1);
        assertEquals(hdr.getAction(), (byte) 1);
    }

    @Test
    public void testConstructor_corner() {
        TestHandler mockHandler = new TestHandler(Short.MAX_VALUE, (byte) 255, (byte) 255);
        assertNotNull(mockHandler);
        Header hdr = mockHandler.getHeader();
        assertNotNull(hdr);
        assertEquals(hdr.getVer(), Short.MAX_VALUE);
        assertEquals(hdr.getCtrl(), (byte) 255);
        assertEquals(hdr.getAction(), (byte) 255);
    }

    @Test
    public void testConstructor_corner2() {
        TestHandler mockHandler = new TestHandler(Short.MIN_VALUE, (byte) 0, (byte) 0);
        assertNotNull(mockHandler);
        Header hdr = mockHandler.getHeader();
        assertNotNull(hdr);
        assertEquals(hdr.getVer(), Short.MIN_VALUE);
        assertEquals(hdr.getCtrl(), (byte) 0);
        assertEquals(hdr.getAction(), (byte) 0);
    }

    @Test
    public void testShutdown() {
        TestHandler mockHandler = new TestHandler((short) 0, (byte) 0, (byte) 0);
        assertNotNull(mockHandler);
        Header hdr = mockHandler.getHeader();
        assertNotNull(hdr);
        assertEquals(hdr.getVer(), (short) 0);
        assertEquals(hdr.getCtrl(), (byte) 0);
        assertEquals(hdr.getAction(), (byte) 0);

        mockHandler.shutDown();
    }

    class TestHandler extends Handler {

        /**
         * @param _ver short
         * @param _ctrl byte
         * @param _act byte
         */
        TestHandler(short _ver, byte _ctrl, byte _act) {
            super(_ver, _ctrl, _act);
        }

        @Override
        public void receive(int _id, String _displayId, byte[] _msg) {
        }
    }
}
