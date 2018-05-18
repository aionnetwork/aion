package org.aion.db.impl.redisdb;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import java.util.HashSet;
import java.util.Set;

public class RedisClusterConstants {
    public static final String CHARSET = "ISO-8859-1";

    // implement singleton
    public static JedisCluster getDb() {
        Set<HostAndPort> jedisClusterNodes = new HashSet<>();
        jedisClusterNodes.add(new HostAndPort("localhost", 7000));
        return new JedisCluster(jedisClusterNodes);
    }
}
