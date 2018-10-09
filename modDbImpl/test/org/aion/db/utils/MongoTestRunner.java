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


    private static class Holder {
        static final MongoTestRunner INSTANCE = new MongoTestRunner();
    }

    public static MongoTestRunner inst() {
        return Holder.INSTANCE;
    }


    private MongoTestRunner() {
        this.port = DatabaseTestUtils.findOpenPort();

        // Create a temp directory to store our db files in
        this.databaseFilesDir = FileUtils.createTempDir("mongodb");
        this.databaseFilesDir.mkdirs();

        // Find the path to the actual mongo db executable
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
            "--noauth",
            "--nojournal"
        );

        try {
            this.runningMongoServer = new ProcessBuilder(commands)
                .redirectError(Redirect.INHERIT)
                .start();

            List<String> initializationCommands = List.of(
                new File(classLoader.getResource("mongo/bin/mongo").getFile()).getAbsolutePath(),
                "--host",
                "localhost",
                "--port",
                Integer.toString(this.port),
                new File(classLoader.getResource("mongo/initScript.js").getFile()).getAbsolutePath()
            );

            tryInitializeDb(initializationCommands, 30, 100);
        } catch (IOException e) {
            e.printStackTrace();
            fail("Exception thrown while starting Mongo");
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail("Exception thrown while starting Mongo");
        }
    }

    /**
     * Helper method to run some initialization command on Mongo with some retry logic if the command fails.
     * @param initializationCommands The command to actually run
     * @param retriesRemaining How many more times to retry the command if it fails
     * @param pauseTimeMillis How long to pause between retries
     * @throws InterruptedException Thrown when the thread gets interrupted trying to sleep.
     */
    private void tryInitializeDb(List<String> initializationCommands, int retriesRemaining, long pauseTimeMillis)
        throws InterruptedException {

        int exitCode = -1;
        Exception exception = null;
        try {
            exitCode = new ProcessBuilder(initializationCommands)
                .redirectError(Redirect.INHERIT)
                .start()
                .waitFor();
        } catch (Exception e) {
            exception = e;
        }

        if (exception != null || exitCode != 0) {
            // This is the case that the command didn't work
            if (retriesRemaining == 0) {
                // We're out of retries, we should fail
                if (exception != null) {
                    exception.printStackTrace();
                }

                fail("Failed to initialize MongoDB, no retries remaining. Exit code was: " + Integer.toString(exitCode));
            } else {
                Thread.sleep(pauseTimeMillis);
                tryInitializeDb(initializationCommands, retriesRemaining - 1, pauseTimeMillis);
            }
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
