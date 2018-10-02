package org.aion.db.impl.mongodb;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.print.Doc;
import org.aion.base.db.PersistenceMethod;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.db.impl.AbstractDB;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Binary;

public class MongoDB extends AbstractDB {

    private String mongoClientUri;
    private MongoCollection<BsonDocument> collection = null;

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

        // Get the database and our collection. Mongo takes care of creating these if they don't exist
        MongoDatabase mongoDb = MongoConnectionManager.inst()
            .getMongoDbInstance(this.mongoClientUri);
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

        MongoIterable<byte[]> iter = this.collection.find()
            .projection(Projections.fields(Projections.include(MongoConstants.ID_FIELD_NAME)))
            .map(f -> f.getBinary(MongoConstants.ID_FIELD_NAME).getData());

        Set<byte[]> keys = new HashSet<>();
        for (byte[] k : iter) {
            keys.add(k);
        }

        return keys;
    }

    @Override
    public boolean commitCache(Map<ByteArrayWrapper, byte[]> cache) {
        return false;
    }

    @Override
    protected byte[] getInternal(byte[] k) {
        // Document searchDocument = new Document();
        // searchDocument.put(MongoConstants.ID_FIELD_NAME, new BsonBinary(k));

        BsonDocument document = this.collection.find(eq(MongoConstants.ID_FIELD_NAME, new BsonBinary(k))).first();
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


        Map<byte[], byte[]> batch = new HashMap();
        batch.put(key, value);
        this.putBatch(batch);
    }

    @Override
    public void delete(byte[] key) {
        check();
        check(key);

        DeleteResult result = this.collection.deleteOne(new MongoDocument(key));
        if (result.getDeletedCount() < 1) {
            LOG.warn("Nothing was actually deleted for key {}", key);
        }
    }

    @Override
    public void putBatch(Map<byte[], byte[]> inputMap) {
        check();
        check(inputMap.keySet());

        List<byte[]> keysToDelete = new ArrayList<>();
        List<WriteModel<BsonDocument>> inserts = new ArrayList<>();
        for (byte[] key : inputMap.keySet()) {
            byte[] value = inputMap.get(key);
            if (value == null) {
                keysToDelete.add(key);
            } else {
                UpdateOneModel updateModel = new UpdateOneModel<>(
                    eq(MongoConstants.ID_FIELD_NAME, new BsonBinary(key)),
                    Updates.set(MongoConstants.VALUE_FIELD_NAME, new BsonBinary(value)),
                    new UpdateOptions().upsert(true));

                inserts.add(updateModel);
            }
        }

        deleteBatch(keysToDelete);

        if (!inserts.isEmpty()) {
            this.collection.bulkWrite(inserts);
        }
    }

    @Override
    public void putToBatch(byte[] key, byte[] value) {
        check();
        check(key);

    }

    @Override
    public void commitBatch() {

    }

    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        check();
        check(keys);

        if (!keys.isEmpty()) {
            List<BsonBinary> deletes = keys.stream().map(k -> new BsonBinary(k)).collect(Collectors.toList());
            DeleteResult result = this.collection.deleteMany(Filters.in(MongoConstants.ID_FIELD_NAME, deletes));
            LOG.info("Expected delete count = {} actual = {}", keys.size(), result.getDeletedCount());
        }
    }

    @Override
    public void close() {
        // do nothing if already closed
        if (collection == null) {
            return;
        }

        LOG.info("Closing database " + this.toString());

        MongoConnectionManager.inst().closeMongoDbInstance(this.mongoClientUri);
        this.collection = null;
    }


    @Override
    public void drop() {
        check();
        this.collection.drop();
    }

    @Override
    public PersistenceMethod getPersistence() {
        return PersistenceMethod.REMOTE_SERVER;
    }
}
