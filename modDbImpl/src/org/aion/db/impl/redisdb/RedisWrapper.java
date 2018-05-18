package org.aion.db.impl.redisdb;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.db.impl.AbstractDB;
import org.aion.db.impl.DBConstants;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import java.io.UnsupportedEncodingException;
import java.util.*;

public class RedisWrapper extends AbstractDB {
    private JedisPool pool;

    public RedisWrapper(String name, String path, boolean enableDbCache, boolean enableDbCompression) {
        super(name, path, enableDbCache, enableDbCompression);
        pool = RedisConstants.getDb(name);
    }

    @Override
    public boolean commitCache(Map<ByteArrayWrapper, byte[]> cache) {
        try(Jedis db = pool.getResource()){
            Pipeline tmpBatch = db.pipelined();

            for (Map.Entry<ByteArrayWrapper, byte[]> e : cache.entrySet()) {
                if (e.getValue() == null) {
                    tmpBatch.del(new String(e.getKey().getData(), DBConstants.CHARSET));
                } else {
                    tmpBatch.set(new String(e.getKey().getData(), DBConstants.CHARSET), new String(e.getValue() , DBConstants.CHARSET));
                }
            }

            tmpBatch.sync();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return true;
    }

    @Override
    protected byte[] getInternal(byte[] key) {
        try(Jedis db = pool.getResource()) {
            String value = db.get(new String(key, DBConstants.CHARSET));
            if(value != null){
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
        try(Jedis db = pool.getResource()) {
            return db.isConnected();
        }
    }

    @Override
    public boolean isCreatedOnDisk() {
        return true;
    }

    @Override
    public long approximateSize() {
        check();

        try(Jedis db = pool.getResource()) {
            return db.keys("*").size();
        }
    }

    @Override
    public boolean isEmpty() {
        check();

        try(Jedis db = pool.getResource()) {
            return db.keys("*").size() <= 0;
        }
    }

    @Override
    public Set<byte[]> keys() {
        check();

        Set<byte[]> set = new HashSet<>();
        try(Jedis db = pool.getResource()) {
            Set<String> keysAsString = db.keys("*");
            for(String key : keysAsString){
                set.add(key.getBytes(DBConstants.CHARSET));
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return set;
    }

    @Override
    public void put(byte[] key, byte[] value) {
        check(key);
        check();

        try(Jedis db = pool.getResource()) {
            db.set(new String(key, DBConstants.CHARSET), new String(value, DBConstants.CHARSET));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void delete(byte[] key) {
        check(key);
        check();

         try(Jedis db = pool.getResource()) {
            db.del(new String(key, DBConstants.CHARSET));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void putBatch(Map<byte[], byte[]> inputMap) {
        check(inputMap.keySet());
        check();

        try(Jedis db = pool.getResource()){
            Pipeline tmpBatch = db.pipelined();

            for(Map.Entry<byte[], byte[]> e : inputMap.entrySet()) {

                byte[] key = e.getKey();
                byte[] value = e.getValue();

                if (value == null) {
                    tmpBatch.del(new String(key, DBConstants.CHARSET));
                } else {
                    tmpBatch.set(new String(key, DBConstants.CHARSET), new String(value, DBConstants.CHARSET));
                }
            }

            tmpBatch.sync();

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void putToBatch(byte[] key, byte[] value) {
        check(key);
        check();

        try(Jedis db = pool.getResource()){
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

        try(Jedis db = pool.getResource()){
            Pipeline tmpBatch = db.pipelined();

            for(byte[] key: keys) {
                tmpBatch.del(new String(key, DBConstants.CHARSET));
            }

            tmpBatch.sync();

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        pool.close();
    }
}
