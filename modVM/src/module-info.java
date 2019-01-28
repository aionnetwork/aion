module aion.vm {
    requires aion.base;
    requires aion.mcf;
    requires transitive slf4j.api;
    requires aion.zero;
    requires commons.lang3;
    requires aion.vm.api;
    requires aion.util;
    requires aion.fastvm;
    requires org.aion.avm.core;
    requires aion.precompiled;

    exports org.aion.vm;
}
