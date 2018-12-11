package org.aion.evtmgr;

import java.util.Properties;

public final class EventMgrModule {
    private static EventMgrModule singleton = null;
    public static final String MODULENAME = "module_name";

    private static IEventMgr EVTMGR;

    private EventMgrModule(Properties config) throws Exception {
        String moduleName = (String) config.get(MODULENAME);
        if (moduleName != null) {
            EVTMGR =
                    (IEventMgr)
                            getClass()
                                    .getClassLoader()
                                    .loadClass(moduleName)
                                    .getDeclaredConstructor(Properties.class)
                                    .newInstance(config);
            if (EVTMGR == null) {
                throw new Exception("Can not load the event manager module!");
            }
        } else {
            throw new Exception("No module name input!");
        }
    }

    public static EventMgrModule getSingleton(Properties config) throws Exception {
        if (config == null) {
            throw new Exception("empty config!");
        }

        if (EventMgrModule.singleton == null) {
            EventMgrModule.singleton = new EventMgrModule(config);
        }

        return EventMgrModule.singleton;
    }

    public IEventMgr getEventMgr() throws Exception {
        if (EventMgrModule.singleton == null) {
            throw new Exception("Module does not initialzed!");
        }

        return EventMgrModule.EVTMGR;
    }
}
