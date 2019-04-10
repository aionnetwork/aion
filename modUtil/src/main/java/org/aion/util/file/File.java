package org.aion.util.file;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class File {
    public static List<java.io.File> getFiles(final Path path) {
        if (path == null) {
            System.out.println("getFiles null path input!");
            return Collections.emptyList();
        }

        try {
            java.io.File[] files = path.toFile().listFiles();
            return files != null ? Arrays.asList(files) : Collections.emptyList();
        } catch (UnsupportedOperationException | NullPointerException e) {
            System.out.println("getFiles exception: " + e.toString());
            return Collections.emptyList();
        }
    }
}
