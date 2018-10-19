module aion.db.impl {
    requires slf4j.api;
    requires aion.log;
    requires aion.base;
    requires leveldbjni.all;
    requires rocksdbjni;
    requires h2.mvstore;
    requires com.google.common;

    exports org.aion.db.impl;
    exports org.aion.db.impl.leveldb;
    exports org.aion.db.impl.rocksdb;
}
