module aion.vm {
    requires aion.mcf;
    requires transitive slf4j.api;
    requires aion.zero;
    requires commons.lang3;
    requires aion.util;
    requires aion.fastvm;
    requires org.aion.avm.core;
    requires aion.precompiled;
    requires com.google.common;
    requires aion.crypto;
    requires aion.types;
    requires aion.base;

    exports org.aion.vm;
    exports org.aion.vm.exception;
}
