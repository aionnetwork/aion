package org.aion.p2p.a0;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import org.junit.Test;
import java.util.concurrent.ThreadLocalRandom;

public class ACTTest {

    @Test
    public void test() {

        /*
         * out range
         */
        ACT type = ACT.getType(ACT.MIN - 1);
        assertTrue(ACT.UNKNOWN == type.getValue());
        type = ACT.getType(ACT.MAX + 1);
        assertTrue(ACT.UNKNOWN == type.getValue());

        /*
         * any
         */
        type = ACT.getType(ThreadLocalRandom.current().nextInt());
        assertNotNull(type);

    }
}