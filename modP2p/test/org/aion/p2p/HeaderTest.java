package org.aion.p2p;

import static junit.framework.Assert.assertEquals;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.Test;

public class HeaderTest {

    private short version = (short)ThreadLocalRandom.current().nextInt();
    private byte ctl = 0;
    private byte action = 4;
    private int length = 8;
    Header hd = new Header(version, ctl, action, length);
    private int route = (version << 16) | (ctl << 8) | action;

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

        try {
            Header hdr = hd.decode(bytes);
            assertEquals(version, hdr.getVer());
            assertEquals(ctl, hd.getCtrl());
            assertEquals(action, hd.getAction());
            assertEquals(length, hd.getLen());
            assertEquals(route, hd.getRoute());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void repeatEncodeDecode() {
        for (int i = 0; i < 100; i++) {
            encodeDecode();
        }
    }
}
