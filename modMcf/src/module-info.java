module aion.mcf {
    requires aion.util;
    requires aion.crypto;
    requires aion.log;
    requires java.xml;
    requires aion.rlp;
    requires slf4j.api;
    requires com.google.common;
    requires commons.collections4;
    requires core;
    requires aion.types;
    requires aion.base;
    requires commons.lang3;
    requires org.json;

    exports org.aion.mcf.blockchain;
    exports org.aion.mcf.db;
    exports org.aion.mcf.db.exception;
}
