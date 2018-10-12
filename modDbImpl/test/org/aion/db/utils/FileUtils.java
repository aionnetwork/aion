/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 *     H2 Group.
 ******************************************************************************/
package org.aion.db.utils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class FileUtils {

    private static final int TEMP_DIR_ATTEMPTS = 10000;

    private FileUtils() {
    }

    public static boolean isSymbolicLink(File file) {
        try {
            File canonicalFile = file.getCanonicalFile();
            File absoluteFile = file.getAbsoluteFile();
            File parentFile = file.getParentFile();
            // a symbolic link has a different name between the canonical and absolute path
            return !canonicalFile.getName().equals(absoluteFile.getName()) ||
                // or the canonical parent path is not the same as the file's parent path,
                // provided the file has a parent path
                parentFile != null && !parentFile.getCanonicalPath()
                    .equals(canonicalFile.getParent());
        } catch (IOException e) {
            // error on the side of caution
            return true;
        }
    }

    public static ImmutableList<File> listFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(files);
    }

    public static ImmutableList<File> listFiles(File dir, FilenameFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(files);
    }

    public static File createTempDir(String prefix) {
        return createTempDir(new File(System.getProperty("java.io.tmpdir")), prefix);
    }

    public static File createTempDir(File parentDir, String prefix) {
        String baseName = "";
        if (prefix != null) {
            baseName += prefix + "-";
        }

        baseName += System.currentTimeMillis() + "-";
        for (int counter = 0; counter < TEMP_DIR_ATTEMPTS; counter++) {
            File tempDir = new File(parentDir, baseName + counter);
            if (tempDir.mkdir()) {
                return tempDir;
            }
        }
        throw new IllegalStateException("Failed to create directory within "
            + TEMP_DIR_ATTEMPTS + " attempts (tried "
            + baseName + "0 to " + baseName + (TEMP_DIR_ATTEMPTS - 1) + ')');
    }

    public static boolean deleteDirectoryContents(File directory) {
        Preconditions.checkArgument(directory.isDirectory(), "Not a directory: %s", directory);

        // Don't delete symbolic link directories
        if (isSymbolicLink(directory)) {
            return false;
        }

        boolean success = true;
        for (File file : listFiles(directory)) {
            success = deleteRecursively(file) && success;
        }
        return success;
    }

    public static boolean deleteRecursively(File file) {
        Path path = file.toPath();
        try {
            java.nio.file.Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                    throws IOException {
                    java.nio.file.Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(final Path file, final IOException e) {
                    return handleException(e);
                }

                private FileVisitResult handleException(final IOException e) {
                    // e.printStackTrace();
                    return FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException e)
                    throws IOException {
                    if (e != null) {
                        return handleException(e);
                    }
                    java.nio.file.Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
    
    /*
    public static boolean deleteRecursively(File file)
    {
        boolean success = true;
        if (file.isDirectory()) {
            success = deleteDirectoryContents(file);
        }

        return file.delete() && success;
    }
    */

    public static boolean copyDirectoryContents(File src, File target) {
        Preconditions.checkArgument(src.isDirectory(), "Source dir is not a directory: %s", src);

        // Don't delete symbolic link directories
        if (isSymbolicLink(src)) {
            return false;
        }

        target.mkdirs();
        Preconditions.checkArgument(target.isDirectory(), "Target dir is not a directory: %s", src);

        boolean success = true;
        for (File file : listFiles(src)) {
            success = copyRecursively(file, new File(target, file.getName())) && success;
        }
        return success;
    }

    public static boolean copyRecursively(File src, File target) {
        if (src.isDirectory()) {
            return copyDirectoryContents(src, target);
        } else {
            try {
                Files.copy(src, target);
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }

    public static File newFile(String parent, String... paths) {
        Preconditions.checkNotNull(parent, "parent is null");
        Preconditions.checkNotNull(paths, "paths is null");

        return newFile(new File(parent), ImmutableList.copyOf(paths));
    }

    public static File newFile(File parent, String... paths) {
        Preconditions.checkNotNull(parent, "parent is null");
        Preconditions.checkNotNull(paths, "paths is null");

        return newFile(parent, ImmutableList.copyOf(paths));
    }

    public static File newFile(File parent, Iterable<String> paths) {
        Preconditions.checkNotNull(parent, "parent is null");
        Preconditions.checkNotNull(paths, "paths is null");

        File result = parent;
        for (String path : paths) {
            result = new File(result, path);
        }
        return result;
    }

    public static long getDirectorySizeBytes(String path) {
        long count = 0;
        File dir = new File(path);
        Preconditions.checkArgument(dir.isDirectory(), "Not a directory: %s", dir);

        for (File f : dir.listFiles()) {
            if (f.isFile()) {
                count += f.length();
            }
        }

        return count;
    }
}
