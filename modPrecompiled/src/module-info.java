module aion.precompiled {
    requires aion.zero;
    requires aion.mcf;
    requires aion.crypto;
    requires slf4j.api;
    requires jsr305;
    requires commons.collections4;
    requires com.google.common;
    requires aion.util;
    requires aion.types;
    requires aion.base;

    exports org.aion.precompiled;
    exports org.aion.precompiled.type;
}
