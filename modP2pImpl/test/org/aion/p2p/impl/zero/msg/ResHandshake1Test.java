package org.aion.p2p.impl.zero.msg;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

/** @author chris */
public class ResHandshake1Test {
    @Mock
    private Logger p2pLOG;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void test() throws UnsupportedEncodingException {

        // test over Byte.MAX_VALUE
        byte[] randomBytes = new byte[200];
        ThreadLocalRandom.current().nextBytes(randomBytes);
        String randomBinaryVersion = new String(randomBytes, "UTF-8");

        ResHandshake1 rh1 =
                new ResHandshake1(p2pLOG, ThreadLocalRandom.current().nextBoolean(), randomBinaryVersion);

        // test route
        assertEquals(Ver.V0, rh1.getHeader().getVer());
        assertEquals(Ctrl.NET, rh1.getHeader().getCtrl());
        assertEquals(Act.RES_HANDSHAKE, rh1.getHeader().getAction());

        // test encode / decode
        byte[] mhBytes = rh1.encode();
        ResHandshake1 rh2 = ResHandshake1.decode(mhBytes, p2pLOG);

        assertEquals(rh1.getSuccess(), rh2.getSuccess());
        assertEquals(rh1.getBinaryVersion().length(), rh2.getBinaryVersion().length());
        assertEquals(rh1.getBinaryVersion(), rh2.getBinaryVersion());
    }

    @Test
    public void testMultiple() {
        // Repeat the test multiple times to ensure validity
        for (int i = 0; i < 10000; i++) {
            try {
                this.test();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testDecodeNull() {
        assertNull(ResHandshake1.decode(null, p2pLOG));
        assertNull(ResHandshake1.decode(new byte[1], p2pLOG));

        byte[] msg = new byte[2];
        msg[1] = 2;
        assertNull(ResHandshake1.decode(msg, p2pLOG));
    }

    @Test
    public void testEncode() {
        String bv = "0.2.9";
        ResHandshake1 rs1 = new ResHandshake1(p2pLOG, true, bv);
        assertNotNull(rs1);

        byte[] ec = rs1.encode();
        assertNotNull(ec);
        assertEquals(7, ec.length);
        assertEquals(0x01, ec[0]);
        assertEquals(bv.length(), (int) ec[1]);
        byte[] cmp = Arrays.copyOfRange(ec, 2, ec.length);

        assertArrayEquals(bv.getBytes(), cmp);
    }

    @Test
    public void testEncodeVerTruncated() {
        StringBuilder bv = new StringBuilder();
        String truncatedBv;

        for (int i = 0; i < Byte.MAX_VALUE; i++) {
            bv.append("1");
        }
        truncatedBv = bv.toString();
        bv.append("2");

        ResHandshake1 rs1 = new ResHandshake1(p2pLOG, true, bv.toString());
        assertNotNull(rs1);

        assertEquals(truncatedBv, rs1.getBinaryVersion());
    }
}
