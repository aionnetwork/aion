module aion.zero {

    requires aion.base;
    requires aion.rlp;
    requires aion.crypto;
    requires aion.mcf;
    requires slf4j.api;

    exports org.aion.zero.api;
    exports org.aion.zero.db;
    exports org.aion.zero.types;
}