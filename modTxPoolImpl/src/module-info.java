module aion.txpool.impl {
    requires aion.log;
    requires slf4j.api;
    requires aion.base;
    requires aion.txpool;
    requires libnsc;
    requires aion.vm.api;

    provides org.aion.txpool.ITxPool with
            org.aion.txpool.zero.TxPoolA0;

    exports org.aion.txpool.zero;
}
