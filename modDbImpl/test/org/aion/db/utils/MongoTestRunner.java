package org.aion.db.utils;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ExecCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.PortBinding;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.aion.db.impl.DatabaseTestUtils;


import static org.junit.Assert.fail;

/**
 * Helper class for spinning up a MongoDB instance to be used for unit tests.
 */
public class MongoTestRunner implements AutoCloseable {

    private int port;
    private Process runningMongoServer;
    private File databaseFilesDir;

    private DockerClient dockerClient;
    private String runningDockerContainerId;
    private static final String MONGO_IMAGE = "library/mongo:3.6.9";

    private static class Holder {
        static final MongoTestRunner INSTANCE = new MongoTestRunner();
    }

    public static MongoTestRunner inst() {
        return Holder.INSTANCE;
    }

    private MongoTestRunner() {
        try {
            // Start by getting a connection to the docker service running on the machine
            dockerClient = DefaultDockerClient.fromEnv().build();

            // Pull the docker image, this will be very quick if it already exists on the machine
            dockerClient.pull(MONGO_IMAGE, message -> System.out.println("Docker pull: " + message.status()));

            // Bind container port 27017 to an automatically allocated available host port.
            this.port = DatabaseTestUtils.findOpenPort();
            final Map<String, List<PortBinding>> portBindings =
                Map.of("27017", Arrays.asList(PortBinding.of("0.0.0.0", Integer.toString(this.port))));
            final HostConfig hostConfig = HostConfig.builder().portBindings(portBindings).build();

            // Configure how we want the image to run
            ContainerConfig containerConfig = ContainerConfig.builder()
                .attachStderr(true)
                .hostConfig(hostConfig)
                .exposedPorts("27017")
                .image(MONGO_IMAGE)
                .cmd("--replSet", "rs0", "--noauth", "--nojournal", "--quiet")
                .build();

            // Actually start the container
            ContainerCreation creation = dockerClient.createContainer(containerConfig);
            dockerClient.startContainer(creation.id());
            this.runningDockerContainerId = creation.id();

            // Next we run a command to initialize the mongo server's replicas set and admin accounts
            String[] initializationCommands = {"mongo", "--eval", "rs.initiate()"};
            tryInitializeDb(initializationCommands, 30, 100);

            // Finally, add a shutdown hook to kill the Mongo server when the process dies
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    close();
                } catch (Exception e) {
                    e.printStackTrace();
                    fail("Failed to close MongoDB connection");
                }
            }));

        } catch (Exception e) {
            e.printStackTrace();
            fail("Error encountered when initializing mongo docker image. Make sure docker service is running");
        }
    }



    /**
     * Helper method to run some initialization command on Mongo with some retry logic if the command fails. Since it's
     * not determinate how long starting the database will take, we need this retry logic.
     * @param initializationCommands The command to actually run
     * @param retriesRemaining How many more times to retry the command if it fails
     * @param pauseTimeMillis How long to pause between retries
     * @throws InterruptedException Thrown when the thread gets interrupted trying to sleep.
     */
    private void tryInitializeDb(String[] initializationCommands, int retriesRemaining, long pauseTimeMillis)
        throws InterruptedException {

        Exception exception = null;
        String execOutput = "";
        try {
            final ExecCreation execCreation = dockerClient.execCreate(
                this.runningDockerContainerId, initializationCommands,
                DockerClient.ExecCreateParam.attachStdout(),
                DockerClient.ExecCreateParam.attachStderr(),
                DockerClient.ExecCreateParam.detach(false));
            final LogStream output = dockerClient.execStart(execCreation.id());
            execOutput = output.readFully();
        }  catch (Exception e) {
            exception = e;
        }

        // We can't get the exit code, but look for an expected message in the output to determine success
        if (exception != null || !execOutput.contains("Using a default configuration for the set")) {
            // This is the case that the command didn't work
            if (retriesRemaining == 0) {
                // We're out of retries, we should fail
                if (exception != null) {
                    exception.printStackTrace();
                }

                fail("Failed to initialize MongoDB, no retries remaining. Output was: " + execOutput);
            } else {
                Thread.sleep(pauseTimeMillis);
                tryInitializeDb(initializationCommands, retriesRemaining - 1, pauseTimeMillis);
            }
        }
    }

    /**
     * Returns the connection string to be used to connect to the started Mongo instance
     * @return The connection string.
     */
    public String getConnectionString() {
        return String.format("mongodb://localhost:%d", this.port);
    }

    @Override
    public void close() throws Exception {
        if (this.dockerClient != null && this.runningDockerContainerId != null) {
            System.out.println("Killing mongo docker container");
            this.dockerClient.killContainer(this.runningDockerContainerId);
            this.dockerClient.removeContainer(this.runningDockerContainerId);
            this.dockerClient.close();
            this.dockerClient = null;
            this.runningDockerContainerId = null;
        }
    }
}
