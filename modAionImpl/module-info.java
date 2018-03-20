module aion.zero.impl {
    requires aion.base;
    requires aion.mcf;
    requires aion.log;
    requires java.xml;
    requires slf4j.api;
    requires aion.p2p;
    requires aion.p2p.impl;
    requires aion.rlp;
    requires aion.evtmgr;
    requires aion.evtmgr.impl;
    requires aion.txpool;
    requires aion.crypto;
    requires aion.db.impl;
    requires aion.zero;
    requires aion.fastvm;
    requires jdk.management;

    exports org.aion.equihash;
    exports org.aion.zero.impl.blockchain;
    exports org.aion.zero.impl;
    exports org.aion.zero.impl.core;
    exports org.aion.zero.impl.types;
    exports org.aion.zero.impl.config;
    exports org.aion.zero.impl.cli;
    exports org.aion.zero.impl.db;
}