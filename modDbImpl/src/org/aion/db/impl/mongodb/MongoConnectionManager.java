package org.aion.db.impl.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

/**
 * This class exists to manage singleton instances to a MongoDB server. It is recommended by the Mongo
 * docs to only have a single instance of the {@link com.mongodb.MongoClient} opened at a time, so this
 * class keeps track of reference counting active instances and will close the connection once all instances
 * are done being used.
 */
public class MongoConnectionManager {
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    private MongoConnectionManager() {
        // Private constructor to force using the Singleton
    }

    private static class Holder {
        static final MongoConnectionManager INSTANCE = new MongoConnectionManager();
    }

    public static MongoConnectionManager inst() {
        return Holder.INSTANCE;
    }

    private Map<String, MongoClient> mongoUriToClientMap = new ConcurrentHashMap<>();
    private Map<String, AtomicInteger> activeClientCountMap = new ConcurrentHashMap<>();

    public MongoClient getMongoClientInstance(String mongoClientUri) {
        MongoClient mongoClient;
        if (!this.mongoUriToClientMap.containsKey(mongoClientUri)) {
            LOG.info("Creating new mongo client to connect to {}", mongoClientUri);
            mongoClient = MongoClients.create(mongoClientUri);
            this.mongoUriToClientMap.put(mongoClientUri, mongoClient);
            this.activeClientCountMap.put(mongoClientUri, new AtomicInteger(1));
        } else {
            LOG.info("Reusing existing mongo client for {}", mongoClientUri);
            mongoClient = this.mongoUriToClientMap.get(mongoClientUri);
            this.activeClientCountMap.get(mongoClientUri).incrementAndGet();
        }

        return mongoClient;
    }

    public void closeMongoClientInstance(String mongoClientUri) {
        if (!this.mongoUriToClientMap.containsKey(mongoClientUri) || !this.activeClientCountMap.containsKey(mongoClientUri)) {
            throw new IllegalArgumentException(String.format("Unopened client uri %s", mongoClientUri));
        }

        int newCount = this.activeClientCountMap.get(mongoClientUri).decrementAndGet();
        if (newCount == 0) {
            LOG.info("Closing mongo client connection for {}", mongoClientUri);

            this.mongoUriToClientMap.get(mongoClientUri).close();
            this.mongoUriToClientMap.remove(mongoClientUri);
            this.activeClientCountMap.remove(mongoClientUri);
        }
    }
}
