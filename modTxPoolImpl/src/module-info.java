module aion.txpool.impl {
    requires aion.log;
    requires slf4j.api;
    requires aion.util;
    requires aion.txpool;
    requires aion.vm.api;
    requires aion.types;

    provides org.aion.txpool.ITxPool with
            org.aion.txpool.zero.TxPoolA0;

    exports org.aion.txpool.zero;
}
