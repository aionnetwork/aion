package org.aion.zero.impl.trie;

import static org.aion.crypto.HashUtil.h256;

import org.aion.db.impl.ByteArrayKeyValueStore;
import org.aion.util.bytes.ByteUtil;

public class SecureTrie extends TrieImpl implements Trie {

    public SecureTrie(ByteArrayKeyValueStore db) {
        this(db, ByteUtil.EMPTY_BYTE_ARRAY);
    }

    public SecureTrie(ByteArrayKeyValueStore db, Object root) {
        super(db, root);
    }

    public SecureTrie(final Cache cache, Object root) {
        super(cache, root);
    }

    @Override
    public byte[] get(byte[] key) {
        return super.get(h256(key));
    }

    @Override
    public void update(byte[] key, byte[] value) {
        super.update(h256(key), value);
    }

    @Override
    public void delete(byte[] key) {
        super.delete(h256(key));
    }
}
