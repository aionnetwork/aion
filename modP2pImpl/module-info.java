module aion.p2p.impl {
    requires aion.p2p;
    requires aion.base;
    requires aion.log;
    requires miniupnpc.linux;

    exports org.aion.p2p.impl1;
    exports org.aion.p2p.impl.zero.msg;
}
