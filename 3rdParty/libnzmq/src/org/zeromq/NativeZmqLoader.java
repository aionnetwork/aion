package org.zeromq;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads (i.e. {@link System#load(String)} the native libraries that {@link ZMQ} binds to.
 * The appropriate library is loaded based on the OS name.
 */
public class NativeZmqLoader {
    public static final String NO_EMBEDDED_LIB_FLAG = "ZMQ_NO_EMBEDDED";
    private static boolean LOADED_EMBEDDED_LIBRARY = false;

    /** @return whether this class has already loaded the embedded library */
    public static boolean isLoaded() {
        return LOADED_EMBEDDED_LIBRARY;
    }

    /**
     * Load native libs for ZMQ, unless:
     *   (1) this class has already loaded it once successfully (i.e. {@link #isLoaded()} is true), or
     *   (2) {@link #NO_EMBEDDED_LIB_FLAG} is set
     *
     * The following OSes are supported: Linux, Mac OS X, Windows.
     *
     * @implNote this method will write the native libs into a temporary location on disk
     * @throws IOException if failed to read/write native libs to/from temporary location
     */
    public void load() {
        if(!LOADED_EMBEDDED_LIBRARY && System.getProperty(NO_EMBEDDED_LIB_FLAG) == null) {
            try {
                final Path libDir = Files.createTempDirectory("zmq_native");
                libDir.toFile().deleteOnExit();
                load(System.getProperty("os.name").toLowerCase(), libDir);
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to persist and load native library for ZMQ", ioe);
            }
        } 
    }

    private void load(String osName, Path libDir) {
        if (osName.contains("win")) {
            LOADED_EMBEDDED_LIBRARY = loadNativeEmbedded("/native/win/zmq/libzmq.dll", libDir)
                && loadNativeEmbedded("/native/win/zmq/jzmq.dll", libDir);
        } else if (osName.contains("linux")) {
            LOADED_EMBEDDED_LIBRARY = loadNativeEmbedded("/native/linux/zmq/libzmq.so.5", libDir)
                && loadNativeEmbedded("/native/linux/zmq/libjzmq.so", libDir);
        } else if (osName.contains("mac")) {
            LOADED_EMBEDDED_LIBRARY = loadNativeEmbedded("/native/darwin/zmq/libzmq.5.dylib", libDir)
                && loadNativeEmbedded("/native/darwin/zmq/libjzmq.0.dylib", libDir);
        } else {
            throw new RuntimeException("Unrecognized OS: " + osName);
        }
    }

    private boolean loadNativeEmbedded(String resourceName, Path libDir) {
        try (InputStream is = App.class.getResourceAsStream(resourceName)) {
            if(is == null) {
                // should hook this up to a log4j that can be configured by the top-level program
                return false;
            } else {
                System.load(streamToTempFile(is, tempNameForResourceName(resourceName), libDir));
            }
            return true;
        } catch (IOException ioe) {
            // should hook this up to a log4j that can be configured by the top-level program
            return false;
        }
    }

    /**
     * Return the last part of the resourceName, where "part" is each part of the
     * resource name separated by either the / character or . character.
     *
     * @param resourceName resource name
     * @return last part of the resource name
     */
    private String tempNameForResourceName(String resourceName) {
        resourceName.replace(".", "/");
        String[] resourceParts = resourceName.split("/");
        return resourceParts[resourceParts.length - 1];
    }

    /**
     * Save input stream into a temp file
     *
     * @param in input stream
     * @param filename temp output filename
     * @param libDir location to save temp file to
     * @return path to the file
     * @throws IOException if IO error
     */
    private String streamToTempFile(InputStream in, String filename, Path libDir) throws IOException {
        String[] fnParts = filename.split("\\.");
        String suffix = "";
        if(fnParts.length > 1) {
            suffix = "." + fnParts[fnParts.length - 1];
        }

        File libFile = new File(libDir.toAbsolutePath() + File.separator + filename);
        try (
            final FileOutputStream fos = new FileOutputStream(libFile);
            final OutputStream out = new BufferedOutputStream(fos);
        ) {
            int len = 0;
            byte[] buffer = new byte[8192];
            while ((len = in.read(buffer)) > -1)
                out.write(buffer, 0, len);
        }

        libFile.deleteOnExit();
        return libFile.getAbsolutePath();
    }
}
