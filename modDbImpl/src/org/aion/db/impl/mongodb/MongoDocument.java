package org.aion.db.impl.mongodb;

import org.bson.BsonDocument;
import org.bson.BsonBinary;

public class MongoDocument extends BsonDocument {
    public MongoDocument(byte[] key) {
        put(MongoConstants.ID_FIELD_NAME, new BsonBinary(key));
    }

    public MongoDocument(byte[] key, byte[] value) {
        put(MongoConstants.ID_FIELD_NAME, new BsonBinary(key));
        put(MongoConstants.VALUE_FIELD_NAME, new BsonBinary(value));
    }

    public byte[] getKey() {
        return this.getBinary(MongoConstants.ID_FIELD_NAME).getData();
    }

    public byte[] getValue() {
        return this.getBinary(MongoConstants.VALUE_FIELD_NAME).getData();
    }
}
