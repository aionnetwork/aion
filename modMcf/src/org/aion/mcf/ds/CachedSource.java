package org.aion.mcf.ds;

import java.util.Collection;

public interface CachedSource<Key, Value> extends Source<Key, Value> {

    Source<Key, Value> getSource();

    Collection<Key> getModified();

    long estimateCacheSize();

    interface BytesKey<Value> extends CachedSource<byte[], Value> {}
}
