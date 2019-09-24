module aion.avm.v2 {
    requires aion.base;
    requires aion.util;
    requires aion.crypto;
    requires aion.mcf;
    requires aion.precompiled;
    requires aion.avm.stub;
    requires aion.types;
    requires org.aion.avm.core;
    requires org.aion.avm.api;
    requires org.aion.avm.rt;
    requires org.aion.avm.userlib;

    exports org.aion.avm.version2;
}
