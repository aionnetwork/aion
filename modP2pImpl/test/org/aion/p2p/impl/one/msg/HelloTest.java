package org.aion.p2p.impl.one.msg;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.UnsupportedEncodingException;
import org.junit.Test;

public class HelloTest {
    @Test
    public void testHello() {
        String msg = "Hello";
        Hello hello = new Hello(msg);
        assertNotNull(hello);

        String m = hello.getMsg();
        assertNotNull(m);
        assertEquals(msg, m);
    }

    @Test
    public void testEncode() {
        String msg = "Hello";
        Hello hello = new Hello(msg);
        assertNotNull(hello);

        byte[] b = hello.encode();
        assertNotNull(b);
        assertArrayEquals(b, msg.getBytes());
    }

    @Test
    public void testDecode() throws UnsupportedEncodingException {
        String msg = "Hello";
        Hello hello = Hello.decode(msg.getBytes());
        assertNotNull(hello);

        String m = hello.getMsg();
        assertNotNull(m);
        assertEquals(msg, m);
    }
}
