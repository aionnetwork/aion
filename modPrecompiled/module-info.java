module aion.precompiled {
    requires aion.zero;
    requires aion.mcf;
    requires aion.base;
    requires aion.crypto;
    requires aion.vm;
    requires slf4j.api;

    exports org.aion.precompiled;
    exports org.aion.precompiled.type;
}
