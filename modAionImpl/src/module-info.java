module aion.zero.impl {
    uses org.aion.evtmgr.EventMgrModule;

    requires aion.util;
    requires aion.mcf;
    requires aion.log;
    requires aion.p2p;
    requires aion.p2p.impl;
    requires aion.rlp;
    requires aion.evtmgr;
    requires aion.evtmgr.impl;
    requires aion.txpool;
    requires aion.crypto;
    requires aion.db.impl;
    requires aion.precompiled;
    requires aion.fastvm;
    requires slf4j.api;
    requires com.google.common;
    requires info.picocli;
    requires commons.lang3;
    requires commons.collections4;
    requires org.json;
    requires java.management;
    requires jdk.management;
    requires java.xml;
    requires aion.types;
    requires aion.base;
    requires core;
    requires aion.avm.stub;

    exports org.aion.equihash;
    exports org.aion.zero.impl.blockchain;
    exports org.aion.zero.impl;
    exports org.aion.zero.impl.core;
    exports org.aion.zero.impl.types;
    exports org.aion.zero.impl.config;
    exports org.aion.zero.impl.cli;

    opens org.aion.zero.impl.cli;

    exports org.aion.zero.impl.db;
    exports org.aion.zero.impl.sync;
    exports org.aion.zero.impl.config.dynamic;
    exports org.aion.utils;
    exports org.aion.zero.impl.keystore;
    exports org.aion.zero.impl.pendingState;
    exports org.aion.zero.impl.vm.avm;
    exports org.aion.zero.impl.vm.avm.schedule;
    exports org.aion.zero.impl.valid;
}
