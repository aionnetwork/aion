module aion.base {
    requires aion.types;
    requires aion.util;
    requires aion.crypto;
    requires aion.rlp;
    requires aion.log;
    requires slf4j.api;
    requires aion.fastvm;
    requires commons.lang3;

    exports org.aion.base;
    exports org.aion.base.db;
}
