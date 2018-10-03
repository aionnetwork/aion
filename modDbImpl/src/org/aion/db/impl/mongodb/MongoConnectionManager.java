package org.aion.db.impl.mongodb;

import com.mongodb.ClientSessionOptions;
import com.mongodb.DB;
import com.mongodb.MongoClientURI;
import com.mongodb.TransactionOptions;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class exists to manage singleton instances to a MongoDB server. It is recommended by the Mongo
 * docs to only have a single instance of the {@link com.mongodb.MongoClient}
 */
public class MongoConnectionManager {
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
            mongoClient = MongoClients.create(mongoClientUri);
            this.mongoUriToClientMap.put(mongoClientUri, mongoClient);
            this.activeClientCountMap.put(mongoClientUri, new AtomicInteger(1));
        } else {
            mongoClient = this.mongoUriToClientMap.get(mongoClientUri);
            this.activeClientCountMap.get(mongoClientUri).intValue();
        }

        return mongoClient;
//
//        mongoClient.startSession(ClientSessionOptions.builder().causallyConsistent(true).defaultTransactionOptions(
//            TransactionOptions.builder()..build()))
//
//        // No need to check for existence of the DB here or anything
//        MongoDatabase database = mongoClient.getDatabase(MongoConstants.AION_DB_NAME);
//
//        return database;
    }

    public void closeMongoClientInstance(String mongoClientUri) {
        if (!this.mongoUriToClientMap.containsKey(mongoClientUri) || !this.activeClientCountMap.containsKey(mongoClientUri)) {
            throw new IllegalArgumentException("Unopened client uri");
        }

        int newCount = this.activeClientCountMap.get(mongoClientUri).decrementAndGet();
        if (newCount == 0) {
            this.mongoUriToClientMap.get(mongoClientUri).close();
            this.mongoUriToClientMap.remove(mongoClientUri);
            this.activeClientCountMap.remove(mongoClientUri);
        }
    }
}
