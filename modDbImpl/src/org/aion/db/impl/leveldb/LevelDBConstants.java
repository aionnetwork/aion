package org.aion.db.impl.leveldb;

/** Constants for LevelDB implementation, used as fallback when nothing else matches */
public class LevelDBConstants {



    /**
     * The maximum allocated file descriptor that will be allocated per database, therefore the
     * total amount of file descriptors that are required is {@code NUM_DB * max_fd_open_alloc}
     */
    public static int MAX_OPEN_FILES = 1024;

    /**
     * The maximum block size
     */
    public static int BLOCK_SIZE = 16 * 1024 * 1024;;

    /**
     * The size of the write buffer that will be applied per database, for more information, see <a
     * href="https://github.com/google/leveldb/blob/master/include/leveldb/options.h">here</a> From
     * LevelDB docs:
     *
     * <p>Amount of data to build up in memory (backed by an unsorted log on disk) before converting
     * to a sorted on-disk file.
     *
     * <p>Larger values increase performance, especially during bulk loads. Up to two write buffers
     * may be held in memory at the same time, so you may wish to adjust this parameter to control
     * memory usage. Also, a larger write buffer will result in a longer recovery time the next time
     * the database is opened.
     */
    public static int WRITE_BUFFER_SIZE = 64 * 1024 * 1024;

    /**
     * Specify the size of the cache used by LevelDB
     */
    public static int CACHE_SIZE = 128 * 1024 * 1024;

    private LevelDBConstants() {}

}
