module aion.vm {
    requires aion.mcf;
    requires transitive slf4j.api;
    requires commons.lang3;
    requires aion.util;
    requires aion.fastvm;
    requires aion.precompiled;
    requires com.google.common;
    requires aion.crypto;
    requires aion.types;
    requires aion.base;
    requires aion.rlp;
    requires aion.avm.stub;

    exports org.aion.vm.common;
    exports org.aion.vm.avm;
    exports org.aion.vm.avm.schedule;
}
