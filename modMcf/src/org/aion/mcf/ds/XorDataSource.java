package org.aion.mcf.ds;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.aion.base.db.IByteArrayKeyValueStore;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;

public class XorDataSource implements IByteArrayKeyValueStore {
    IByteArrayKeyValueStore source;
    byte[] subKey;

    public XorDataSource(IByteArrayKeyValueStore source, byte[] subKey) {
        this.source = source;
        this.subKey = subKey;
    }

    private byte[] convertKey(byte[] key) {
        return ByteUtil.xorAlignRight(key, subKey);
    }

    @Override
    public Optional<byte[]> get(byte[] key) {
        return source.get(convertKey(key));
    }

    @Override
    public void put(byte[] key, byte[] value) {
        source.put(convertKey(key), value);
    }

    @Override
    public void delete(byte[] key) {
        source.delete(convertKey(key));
    }

    @Override
    public Set<byte[]> keys() {
        Set<byte[]> keys = source.keys();
        Set<byte[]> ret = new HashSet<>(keys.size());
        for (byte[] key : keys) {
            ret.add(convertKey(key));
        }
        return ret;
    }

    @Override
    public void putBatch(Map<byte[], byte[]> rows) {
        Map<byte[], byte[]> converted = new HashMap<>(rows.size());
        for (Map.Entry<byte[], byte[]> entry : rows.entrySet()) {
            converted.put(convertKey(entry.getKey()), entry.getValue());
        }
        source.putBatch(converted);
    }

    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows, boolean erasure) {
        // not supported
        throw new UnsupportedOperationException(
                "ByteArrayWrapper map not supported in XorDataSource.updateBatch yet");
    }

    @Override
    public void close() throws Exception {
        source.close();
    }

    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        // TODO Auto-generated method stub

    }

    @Override
    public void check() {
        source.check();
    }

    @Override
    public boolean isEmpty() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void putToBatch(byte[] key, byte[] value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void commitBatch() {
        throw new UnsupportedOperationException();
    }
}
