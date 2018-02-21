package org.aion.p2p.a0.msg;

import static org.junit.Assert.*;

import org.aion.p2p.a0.msg.Disconnect;
import org.junit.Test;

public class DisconnectTest {

    String str, _str;
    byte[] encode;
    Disconnect dis;
    
    @Test
    public void test() {
        str = "1234567890";        
        Disconnect dis = new Disconnect(str.getBytes());
        encode = dis.encode();
        assertEquals(encode.length, Disconnect.LEN);
        _str = new String(Disconnect.decode(encode).getReason());
        assertTrue(str.equals(_str.trim()));
        
        str = "AaBb%$ ";
        dis = new Disconnect(str.getBytes());
        encode = dis.encode();
        assertEquals(encode.length, Disconnect.LEN);
        _str = new String(Disconnect.decode(encode).getReason());
        assertTrue(str.trim().equals(_str.trim()));
        
        str = "12345678901234567890123456789012345678901";
        dis = new Disconnect(str.getBytes());
        encode = dis.encode();
        assertEquals(encode.length, Disconnect.LEN);
        _str = new String(Disconnect.decode(encode).getReason());
        assertTrue("1234567890123456789012345678901234567890".equals(_str.trim()));
        
        
        dis = new Disconnect(null);
        encode = dis.encode();
        assertEquals(encode.length, Disconnect.LEN);
        _str = new String(Disconnect.decode(encode).getReason());
        assertTrue("unknown".equals(_str.trim()));
        
    }

}
