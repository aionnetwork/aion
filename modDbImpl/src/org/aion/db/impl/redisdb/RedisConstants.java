package org.aion.db.impl.redisdb;

import org.aion.db.impl.DBConstants;
import redis.clients.jedis.JedisPool;

public class RedisConstants {
    public static JedisPool getDb(String dbName) {
        switch(dbName) {
            case DBConstants.Names.BLOCK:
                return new JedisPool("localhost", 7001);
            case DBConstants.Names.INDEX:
                return new JedisPool("localhost", 7002);
            case DBConstants.Names.DETAILS:
                return new JedisPool("localhost", 7003);
            case DBConstants.Names.STORAGE:
                return new JedisPool("localhost", 7004);
            case DBConstants.Names.STATE:
                return new JedisPool("localhost", 7000);
            case DBConstants.Names.TRANSACTION:
                return new JedisPool("localhost", 7005);
            case DBConstants.Names.TX_CACHE:
                return new JedisPool("localhost", 7006);
            case DBConstants.Names.TX_POOL:
                return new JedisPool("localhost", 7007);
            default:
                return null;
        }
    }
}