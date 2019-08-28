module aion.mcf {
    requires aion.util;
    requires aion.crypto;
    requires aion.log;
    requires java.xml;
    requires aion.rlp;
    requires aion.db.impl;
    requires slf4j.api;
    requires com.google.common;
    requires commons.collections4;
    requires core;
    requires aion.types;
    requires aion.base;
    requires aion.fastvm;
    requires commons.lang3;
    requires org.json;

    exports org.aion.mcf.account;
    exports org.aion.mcf.blockchain;
    exports org.aion.mcf.config;
    exports org.aion.mcf.core;
    exports org.aion.mcf.db;
    exports org.aion.mcf.db.exception;
    exports org.aion.mcf.serial;
    exports org.aion.mcf.types;
    exports org.aion.mcf.vm.types;
}
