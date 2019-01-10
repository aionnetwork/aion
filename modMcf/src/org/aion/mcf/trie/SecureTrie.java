package org.aion.mcf.trie;

import static org.aion.base.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.aion.crypto.HashUtil.h256;

import org.aion.base.db.IByteArrayKeyValueStore;

public class SecureTrie extends TrieImpl implements Trie {

    public SecureTrie(IByteArrayKeyValueStore db) {
        this(db, "");
    }

    public SecureTrie(IByteArrayKeyValueStore db, Object root) {
        super(db, root);
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
        this.update(key, EMPTY_BYTE_ARRAY);
    }
}
