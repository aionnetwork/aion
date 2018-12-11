package org.aion.evtmgr;

import static org.junit.Assert.assertEquals;

import java.util.Properties;
import org.aion.evtmgr.impl.mgr.EventMgrA0;
import org.junit.Test;

public class EventMgrModuleTest {

    @Test
    public void orderedCoverageTest() throws Exception {
        EventMgrModule singletonEventMgrModule;
        Properties prop = new Properties();

        // try initialize with null input
        try {
            singletonEventMgrModule = EventMgrModule.getSingleton(null);
        } catch (Exception e) {
            System.out.println(e.toString());
        }

        // initialize with proper input
        prop.put(EventMgrModule.MODULENAME, "org.aion.evtmgr.impl.mgr.EventMgrA0");
        singletonEventMgrModule = EventMgrModule.getSingleton(prop);

        // test getEventMgr()
        IEventMgr eventMgr = singletonEventMgrModule.getEventMgr();
        assertEquals(EventMgrA0.class, eventMgr.getClass());
        assertEquals(4, eventMgr.getHandlerList().size());
    }
}
