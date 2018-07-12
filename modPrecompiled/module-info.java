module aion.precompiled {
    requires aion.zero;
    requires aion.mcf;
    requires aion.base;
    requires aion.crypto;
    requires slf4j.api;
    requires aion.fastvm;

    exports org.aion.precompiled;
    exports org.aion.precompiled.type;
}