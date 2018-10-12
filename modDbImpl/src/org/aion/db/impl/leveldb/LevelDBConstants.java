package org.aion.db.impl.leveldb;

/**
 * Constants for LevelDB implementation, used as fallback when nothing else matches
 */
public class LevelDBConstants {

    public static int MAX_OPEN_FILES = 1024;
    public static int BLOCK_SIZE = 4096;
    public static int WRITE_BUFFER_SIZE = 16 * 1024 * 1024;
    public static int CACHE_SIZE = 128 * 1024 * 1024;

    private LevelDBConstants() {
    }
}
