module aion.zero {
    requires aion.util;
    requires aion.rlp;
    requires aion.crypto;
    requires aion.mcf;
    requires slf4j.api;
    requires org.json;
    requires commons.lang3;
    requires com.google.common;
    requires aion.types;
    requires core;
    requires aion.base;

    exports org.aion.zero.api;
    exports org.aion.zero.types;
}
