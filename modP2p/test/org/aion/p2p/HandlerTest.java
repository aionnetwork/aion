package org.aion.p2p;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class HandlerTest {

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
        public void receive(int _id, String _displayId, byte[] _msg) {}
    }

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
}
