module aion.zero {

    requires aion.base;
    requires aion.mcf;
    requires aion.log;
    requires java.xml;
    requires slf4j.api;
    requires aion.p2p;
    requires aion.p2p.a0;
    requires aion.rlp;
    requires aion.evtmgr;
    requires aion.evtmgr.impl;
    requires aion.txpool;
    requires aion.crypto;
    requires aion.dbmgr;
    requires aion.zero.api;

    exports org.aion.zero.impl.blockchain;
    exports org.aion.solidity;
    exports org.aion.zero.impl;
    exports org.aion.zero.impl.core;
    exports org.aion.zero.impl.types;
    exports org.aion.zero.impl.config;
    exports org.aion.equihash;
    exports org.aion.zero.impl.cli;
}