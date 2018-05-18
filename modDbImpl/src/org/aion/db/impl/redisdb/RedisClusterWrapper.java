package org.aion.db.impl.redisdb;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.db.impl.AbstractDB;
import org.aion.db.impl.DBConstants;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;

public class RedisClusterWrapper extends AbstractDB {
    private JedisCluster db = RedisClusterConstants.getDb();

    public RedisClusterWrapper(String name, String path, boolean enableDbCache, boolean enableDbCompression) {
        super(name, path, enableDbCache, enableDbCompression);
    }

    @Override
    public boolean commitCache(Map<ByteArrayWrapper, byte[]> cache) {
        try {
            for (Map.Entry<ByteArrayWrapper, byte[]> e : cache.entrySet()) {
                if (e.getValue() == null) {
                    db.del(new String(e.getKey().getData(), DBConstants.CHARSET));
                } else {
                    db.set(new String(e.getKey().getData(), DBConstants.CHARSET), new String(e.getValue(), DBConstants.CHARSET));
                }
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return true;
    }

    @Override
    protected byte[] getInternal(byte[] key) {
        try {
            String value = db.get(new String(key, DBConstants.CHARSET));
            if (value != null) {
                return value.getBytes(DBConstants.CHARSET);
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean open() {
        return true;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isCreatedOnDisk() {
        return true;
    }

    @Override
    public long approximateSize() {
        TreeSet<String> keys = new TreeSet<>();

        Map<String, JedisPool> clusterNodes = db.getClusterNodes();
        for (String k : clusterNodes.keySet()) {
            JedisPool jp = clusterNodes.get(k);
            Jedis connection = jp.getResource();
            try {
                keys.addAll(connection.keys("*"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return keys.size();
    }

    @Override
    public boolean isEmpty() {
        check();

        TreeSet<String> keys = new TreeSet<>();

        Map<String, JedisPool> clusterNodes = db.getClusterNodes();
        for (String k : clusterNodes.keySet()) {
            JedisPool jp = clusterNodes.get(k);
            Jedis connection = jp.getResource();
            try {
                keys.addAll(connection.keys("*"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return keys.size() <= 0;
    }

    @Override
    public Set<byte[]> keys() {
        check();

        Set<byte[]> keys = new HashSet<>();
        Map<String, JedisPool> clusterNodes = db.getClusterNodes();

        for (String k : clusterNodes.keySet()) {
            JedisPool jp = clusterNodes.get(k);
            Jedis connection = jp.getResource();
            try {
                Set<String> keysAsString = connection.keys("*");

                for (String key : keysAsString) {
                    keys.add(key.getBytes(DBConstants.CHARSET));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return keys;
    }

    @Override
    public void put(byte[] key, byte[] value) {
        check(key);
        check();

        try {
            db.set(new String(key, DBConstants.CHARSET), new String(value, DBConstants.CHARSET));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void delete(byte[] key) {
        check(key);
        check();

        try {
            db.del(new String(key, DBConstants.CHARSET));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void putBatch(Map<byte[], byte[]> inputMap) {
        check(inputMap.keySet());
        check();

        try {
            for (Map.Entry<byte[], byte[]> e : inputMap.entrySet()) {

                byte[] key = e.getKey();
                byte[] value = e.getValue();

                if (value == null) {
                    db.del(new String(key, DBConstants.CHARSET));
                } else {
                    db.set(new String(key, DBConstants.CHARSET), new String(value, DBConstants.CHARSET));
                }
            }

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void putToBatch(byte[] key, byte[] value) {
        check(key);
        check();

        try {
            if (value == null) {
                db.del(new String(key, DBConstants.CHARSET));
            } else {
                db.set(new String(key, DBConstants.CHARSET), new String(value, DBConstants.CHARSET));
            }

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void commitBatch() {
        // not using batch here
    }

    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        check(keys);

        check();

        try {
            for (byte[] key : keys) {
                db.del(new String(key, DBConstants.CHARSET));
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        try {
            db.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
