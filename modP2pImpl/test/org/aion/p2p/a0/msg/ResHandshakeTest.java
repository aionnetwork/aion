package org.aion.p2p.a0.msg;

import static org.junit.Assert.*;

import org.aion.p2p.a0.ACT;
import org.junit.Test;

public class ResHandshakeTest {

    @Test 
    public void testAct() {
        ResHandshake mh1 = new ResHandshake(true);
        assertEquals(ACT.RES_HANDSHAKE, mh1.getAct());
    }
    
    @Test
    public void testEncodeDecode() {

        ResHandshake mh1 = new ResHandshake(true);
        byte[] mhBytes = mh1.encode();
        ResHandshake mh2 = ResHandshake.decode(mhBytes);
        assertTrue(mh2.getSuccess());
        
        mh1 = new ResHandshake(false);
        mhBytes = mh1.encode();
        mh2 = ResHandshake.decode(mhBytes);
        assertFalse(mh2.getSuccess());
        
        
        mh2 = ResHandshake.decode(new byte[0]);
        assertNull(mh2);
               
    }

}
