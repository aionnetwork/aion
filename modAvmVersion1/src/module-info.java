module aion.avm.v1 {
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

    exports org.aion.avm.version1;
    exports org.aion.avm.version1.contracts;
}
