module aion.db.impl {
    requires guava;
    requires slf4j.api;
    requires aion.log;
    requires aion.base;
    requires leveldbjni.all;
    requires h2.mvstore;
    
	exports org.aion.db.impl;
	//exports org.aion.db.impl.h2;
//	exports org.aion.dbmgr.driver.leveldb;
//	exports org.aion.dbmgr.driver.mockdb;
	//exports org.aion.dbmgr.exception;
}
