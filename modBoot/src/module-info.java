module aion.boot {
    requires aion.crypto;
    requires aion.apiserver;
    requires aion.zero.impl;
    requires aion.log;
    requires aion.evtmgr;
    requires aion.mcf;
    requires slf4j.api;
    requires aion.p2p;
    requires aion.fastvm;
    requires aion.txpool;
    requires libnzmq;

    requires org.objectweb.asm;
    requires org.objectweb.asm.commons;
    requires org.objectweb.asm.tree;
    requires org.objectweb.asm.tree.analysis;
    requires org.objectweb.asm.util;

    uses org.aion.evtmgr.EventMgrModule;
    uses org.aion.log.AionLoggerFactory;

    requires aion.util;
    requires aion.avm.stub;

    exports org.aion;
}
