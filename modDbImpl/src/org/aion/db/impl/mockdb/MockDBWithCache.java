package org.aion.db.impl.mockdb;

import org.aion.db.impl.AbstractDatabaseWithCache;

public class MockDBWithCache extends AbstractDatabaseWithCache {

    public MockDBWithCache(String name, boolean enableAutoCommit, String max_cache_size, boolean enableStats) {
        // when to commit is directed by this implementation
        super(enableAutoCommit, max_cache_size, enableStats);
        database = new MockDB(name);
    }
}






