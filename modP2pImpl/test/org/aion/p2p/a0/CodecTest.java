package org.aion.p2p.a0;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.aion.p2p.CTRL;
import org.aion.p2p.a0.Codec.Header;
import org.junit.Test;

public class CodecTest {

    @Test
    public void testEncodeDecodeHeader() {
        
        Header h = new Header(3, 1, 0);
        byte[] hBytes = h.encode();
        h = Header.decode(hBytes);
        assertNotNull(h);
        assertEquals(3, h.getCtrl());
        assertEquals(1, h.getAction());
        assertEquals(0, h.getLen());
        
        h = new Header(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        hBytes = h.encode();
        h = Header.decode(hBytes);
        assertNotNull(h);
        assertEquals(0, h.getCtrl());
        assertEquals(0, h.getAction());
        assertEquals(Integer.MAX_VALUE, h.getLen());
        
        h = new Header(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        hBytes = h.encode();
        h = Header.decode(hBytes);
        assertNotNull(h);
        assertEquals(0, h.getCtrl());
        assertEquals(0, h.getAction());
        assertEquals(0, h.getLen());
    }
    
}
