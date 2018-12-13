module aion.mcf {
    requires aion.util;
    requires aion.crypto;
    requires aion.base;
    requires aion.log;
    requires java.xml;
    requires aion.rlp;
    requires aion.db.impl;
    requires slf4j.api;
    requires aion.p2p;
    requires com.google.common;
    requires libnsc;
    requires commons.collections4;
    requires aion.vm.api;

    exports org.aion.mcf.account;
    exports org.aion.mcf.blockchain;
    exports org.aion.mcf.config;
    exports org.aion.mcf.blockchain.valid;
    exports org.aion.mcf.core;
    exports org.aion.mcf.db;
    exports org.aion.mcf.db.exception;
    exports org.aion.mcf.ds;
    exports org.aion.mcf.evt;
    exports org.aion.mcf.manager;
    exports org.aion.mcf.mine;
    exports org.aion.mcf.serial;
    exports org.aion.mcf.trie;
    exports org.aion.mcf.tx;
    exports org.aion.mcf.types;
    exports org.aion.mcf.valid;
    exports org.aion.mcf.vm;
    exports org.aion.mcf.vm.types;
}
