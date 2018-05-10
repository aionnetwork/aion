package org.aion.db.impl.redisdb;

import redis.clients.jedis.JedisPool;

public class RedisDBConstants {
    public static final String CHARSET = "ISO-8859-1";

    private static final String BLOCK = "block";
    private static final String INDEX = "index";

    private static final String DETAILS = "details";
    private static final String STORAGE = "storage";

    private static final String STATE = "state";
    private static final String TRANSACTION = "transaction";

    private static final String TX_CACHE = "pendingtxCache";
    private static final String TX_POOL = "pendingtxPool";

    public static JedisPool getDb(String dbName) {
        switch(dbName) {
            case BLOCK:
                return new JedisPool("localhost", 7000);
            case INDEX:
                return new JedisPool("localhost", 7001);
            case DETAILS:
                return new JedisPool("localhost", 7002);
            case STORAGE:
                return new JedisPool("localhost", 7003);
            case STATE:
                return new JedisPool("localhost", 7004);
            case TRANSACTION:
                return new JedisPool("localhost", 7005);
            case TX_CACHE:
                return new JedisPool("localhost", 7006);
            case TX_POOL:
                return new JedisPool("localhost", 7007);
            default:
                return null;
        }
    }
}