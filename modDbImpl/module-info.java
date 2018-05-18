module aion.db.impl {
    requires guava;
    requires slf4j.api;
    requires aion.log;
    requires aion.base;
    requires leveldbjni.all;
    requires rocksdbjni;
    requires h2.mvstore;
    requires jedis;

    exports org.aion.db.impl;
	exports org.aion.db.impl.leveldb;
	exports org.aion.db.impl.rocksdb;
	exports org.aion.db.impl.redisdb;
}
