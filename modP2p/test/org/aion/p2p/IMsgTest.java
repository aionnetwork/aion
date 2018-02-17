package org.aion.p2p;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class IMsgTest {

    @Test
    public void testCTRL() {
        /*
         * getValue 
         */
        for(CTRL type: CTRL.values()) {
            int typeInt = type.getValue();
            assertEquals(typeInt, CTRL.getType(typeInt).getValue());
        }

        /*
         * Out of range
         */
        CTRL type = CTRL.getType(CTRL.MIN - 1);
        assertEquals(CTRL.UNKNOWN, type);
        type = CTRL.getType(CTRL.MAX + 1);
        assertEquals(CTRL.UNKNOWN, type);
    }
}
