module aion.p2p.impl {
    requires aion.p2p;
    requires aion.util;
    requires aion.log;
    requires miniupnpc.linux;
    requires slf4j.api;
    requires jsr305;
    requires commons.collections4;


    exports org.aion.p2p.impl1;
    exports org.aion.p2p.impl.zero.msg;
}
