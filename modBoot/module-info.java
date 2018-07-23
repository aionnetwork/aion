module aion.boot {

    requires  aion.crypto;
    requires  aion.apiserver;
    requires  aion.zero.impl;
    requires  aion.log;
    requires  aion.evtmgr;
    requires  aion.mcf;
    requires  slf4j.api;
    requires  aion.p2p;
    requires  java.management;

    exports org.aion;
}
