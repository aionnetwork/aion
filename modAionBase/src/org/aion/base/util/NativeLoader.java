/*
*****************************************************************************
* Copyright (c) 2017-2018 Aion foundation.
*
* This file is part of the aion network project.
*
* The aion network project is free software: you can redistribute it
* and/or modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation, either version 3 of
* the License, or any later version.
*
* The aion network project is distributed in the hope that it will
* be useful, but WITHOUT ANY WARRANTY; without even the implied
* warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
* See the GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with the aion network project source files.
* If not, see <https://www.gnu.org/licenses/>.
*
* The aion network project leverages useful source code from other
* open source projects. We greatly appreciate the effort that was
* invested in these projects and we thank the individual contributors
* for their work. For provenance information and contributors
* please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
*
* Contributors to the aion source files in decreasing order of code volume:
* Aion foundation.
* <ether.camp> team through the ethereumJ library.
* Ether.Camp Inc. (US) team through Ethereum Harmony.
* John Tromp through the Equihash solver.
* Samuel Neves through the BLAKE2 implementation.
* Zcash project team.
* Bitcoinj team.
*****************************************************************************
*/
package org.aion.base.util;

import java.io.File;
//import java.io.FileNotFoundException;
//import java.io.FileOutputStream;
import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
import java.util.Scanner;

/**
 * Native library loader.
 *
 * @author jin
 */
public class NativeLoader {

    /**
     * Returns the current OS name.
     *
     * @return
     */
    public static String getOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return "win";
        } else if (osName.contains("linux")) {
            return "linux";
        } else if (osName.contains("mac")) {
            return "mac";
        } else {
            throw new RuntimeException("Unrecognized OS: " + osName);
        }
    }

    /**
     * Builds a file path given a list of folder names.
     *
     * @param args
     * @return
     */
    public static File buildPath(String... args) {
        StringBuilder sb = new StringBuilder();
        for (String arg : args) {
            sb.append(File.separator);
            sb.append(arg);
        }

        return sb.length() > 0 ? new File(sb.substring(1)) : new File(".");
    }

    /**
     * Loads library based on the file list in the given module folder.
     *
     * @param module
     */
    public static void loadLibrary(String module) {
        File dir = buildPath("native", getOS(), module);

        try (Scanner s = new Scanner(new File(dir, "file.list"))) {
            while (s.hasNextLine()) {
                String line = s.nextLine();

                if (line.startsWith("/") || line.startsWith(".")) { // for debug
                    // purpose
                    // mainly
                    System.load(line);
                } else {
                    System.load(new File(dir, line).getCanonicalPath());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load libraries for " + module, e);
        }
    }

//    public static void loadLibraryFromJar(@SuppressWarnings("rawtypes") Class clz, String path)
//            throws IOException {
//
//        if (!path.startsWith("/")) {
//            throw new IllegalArgumentException("The path has to be absolute (start with '/').");
//        }
//
//        // Obtain filename from path
//        String[] parts = path.split("/");
//        String filename = (parts.length > 1) ? parts[parts.length - 1] : null;
//
//        // Split filename to prexif and suffix (extension)
//        String prefix = "";
//        String suffix = null;
//        if (filename != null) {
//            parts = filename.split("\\.", 2);
//            prefix = parts[0];
//            suffix = (parts.length > 1) ? "." + parts[parts.length - 1] : null;
//        }
//
//        // Check if the filename is okay
//        if (filename == null || prefix.length() < 3) {
//            throw new IllegalArgumentException(
//                    "The filename has to be at least 3 characters long.");
//        }
//
//        // Prepare temporary file
//        File temp = File.createTempFile(prefix, suffix);
//        temp.deleteOnExit();
//
//        if (!temp.exists()) {
//            throw new FileNotFoundException("File " + temp.getAbsolutePath() + " does not exist.");
//        }
//
//        // Prepare buffer for data copying
//        byte[] buffer = new byte[1024];
//        int readBytes;
//
//        // Open and check input stream
//        InputStream is = clz.getResourceAsStream(path);
//        if (is == null) {
//            throw new FileNotFoundException("File " + path + " was not found inside JAR.");
//        }
//
//        // Open output stream and copy data between source file in JAR and the
//        // temporary file
//        OutputStream os = new FileOutputStream(temp);
//        try {
//            while ((readBytes = is.read(buffer)) != -1) {
//                os.write(buffer, 0, readBytes);
//            }
//        } finally {
//            // If read/write fails, close streams safely before throwing an
//            // exception
//            os.close();
//            is.close();
//        }
//
//        // Finally, load the library
//        System.load(temp.getAbsolutePath());
//    }
}
