package org.aion.zero.impl.db;

import static org.aion.zero.impl.db.DatabaseUtils.deleteRecursively;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DatabaseUtilsTest {

    File testDB;
    File linkedDB;
    String dbName = "database";
    String levelDB = "leveldb";
    String rocksDB = "rocksdb";

    String testDBPath = System.getProperty("user.dir") + "/tmp/testPath";
    String linkDBPath = System.getProperty("user.dir") + "/tmp/linkedPath";

    @Before
    public void setup() {
        testDB = new File(testDBPath, "");
        testDB.mkdirs();
        linkedDB = new File(linkDBPath, dbName);
        linkedDB.mkdirs();
    }

    @After
    public void teardown() {
        deleteRecursively(testDB);
        deleteRecursively(linkedDB);
    }

    @Test
    public void testVerifyRocksDBfileTypeWithLinkDBPath() throws IOException {

        File options = new File(linkedDB, "OPTIONS-123");
        options.createNewFile();

        File symbolicLink = new File(testDB, dbName);

        Files.createSymbolicLink(symbolicLink.toPath(), linkedDB.toPath());
        DatabaseUtils.verifyDBfileType(testDB, rocksDB);
    }

    @Test (expected = IllegalStateException.class)
    public void testVerifyRocksDBfileTypeWithInvalidOptionFile() throws IOException {

        File options = new File(linkedDB, "OPTION-123");
        options.createNewFile();

        File symbolicLink = new File(testDB, dbName);

        Files.createSymbolicLink(symbolicLink.toPath(), linkedDB.toPath());
        DatabaseUtils.verifyDBfileType(testDB, rocksDB);
    }

    @Test (expected = IllegalStateException.class)
    public void testVerifyRocksDBfileTypeWithInvalidLDBFile() throws IOException {

        File options = new File(linkedDB, "123.ldb");
        options.createNewFile();

        File symbolicLink = new File(testDB, dbName);

        Files.createSymbolicLink(symbolicLink.toPath(), linkedDB.toPath());
        DatabaseUtils.verifyDBfileType(testDB, rocksDB);
    }

    @Test
    public void testVerifyLevelDBfileTypeWithLinkDBPath() throws IOException {

        File options = new File(linkedDB, "123.ldb");
        options.createNewFile();

        File symbolicLink = new File(testDB, dbName);

        Files.createSymbolicLink(symbolicLink.toPath(), linkedDB.toPath());
        DatabaseUtils.verifyDBfileType(testDB, levelDB);
    }

    @Test (expected = IllegalStateException.class)
    public void testVerifyLevelDBfileTypeWithInvalidOptionFile() throws IOException {

        File options = new File(linkedDB, "OPTIONS-123");
        options.createNewFile();

        File symbolicLink = new File(testDB, dbName);

        Files.createSymbolicLink(symbolicLink.toPath(), linkedDB.toPath());
        DatabaseUtils.verifyDBfileType(testDB, levelDB);
    }

    @Test (expected = IllegalStateException.class)
    public void testVerifyLevelDBfileTypeWithInvalidSSTFile() throws IOException {

        File options = new File(linkedDB, "123.sst");
        options.createNewFile();

        File symbolicLink = new File(testDB, dbName);

        Files.createSymbolicLink(symbolicLink.toPath(), linkedDB.toPath());
        DatabaseUtils.verifyDBfileType(testDB, levelDB);
    }
}
