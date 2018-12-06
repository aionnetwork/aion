module aion.zero {
    requires aion.base;
    requires aion.rlp;
    requires aion.crypto;
    requires aion.mcf;
    requires slf4j.api;
    requires libJson;
    requires commons.lang3;
    requires aion.vm.api;

    exports org.aion.zero.api;
    exports org.aion.zero.db;
    exports org.aion.zero.types;
    exports org.aion.zero.exceptions;
}
