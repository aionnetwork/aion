module aion.avm.v3 {
    requires aion.base;
    requires aion.util;
    requires aion.crypto;
    requires aion.mcf;
    requires aion.avm.stub;
    requires aion.types;
    requires org.aion.avm.core;
    requires org.aion.avm.api;
    requires org.aion.avm.rt;
    requires org.aion.avm.userlib;
    requires org.aion.avm.tooling;
    requires org.aion.avm.utilities;
    requires aion.precompiled;

    exports org.aion.avm.version3;
    exports org.aion.avm.version3.contracts;
}