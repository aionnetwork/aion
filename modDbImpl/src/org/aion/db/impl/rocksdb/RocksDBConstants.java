package org.aion.db.impl.rocksdb;

public class RocksDBConstants {
    public static int MAX_OPEN_FILES = 1024;
    public static int BLOCK_SIZE = 4096;
    public static int WRITE_BUFFER_SIZE = 64 * 1024 * 1024;
    public static int READ_BUFFER_SIZE = 64 * 1024 * 1024;
    public static int CACHE_SIZE = 128 * 1024 * 1024;

    private RocksDBConstants() {}
}
