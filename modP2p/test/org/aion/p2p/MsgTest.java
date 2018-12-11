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
         *     socket.write, we need to figure out len before preparing the byte[]
         */
        MockMsg(short _ver, byte _ctrl, byte _act) {
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
        mockMsg = new MockMsg((short) 0, (byte) 0, (byte) 0);
        Header hdr = mockMsg.getHeader();
        assertNotNull(hdr);
        assertEquals(hdr.getVer(), (short) 0);
        assertEquals(hdr.getCtrl(), (byte) 0);
        assertEquals(hdr.getAction(), (byte) 0);
        assertEquals(hdr.getLen(), (byte) 0);
    }

    @Test
    public void testConstruct_corner() {
        mockMsg = new MockMsg(Short.MIN_VALUE, (byte) 0, (byte) 0);
        Header hdr = mockMsg.getHeader();
        assertNotNull(hdr);
        assertEquals(hdr.getVer(), Short.MIN_VALUE);
        assertEquals(hdr.getCtrl(), (byte) 0);
        assertEquals(hdr.getAction(), (byte) 0);
        assertEquals(hdr.getLen(), (byte) 0);
    }

    @Test
    public void testConstruct_corner2() {
        mockMsg = new MockMsg(Short.MAX_VALUE, (byte) 255, (byte) 255);
        Header hdr = mockMsg.getHeader();
        assertNotNull(hdr);
        assertEquals(hdr.getVer(), Short.MAX_VALUE);
        assertEquals(hdr.getCtrl(), (byte) 255);
        assertEquals(hdr.getAction(), (byte) 255);
        assertEquals(hdr.getLen(), (byte) 0);
    }
}
