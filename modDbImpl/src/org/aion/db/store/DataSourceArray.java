package org.aion.db.store;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.Optional;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;

/**
 * DataSource Array.
 *
 * @param <V>
 */
class DataSourceArray<V> implements ArrayStore<V> {

    private final ObjectStore<V> src;
    private final ByteArrayKeyValueDatabase db;
    @VisibleForTesting
    static final byte[] sizeKey = Hex.decode("FFFFFFFFFFFFFFFF");
    private long size = -1L;

    DataSourceArray(ByteArrayKeyValueDatabase database, Serializer<V> serializer) {
        this.db = database;
        this.src = new DataSource<>(db, serializer).buildObjectSource();
    }

    @Override
    public void set(long index, V value) {
        if (index <= Integer.MAX_VALUE) {
            src.putToBatch(ByteUtil.intToBytes((int) index), value);
        } else {
            src.putToBatch(ByteUtil.longToBytes(index), value);
        }
        // TODO AKI-309: flush in bulk by the repository
        src.flushBatch();
        if (index >= size()) {
            setSize(index + 1);
        }
    }

    @Override
    public void remove(long index) {
        // without this check it will remove the sizeKey
        if (index < 0 || index >= size()) {
            return;
        }

        if (index <= Integer.MAX_VALUE) {
            src.deleteInBatch(ByteUtil.intToBytes((int) index));
        } else {
            src.deleteInBatch(ByteUtil.longToBytes(index));
        }
        // TODO AKI-309: flush in bulk by the repository
        src.flushBatch();
        if (index < size()) {
            setSize(index);
        }
    }

    @Override
    public V get(long index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException(
                    "Incorrect index value <"
                            + index
                            + ">. Allowed values are >= 0 and < "
                            + size
                            + ".");
        }

        V value;

        if (index <= Integer.MAX_VALUE) {
            value = src.get(ByteUtil.intToBytes((int) index));
        } else {
            value = src.get(ByteUtil.longToBytes(index));
        }
        return value;
    }

    public long getStoredSize() {
        long size;

        // Read the value from the database directly and
        // convert to the size, and if it doesn't exist, 0.
        Optional<byte[]> optBytes = db.get(sizeKey);
        if (!optBytes.isPresent()) {
            size = 0L;
        } else {
            byte[] bytes = optBytes.get();

            if (bytes.length == 4) {
                size = ByteUtil.byteArrayToInt(bytes);
            } else {
                size = ByteUtil.byteArrayToLong(bytes);
            }
        }

        return size;
    }

    @Override
    public long size() {

        if (size < 0) {
            size = getStoredSize();
        }

        return size;
    }

    private synchronized void setSize(long newSize) {
        size = newSize;
        if (size <= Integer.MAX_VALUE) {
            db.putToBatch(sizeKey, ByteUtil.intToBytes((int) newSize));
        } else {
            db.putToBatch(sizeKey, ByteUtil.longToBytes(newSize));
        }
        // TODO AKI-309: flush in bulk by the repository
        db.commitBatch();
    }

    @Override
    public boolean isOpen() {
        return src.isOpen();
    }

    @Override
    public void close() throws IOException {
        // ensures that the size is written to disk if it was previously missing
        if (!db.get(sizeKey).isPresent()) {
            setSize(size);
        }
        src.close();
    }
}
