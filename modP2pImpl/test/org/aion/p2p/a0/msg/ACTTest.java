package org.aion.p2p.a0.msg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.aion.p2p.a0.msg.ACT;
import org.junit.Test;

public class ACTTest {
    
    @Test 
    public void test() {
        
        /**
         * getValue
         */
        for(ACT type: ACT.values()) {
            int typeInt = type.getValue();
            assertEquals(typeInt, ACT.getType(typeInt).getValue());
        }
        
        /**
         * Out range
         */
//        ACT type = ACT.getType(ACT.MIN - 1);
//        assertNull(type);
//        type = ACT.getType(ACT.MAX + 1);
//        assertNull(type);
        
        /**
         * Unregistered
         */
//        type = ACT.getType(ACT.MAX);
//        assertNull(type);
        
    }
}
