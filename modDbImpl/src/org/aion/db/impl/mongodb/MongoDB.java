package org.aion.db.impl.mongodb;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.ClientSessionOptions;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.aion.base.db.PersistenceMethod;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.db.impl.AbstractDB;
import org.bson.BsonBinary;
import org.bson.BsonDocument;

/**
 * This class allows us to connect to a MongoDB instance to write the kernel's data. To test this
 * out locally, you can use the script located at
 * modDbImpl/test_resources/mongo/start_mongo_local.sh to spin up a mongo database using docker, and
 * then modify your config.xml to point to that local database
 */
public class MongoDB extends AbstractDB {

    /**
     * Simple wrapper class to encloses a collection of writes (inserts, edits, or deletes) to the
     * Mongo database.
     */
    private static class WriteBatch {
        private List<WriteModel<BsonDocument>> edits = new ArrayList<>();

        /**
         * Adds a new edit to the batch
         *
         * @param key the key to write
         * @param value the value to write. Null indicates we should delete this key
         * @return this
         */
        public WriteBatch addEdit(byte[] key, byte[] value) {
            if (value == null) {
                DeleteOneModel deleteModel =
                        new DeleteOneModel<>(eq(MongoConstants.ID_FIELD_NAME, new BsonBinary(key)));

                edits.add(deleteModel);
            } else {
                UpdateOneModel updateModel =
                        new UpdateOneModel<>(
                                eq(MongoConstants.ID_FIELD_NAME, new BsonBinary(key)),
                                Updates.set(MongoConstants.VALUE_FIELD_NAME, new BsonBinary(value)),
                                new UpdateOptions().upsert(true));

                edits.add(updateModel);
            }

            return this;
        }

        /**
         * Adds a new edit to the batch
         *
         * @param key the key to write
         * @param value the value to write. Null indicates we should delete this key
         * @return this
         */
        public WriteBatch addEdit(ByteArrayWrapper key, byte[] value) {
            return addEdit(key.getData(), value);
        }

        /**
         * Adds a collection of edits to the batch
         *
         * @param kvPairs The collection of key value pairs we want to write in
         * @return this
         */
        public WriteBatch addEdits(Map<byte[], byte[]> kvPairs) {
            for (byte[] key : kvPairs.keySet()) {
                addEdit(key, kvPairs.get(key));
            }

            return this;
        }

        /**
         * Adds a collection of edits to the batch
         *
         * @param kvPairs The collection of key value pairs we want to write in
         * @return this
         */
        public WriteBatch addEditsWrapper(Map<ByteArrayWrapper, byte[]> kvPairs) {
            for (ByteArrayWrapper key : kvPairs.keySet()) {
                addEdit(key, kvPairs.get(key));
            }

            return this;
        }

        /**
         * Gets the collection of writes which have been collected here
         *
         * @return The edits which have been added to this instance.
         */
        public List<WriteModel<BsonDocument>> getEdits() {
            return this.edits;
        }

        /**
         * Gets the number of deletes which are in this batch
         *
         * @return Number of deletes
         */
        public long getDeleteCount() {
            return this.edits.stream().filter(e -> e instanceof DeleteOneModel).count();
        }

        /**
         * Gets the number of updates (edits or inserts) in this batch
         *
         * @return Number of updates
         */
        public long getUpdateCount() {
            return this.edits.stream().filter(e -> e instanceof UpdateOneModel).count();
        }
    }

    /** Wrapper class holding the result of writing a batch */
    private static class WriteBatchResult {

        /** Total number of updates which were committed */
        public final long totalUpdates;

        /** Total number of deletes which were committed */
        public final long totalDeletes;

        /**
         * Whether or not our database is read-only, which means we'll never have written anything
         */
        public final boolean isReadOnly;

        /**
         * Creates a new instance of the WriteBatchResult from Mongo's raw BulkWriteResult
         *
         * @param writeResult The BulkWriteResult returned from Mongo
         */
        public WriteBatchResult(BulkWriteResult writeResult) {
            this.totalUpdates =
                    writeResult.getInsertedCount()
                            + writeResult.getModifiedCount()
                            + writeResult.getUpserts().size();
            this.totalDeletes = writeResult.getDeletedCount();
            this.isReadOnly = false;
        }

        /**
         * Overloaded constructor to return a dummy WriteBatchResult if we're ready only.
         *
         * @param isReadOnly Whether or not our database is read only
         */
        public WriteBatchResult(boolean isReadOnly) {
            this.totalUpdates = 0;
            this.totalDeletes = 0;
            this.isReadOnly = isReadOnly;
        }

        /**
         * Returns whether or not the expeced number of updates and deletes where committed in this
         * batch
         *
         * @param batch The batch which specified these results
         * @return Whether or not things were written as expected
         */
        public boolean matchedExpectation(WriteBatch batch) {
            return (batch.getDeleteCount() == this.totalDeletes
                            && batch.getUpdateCount() == this.totalUpdates)
                    || isReadOnly;
        }
    }

    private String mongoClientUri;
    private ClientSession clientSession;
    private MongoCollection<BsonDocument> collection = null;
    private WriteBatch batch = null;
    private boolean isReadOnly;

    public MongoDB(String dbName, String mongoClientUri) {
        super(dbName);
        this.mongoClientUri = mongoClientUri;

        this.isReadOnly = mongoClientUri.contains("reader");
    }

    /**
     * Private helper method for writing a collection of edits into the database
     *
     * @param edits The edits to write
     * @return A summary of the write results
     */
    private WriteBatchResult doBulkWrite(WriteBatch edits) {
        if (this.isReadOnly) {
            LOG.info("Skipping writing because database is read only");
            return new WriteBatchResult(true);
        }

        BulkWriteResult writeResult =
                this.collection.bulkWrite(this.clientSession, edits.getEdits());
        WriteBatchResult result = new WriteBatchResult(writeResult);

        if (result.totalDeletes != edits.getDeleteCount()) {
            LOG.debug(
                    "Expected {} deletes but only deleted {}",
                    edits.getDeleteCount(),
                    result.totalDeletes);
        }

        if (result.totalUpdates != edits.getUpdateCount()) {
            LOG.debug(
                    "Expected {} upserts but only got {}",
                    edits.getUpdateCount(),
                    result.totalUpdates);
        }

        LOG.debug("Successfully wrote {} edits", edits.getEdits().size());

        return result;
    }

    @Override
    public boolean open() {
        if (isOpen()) {
            return true;
        }

        LOG.info("Initializing MongoDB at {}", mongoClientUri);

        // Get the client and create a session for this instance
        MongoClient mongoClient =
                MongoConnectionManager.inst().getMongoClientInstance(this.mongoClientUri);
        ClientSessionOptions sessionOptions =
                ClientSessionOptions.builder()
                        .causallyConsistent(true)
                        .defaultTransactionOptions(
                                TransactionOptions.builder()
                                        .readConcern(ReadConcern.DEFAULT)
                                        .writeConcern(WriteConcern.MAJORITY)
                                        .readPreference(ReadPreference.nearest())
                                        .build())
                        .build();
        this.clientSession = mongoClient.startSession(sessionOptions);

        // Get the database and our collection. Mongo takes care of creating these if they don't
        // exist
        MongoDatabase mongoDb = mongoClient.getDatabase(MongoConstants.AION_DB_NAME);

        // Gets the collection where we will be saving our values. Mongo creates it if it doesn't
        // yet exist
        this.collection = mongoDb.getCollection(this.name, BsonDocument.class);

        LOG.info("Finished opening the Mongo connection");
        return isOpen();
    }

    @Override
    public boolean isOpen() {
        return this.collection != null;
    }

    @Override
    public boolean isCreatedOnDisk() {
        // Always return false here since we don't persist to the local disk.
        return false;
    }

    @Override
    public long approximateSize() {
        check();

        // Just return -1 because we don't have a good way of asking the Mongo Server our size in
        // bytes
        return -1L;
    }

    @Override
    public boolean isEmpty() {
        check();
        long count = this.collection.estimatedDocumentCount();
        LOG.info("Estimated document count: {}", count);
        return count == 0;
    }

    @Override
    public Iterator<byte[]> keys() {
        check();

        LOG.debug("Getting the collection of keys");

        Set<byte[]> keys =
                this.collection
                        .find(this.clientSession)
                        .projection(
                                Projections.fields(
                                        Projections.include(MongoConstants.ID_FIELD_NAME)))
                        .map(f -> f.getBinary(MongoConstants.ID_FIELD_NAME).getData())
                        .into(new HashSet<>());

        LOG.debug("The database contains {} keys", keys.size());

        return keys.iterator();
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
        BsonDocument document =
                this.collection
                        .find(
                                this.clientSession,
                                eq(MongoConstants.ID_FIELD_NAME, new BsonBinary(k)))
                        .first();
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

        // Write this single edit in as a batch
        WriteBatch edits = new WriteBatch().addEdit(key, value);
        doBulkWrite(edits);
    }

    @Override
    public void delete(byte[] key) {
        check();
        check(key);

        // Write this single edit in as a batch
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
            LOG.debug("Committing batch of writes");
            doBulkWrite(this.batch);
        } else {
            LOG.debug("Attempting to commit empty batch, skipping");
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

        if (this.isReadOnly) {
            LOG.info("read-only database. Not dropping.");
            return;
        }

        LOG.info("Dropping collection {}", this.name);
        this.collection.drop(this.clientSession);
    }

    @Override
    public PersistenceMethod getPersistenceMethod() {
        return PersistenceMethod.DBMS;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ":" + propertiesInfo();
    }
}
