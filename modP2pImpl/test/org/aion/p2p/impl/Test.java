package org.aion.p2p.impl;

import java.util.HashSet;
import java.util.Set;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

public class Test {

    @org.junit.Test
    public void testParseFromP2p(){

        Set<String> ips = new HashSet<>();
        for(int i = 0; i < 3; i++)
            ips.add("192.168.0." + i);
        assertTrue(ips.contains("192.168.0.2"));
        assertFalse(ips.contains("192.168.0.3"));

    }
}
