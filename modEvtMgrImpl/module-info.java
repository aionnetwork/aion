module aion.evtmgr.impl {
    requires aion.evtmgr;
    requires aion.log;
    requires slf4j.api;
    exports org.aion.evtmgr.impl.abs;
    exports org.aion.evtmgr.impl.callback;
    exports org.aion.evtmgr.impl.handler;
    exports org.aion.evtmgr.impl.mgr;
    exports org.aion.evtmgr.impl.evt;
}
