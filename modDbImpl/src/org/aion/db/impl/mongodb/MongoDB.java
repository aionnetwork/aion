package org.aion.db.impl.mongodb;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.ClientSessionOptions;
import com.mongodb.ReadPreference;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.ReadConcern;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.aion.base.db.PersistenceMethod;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.db.impl.AbstractDB;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.rocksdb.Transaction;

public class MongoDB extends AbstractDB {

    private static class WriteBatch {
        private List<WriteModel<BsonDocument>> edits = new ArrayList<>();

        public WriteBatch addEdit(byte[] key, byte[] value) {
            if (value == null) {
                DeleteOneModel deleteModel = new DeleteOneModel<>(
                    eq(MongoConstants.ID_FIELD_NAME, new BsonBinary(key))
                );

                edits.add(deleteModel);
            } else {
                UpdateOneModel updateModel = new UpdateOneModel<>(
                    eq(MongoConstants.ID_FIELD_NAME, new BsonBinary(key)),
                    Updates.set(MongoConstants.VALUE_FIELD_NAME, new BsonBinary(value)),
                    new UpdateOptions().upsert(true));

                edits.add(updateModel);
            }

            return this;
        }

        public WriteBatch addEdit(ByteArrayWrapper key, byte[] value) {
            return addEdit(key.getData(), value);
        }

        public WriteBatch addEdits(Map<byte[], byte[]> kvPairs) {
            for (byte[] key : kvPairs.keySet()) {
                addEdit(key, kvPairs.get(key));
            }

            return this;
        }

        public WriteBatch addEditsWrapper(Map<ByteArrayWrapper, byte[]> kvPairs) {
            for (ByteArrayWrapper key : kvPairs.keySet()) {
                addEdit(key, kvPairs.get(key));
            }

            return this;
        }

        public List<WriteModel<BsonDocument>> getEdits() {
            return this.edits;
        }

        public long getDeleteCount() {
            return this.edits.stream().filter(e -> e instanceof DeleteOneModel).count();
        }

        public long getUpdateCount() {
            return this.edits.stream().filter(e -> e instanceof UpdateOneModel).count();
        }
    }

    private static class WriteBatchResult {
        public final long totalUpdates;
        public final long totalDeletes;
        public final BulkWriteResult writeResult;
        public WriteBatchResult(BulkWriteResult writeResult) {
            this.totalUpdates = writeResult.getInsertedCount() + writeResult.getModifiedCount() + writeResult.getUpserts().size();
            this.totalDeletes = writeResult.getDeletedCount();
            this.writeResult = writeResult;
        }

        public boolean matchedExpectation(WriteBatch batch) {
            return batch.getDeleteCount() == this.totalDeletes && batch.getUpdateCount() == this.totalUpdates;
        }
    }

    private String mongoClientUri;
    private ClientSession clientSession;
    private MongoCollection<BsonDocument> collection = null;
    private WriteBatch batch = null;

    public MongoDB(String dbName, String mongoClientUri) {
        super(dbName);
        this.mongoClientUri = mongoClientUri;
    }

    @Override
    public boolean open() {
        if (isOpen()) {
            return true;
        }

        LOG.info("Initializing MongoDB at {}", mongoClientUri);

        // Get the client and create a session for this instance
        MongoClient mongoClient = MongoConnectionManager.inst().getMongoClientInstance(this.mongoClientUri);
        ClientSessionOptions sessionOptions = ClientSessionOptions.builder()
            .causallyConsistent(true)
            .defaultTransactionOptions(TransactionOptions.builder()
                .readConcern(ReadConcern.DEFAULT)
                .writeConcern(WriteConcern.MAJORITY)
                .readPreference(ReadPreference.nearest())
                .build())
            .build();
        this.clientSession = mongoClient.startSession(sessionOptions);

        // Get the database and our collection. Mongo takes care of creating these if they don't exist
        MongoDatabase mongoDb = mongoClient.getDatabase(MongoConstants.AION_DB_NAME);
        boolean collectionExists = false;
        for (String collectionName : mongoDb.listCollectionNames(this.clientSession)) {
            if (collectionName.equals(this.name)) {
                collectionExists = true;
                break;
            }
        }

        if (!collectionExists) {
            mongoDb.createCollection(this.clientSession, this.name);
        }

        this.collection = mongoDb.getCollection(this.name, BsonDocument.class);
        return isOpen();
    }

    @Override
    public boolean isOpen() {
        return this.collection != null;
    }

    @Override
    public boolean isCreatedOnDisk() {
        return false;
    }

    @Override
    public long approximateSize() {
        check();
        return -1L;
    }

    @Override
    public boolean isEmpty() {
        check();
        long count = this.collection.estimatedDocumentCount();
        return count == 0;
    }

    @Override
    public Set<byte[]> keys() {
        check();

        MongoIterable<byte[]> iter = this.collection.find(this.clientSession)
            .projection(Projections.fields(Projections.include(MongoConstants.ID_FIELD_NAME)))
            .map(f -> f.getBinary(MongoConstants.ID_FIELD_NAME).getData());

        Set<byte[]> keys = new HashSet<>();
        for (byte[] k : iter) {
            keys.add(k);
        }

        return keys;
    }

    private WriteBatchResult doBulkWrite(WriteBatch edits) {
        BulkWriteResult writeResult = this.collection.bulkWrite(this.clientSession, edits.getEdits());
        WriteBatchResult result = new WriteBatchResult(writeResult);

        if (result.totalDeletes != edits.getDeleteCount()) {
            LOG.warn("Expected {} deletes but only deleted {}", edits.getDeleteCount(), result.totalDeletes);
        }

        if (result.totalUpdates != edits.getUpdateCount()) {
            LOG.warn("Expected {} upserts but only got {}", edits.getUpdateCount(), result.totalUpdates);
        }

        return result;
    }

    @Override
    public boolean commitCache(Map<ByteArrayWrapper, byte[]> cache) {
        check();
        check(cache.keySet().stream().map(k -> k.getData()).collect(Collectors.toList()));

        WriteBatch edits = new WriteBatch().addEditsWrapper(cache);
        WriteBatchResult result = doBulkWrite(edits);

        return result.matchedExpectation(edits);
    }

    @Override
    protected byte[] getInternal(byte[] k) {
        BsonDocument document = this.collection.find(this.clientSession, eq(MongoConstants.ID_FIELD_NAME, new BsonBinary(k))).first();
        if (document == null) {
            return null;
        } else {
            return document.getBinary(MongoConstants.VALUE_FIELD_NAME).getData();
        }
    }

    @Override
    public void put(byte[] key, byte[] value) {
        check();
        check(key);


        WriteBatch edits = new WriteBatch().addEdit(key, value);
        doBulkWrite(edits);
    }

    @Override
    public void delete(byte[] key) {
        check();
        check(key);


        WriteBatch edits = new WriteBatch().addEdit(key, null);
        doBulkWrite(edits);
    }

    @Override
    public void putBatch(Map<byte[], byte[]> inputMap) {
        check();
        check(inputMap.keySet());

        WriteBatch edits = new WriteBatch().addEdits(inputMap);
        doBulkWrite(edits);
    }

    @Override
    public void putToBatch(byte[] key, byte[] value) {
        check();
        check(key);

        if (this.batch == null) {
            this.batch = new WriteBatch();
        }

        batch.addEdit(key, value);
    }

    @Override
    public void commitBatch() {
        check();

        if (this.batch != null) {
            doBulkWrite(this.batch);
        }

        this.batch = null;
    }

    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        check();
        check(keys);

        if (!keys.isEmpty()) {
            Map<byte[], byte[]> batch = new HashMap();
            keys.forEach(key -> batch.put(key, null));
            this.putBatch(batch);
        }
    }

    @Override
    public void close() {
        // do nothing if already closed
        if (collection == null) {
            return;
        }

        LOG.info("Closing database " + this.toString());

        MongoConnectionManager.inst().closeMongoClientInstance(this.mongoClientUri);
        this.collection = null;
        this.clientSession = null;
        this.batch = null;
    }


    @Override
    public void drop() {
        check();
        this.collection.drop(this.clientSession);
    }

    @Override
    public PersistenceMethod getPersistence() {
        return PersistenceMethod.REMOTE_SERVER;
    }
}
