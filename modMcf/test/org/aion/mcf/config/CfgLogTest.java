/*
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
 * Contributors:
 *     Aion foundation.
 */
package org.aion.mcf.config;

import org.aion.log.AionLoggerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for CfgLog.java
 */
public class CfgLogTest extends CfgLog {

    private final static String[] toggle = {
            "true",                             // valid entry
            "maybe?",                           // invalid entry
            "tRuE",                             // special entry
            ""                                  // null entry
    };

    private final static String[] path = {
            "logger",                           // valid entry
            "l!@#*g",                           // invalid entry
            "log/logging/logger",               // special entry
            ""                                  // null entry
    };

    private File testRoot;                      // Path: /home/joey/Desktop/IDE/aion/modMcf
    private File generatedPath;
    private String accumulatedPath;
    private Map<String, String> _logModules;

    @Before
    public void setup() {
        testRoot = new File("testLog");
        if (testRoot.exists()) {
            deleteRecursively(testRoot);
            testRoot.delete();
        }
        testRoot.mkdirs();
    }

    @After
    public void shutdown() {
        if (testRoot.exists()) {
            deleteRecursively(testRoot);
            testRoot.delete();
        }
    }

    /**
     * Test for:
     *  - if parseBoolean() correctly parses user input
     *  - if getLogFile() returns the correct configuration
     */
    @Test
    public void testToggle() {

        // Test for default log configuration
        CfgLog config = new CfgLog();
        assertFalse(config.getLogFile());

        // Test for valid configuration
        config.logFile = Boolean.parseBoolean(toggle[0]);
        assertTrue(config.getLogFile());

        // Test for capitalized entry
        config.logFile = Boolean.parseBoolean(toggle[1]);
        assertFalse(config.getLogFile());

        // Test for invalid configuration
        config.logFile = Boolean.parseBoolean(toggle[2]);
        assertTrue(config.getLogFile());

        // Test for null entry
        config.logFile = Boolean.parseBoolean(toggle[3]);
        assertFalse(config.getLogFile());

    }

    /**
     * Test for:
     *  - if isValidPath() validates user input log path
     *  - if getLogPath() returns the correct log path
     */
    @Test
    public void testPathInput() {

        // Test for default file path
        CfgLog config = new CfgLog();
        assertTrue(config.isValidPath());
        assertEquals("log", config.getLogPath());

        // Test for valid file path
        config.logPath = path[0];
        assertTrue(config.isValidPath());
        assertEquals("logger", config.getLogPath());

        // Test for invalid file path
        config.logPath = path[1];
        assertFalse(config.isValidPath());

        // Test for folder hierarchy path
        config.logPath = path[2];
        assertTrue(config.isValidPath());
        assertEquals("log/logging/logger", config.getLogPath());

        // Test for null path
        config.logPath = path[3];
        assertFalse(config.isValidPath());

    }

    /**
     * Test for:
     *  - if archives are stored under correct file name
     */
    @Test
    public void testLoggedPath() {

        // Test Case Default
        CfgLog config = new CfgLog();
        assertFalse(config.logFile && config.isValidPath());

        if(config.logFile && config.isValidPath()) {
            AionLoggerFactory.init(_logModules, config.logFile, "testLog/" + config.logPath);
            generatedPath = testRoot.listFiles()[0];
            assertEquals("log", generatedPath.getName());
            reset();
        }

        // All Test Case
        for(int a = 0; a < 4; a++){
            for(int b = 0; b < 4; b++){
                config.logFile = Boolean.parseBoolean(toggle[a]);
                config.logPath = path[b];

                if(config.logFile && config.isValidPath()) {
                    AionLoggerFactory.init(_logModules, config.logFile, "testLog/" + config.logPath);
                    generatedPath = testRoot.listFiles()[0];

                    accumulatedPath = generatedPath.getName();
                    while(generatedPath.listFiles()[0].isDirectory()) {
                        generatedPath = generatedPath.listFiles()[0];
                        accumulatedPath = accumulatedPath + "/" + generatedPath.getName();
                    }
                    assertEquals(path[b], accumulatedPath);
                    reset();
                }
            }
        }
    }

    public void reset() {
        deleteRecursively(testRoot);
        testRoot.mkdirs();
    }

    public static boolean deleteRecursively(File file) {
        Path path = file.toPath();
        try {
            java.nio.file.Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
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
                public FileVisitResult postVisitDirectory(final Path dir, final IOException e) throws IOException {
                    if (e != null)
                        return handleException(e);
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
}
