module aion.vm {
    requires aion.mcf;
    requires transitive slf4j.api;
    requires commons.lang3;
    requires aion.util;
    requires aion.fastvm;
    requires org.aion.avm.core;
    requires aion.precompiled;
    requires com.google.common;
    requires aion.crypto;
    requires aion.types;
    requires aion.base;
    requires aion.rlp;

    exports org.aion.vm.exception;
    exports org.aion.vm.common;
    exports org.aion.vm.avm;
}
