package org.aion.zero.impl.trie.scan;

import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.rlp.Value;

/** @author Alexandra Roatis */
public class ExtractToDatabase implements ScanAction {

    // only the keys are relevant so the value will be this constant
    byte[] dummy_value = new byte[] {0};
    ByteArrayKeyValueDatabase db;
    public long count = 0;

    public ExtractToDatabase(ByteArrayKeyValueDatabase _db) {
        this.db = _db;
    }

    @Override
    public void doOnNode(byte[] hash, Value node) {
        db.putToBatch(hash, dummy_value);
        count++;
    }
}
