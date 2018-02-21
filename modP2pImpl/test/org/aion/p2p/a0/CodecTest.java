package org.aion.p2p.a0;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.aion.p2p.CTRL;
import org.aion.p2p.a0.Codec.Header;
import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;

public class CodecTest {

    @Test
    public void testEncodeDecodeHeader() {

        byte[] verBytes = new byte[2];
        byte[] ctrlBytes = new byte[1];
        byte[] actBytes = new byte[1];

        ThreadLocalRandom.current().nextBytes(verBytes);
        ThreadLocalRandom.current().nextBytes(ctrlBytes);
        ThreadLocalRandom.current().nextBytes(actBytes);

        short ver = (short) (verBytes[0] << 8 | verBytes[1] & 0xFF);
        byte ctrl = ctrlBytes[0];
        byte act = actBytes[0];
        int len = ThreadLocalRandom.current().nextInt();

        Header h = new Header(ver, ctrl, act, len);
        byte[] hBytes = h.encode();
        h = Header.decode(hBytes);
        assertNotNull(h);
        assertEquals(ver, h.getVer());
        assertEquals(ctrl, h.getCtrl());
        assertEquals(act, h.getAction());
        assertEquals(len, h.getLen());

    }
    
}
