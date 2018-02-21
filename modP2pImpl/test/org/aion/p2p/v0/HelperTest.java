//package org.aion.p2p.a0;
//
//import static org.junit.Assert.*;
//
//import org.aion.p2p.a0.Helper;
//import org.junit.Test;
//
//public class HelperTest {
//
//    String ipSource, ipVerify;
//    byte[] ipBytes;
//
//    @Test
//    public void test() {
//
//        ipSource = "255.255.255.255";
//        ipBytes = Helper.ipStrToBytes(ipSource);
//        assertNotNull(ipBytes);
//        assertEquals(ipBytes.length, 8);
//        ipVerify = Helper.ipBytesToStr(ipBytes);
//        assertTrue(ipSource.equals(ipVerify));
//
//        ipSource = "000.000.000.000";
//        ipBytes = Helper.ipStrToBytes(ipSource);
//        assertNotNull(ipBytes);
//        assertEquals(ipBytes.length, 8);
//        ipVerify = Helper.ipBytesToStr(ipBytes);
//        assertFalse(ipSource.equals(ipVerify));
//        assertTrue("0.0.0.0".equals(ipVerify));
//
//
//        ipSource = "256.256.256.256";
//        ipBytes = Helper.ipStrToBytes(ipSource);
//        assertNotNull(ipBytes);
//        assertEquals(ipBytes.length, 8);
//        ipVerify = Helper.ipBytesToStr(ipBytes);
//        System.out.println(ipVerify);
//        assertTrue(ipSource.equals(ipVerify));
//
//    }
//
//}
