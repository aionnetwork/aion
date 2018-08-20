module aion.zero {

    requires aion.base;
    requires aion.rlp;
    requires aion.crypto;
    requires aion.mcf;
    requires aion.evtmgr;
    requires aion.p2p;
    requires slf4j.api;

    exports org.aion.zero.api;
    exports org.aion.zero.db;
    exports org.aion.zero.types;
    exports org.aion.zero.exceptions;
    exports org.aion.zero.blockchain;

    exports org.aion.generic;
}