module aion.db.impl {
    requires slf4j.api;
    requires aion.util;
    requires rocksdbjni;
    requires h2.mvstore;
    requires com.google.common;
    requires mongo.java.driver;
    requires leveldbjni.all;
    requires commons.collections4;
    requires com.github.benmanes.caffeine;

    exports org.aion.db.impl;
    exports org.aion.db.impl.leveldb;
    exports org.aion.db.impl.rocksdb;
    exports org.aion.db.store;
}
