package org.aion.zero.impl.trie;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.util.types.ByteArrayWrapper;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrieBenchmarkTest {

    private Trie trie;
    private final Logger log = LoggerFactory.getLogger("DB");
    private Map<ByteArrayWrapper, byte[]> keyValue;
    private long testSets = 5_000L;

    @Before
    public void setup() {
        MockDB mockDb = new MockDB("TrieTest", log);
        trie = new TrieImpl(mockDb);
        keyValue = new HashMap<>();

        for (int i=0 ; i< testSets; i++) {
            String key = "key" + i;
            String value = "value" + i;
            keyValue.put(ByteArrayWrapper.wrap(key.getBytes()), value.getBytes());
        }
    }

    @Test
    public void trieInsertDeleteTest() {

        long t1 = System.nanoTime();
        for(Entry<ByteArrayWrapper, byte[]> e: keyValue.entrySet()) {
            trie.update(e.getKey().toBytes(), e.getValue());
        }
        log.info("trie insert {} elements cost {} ms", testSets, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t1));

        t1 = System.nanoTime();
        for(Entry<ByteArrayWrapper, byte[]> e: keyValue.entrySet()) {
            trie.delete(e.getKey().toBytes());
        }
        log.info("trie delete {} elements cost {} ms", testSets, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t1));
    }
}
