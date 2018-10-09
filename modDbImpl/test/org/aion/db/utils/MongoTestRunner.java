package org.aion.db.utils;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;
import org.aion.db.impl.DatabaseTestUtils;

public class MongoTestRunner implements AutoCloseable {

    private int port;
    private Process runningMongoServer;
    private File databaseFilesDir;

    public MongoTestRunner(File dbPath) {
        this.port = DatabaseTestUtils.findOpenPort();

        this.databaseFilesDir = FileUtils.createTempDir("mongodb");
        this.databaseFilesDir.mkdirs();

//
//
//        File journalFile = new File(databaseFilesDir, "journal");
//        journalFile.mkdirs();
//        if (!databaseFilesDir.exists()) {
//            databaseFilesDir.mkdirs();
//        }

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("mongo/bin/mongod").getFile());
        String mongodPath = file.getAbsolutePath();

        List<String> commands = List.of(
            mongodPath,
            "--port",
            Integer.toString(this.port),
            "--dbpath",
            databaseFilesDir.getAbsolutePath(),
            "--replSet",
            String.format("rs%d", System.currentTimeMillis()),
            "--noauth" ,
            "--nojournal"
        );

        String mongoCommand = String.format("%s --port %d --dbpath %s --noauth", mongodPath, this.port, databaseFilesDir.getAbsolutePath());
        try {
            this.runningMongoServer = new ProcessBuilder(commands)
                .redirectError(Redirect.INHERIT)
                .redirectOutput(Redirect.INHERIT)
                .start();

            Thread.sleep(3000);

            List<String> initializationCommands = List.of(
                new File(classLoader.getResource("mongo/bin/mongo").getFile()).getAbsolutePath(),
                "--host",
                "localhost",
                "--port",
                Integer.toString(this.port),
                new File(classLoader.getResource("mongo/initScript.js").getFile()).getAbsolutePath()
            );

            new ProcessBuilder(initializationCommands)
                 .redirectError(Redirect.INHERIT)
                 .redirectOutput(Redirect.INHERIT)
                .start().waitFor();
        } catch (IOException e) {
            e.printStackTrace();
            fail("Exception thrown while starting Mongo");
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail("Exception thrown while starting Mongo");
        }
    }

    public String getConnectionString() {
        return String.format("mongodb://localhost:%d", this.port);
    }

    @Override
    public void close() throws Exception {
        this.runningMongoServer.destroyForcibly();
        FileUtils.deleteRecursively(this.databaseFilesDir);
    }
}
