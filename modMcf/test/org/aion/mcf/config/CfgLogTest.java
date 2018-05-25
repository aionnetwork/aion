package org.aion.mcf.config;

import org.aion.db.utils.FileUtils;
import org.aion.log.AionLoggerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for CfgLog.java
 */
public class CfgLogTest extends CfgLog {

    /**
     * User input toggle under "log-file" in config.xml
     */
    private final static String[] toggle = {
            "true",                             // valid configuration
            "tRuE",                             // capitalized entry
            "maybe?",                           // invalid config entry
            ""                                  // null config entry
    };

    /**
     * User input file path under "log-path" in config.xml
     */
    private final static String[] path = {
            "logger",                           // valid file path
            "l!@#*g",                           // invalid file path
            "log/logging/logger",               // folder hierarchy path
            ""                                  // null file path
    };

    /**
     * Before: Creates folder to create test log files in
     * After: Remove the created folder after testing
     */
    File testRoot;
    // Path: /home/joey/Desktop/IDE/aion/modMcf

    @Before
    public void setup() {
        testRoot = new File("testLog");
        if (testRoot.exists()) {
            FileUtils.deleteRecursively(testRoot);
            testRoot.delete();
        }
        testRoot.mkdirs();
    }

    //@After
    public void shutdown() {
        if (testRoot.exists()) {
            FileUtils.deleteRecursively(testRoot);
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
        assertEquals(false, config.logFile);
        assertFalse(config.getLogFile());

        // Test for valid configuration
        config.logFile = Boolean.parseBoolean(toggle[0]);
        assertEquals(true, config.logFile);
        assertTrue(config.getLogFile());

        // Test for capitalized entry
        config.logFile = Boolean.parseBoolean(toggle[1]);
        assertEquals(true, config.logFile);
        assertTrue(config.getLogFile());

        // Test for invalid configuration
        config.logFile = Boolean.parseBoolean(toggle[2]);
        assertEquals(false, config.logFile);
        assertFalse(config.getLogFile());

        // Test for null entry
        config.logFile = Boolean.parseBoolean(toggle[3]);
        assertEquals(false, config.logFile);
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
        assertTrue(config.isValidPath());
        assertEquals("", config.getLogPath());

    }

    /**
     * Test for:
     *  - if archives are stored under correct file name
     *  - if log rollover conditions are correct
     *      - size roughly equal to 100MB
     */
    File generatedPath;
    Map<String, String> _logModules;

    @Test
    public void testLoggedPath() {

        // Test Case Default
        CfgLog config = new CfgLog();

        assertFalse(config.logFile && config.isValidPath());
        if(config.logFile && config.isValidPath()) {
            AionLoggerFactory.init(_logModules, config.logFile, "testLog/" + config.logPath);
            generatedPath = testRoot.listFiles()[0];
            assertEquals("log", generatedPath.getName());
            //reset();
        }

        // Test Case 1: Enabled + Valid
        config.logFile = true;
        config.logPath = "log1";

        assertTrue(config.logFile && config.isValidPath());
        if(config.logFile && config.isValidPath()) {
            AionLoggerFactory.init(_logModules, config.logFile, "testLog/" + config.logPath);
            generatedPath = testRoot.listFiles()[0];
            assertEquals("log1", generatedPath.getName());
            reset();
        }

        // Test Case 2: Enabled + Invalid
        config.logFile = true;
        config.logPath = "*log2*";

        assertFalse(config.logFile && config.isValidPath());
        if(config.logFile && config.isValidPath()) {
            AionLoggerFactory.init(_logModules, config.logFile, "testLog/" + config.logPath);
            generatedPath = testRoot.listFiles()[0];
            assertEquals("log2*", generatedPath.getName());
            reset();
        }

        // Test Case 3: Disabled + Valid
        config.logFile = false;
        config.logPath = "log3";

        assertFalse(config.logFile && config.isValidPath());
        if(config.logFile && config.isValidPath()) {
            AionLoggerFactory.init(_logModules, config.logFile, "testLog/" + config.logPath);
            generatedPath = testRoot.listFiles()[0];
            assertEquals("log3", generatedPath.getName());
            reset();
        }

        // Test Case 4: Disabled + Invalid
        config.logFile = false;
        config.logPath = "*log4*";

        assertFalse(config.logFile && config.isValidPath());
        if(config.logFile && config.isValidPath()) {
            AionLoggerFactory.init(_logModules, config.logFile, "testLog/" + config.logPath);
            generatedPath = testRoot.listFiles()[0];
            assertEquals("*log4*", generatedPath.getName());
            reset();
        }
    }

    public void reset() {
        FileUtils.deleteRecursively(generatedPath);
        generatedPath.delete();
    }
}