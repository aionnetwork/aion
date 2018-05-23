package org.aion.mcf.config;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for CfgLog.java
 */
public class CfgLogTest extends CfgLog {

    private final static String user1 = "logger";       // valid file path
    private final static String user2 = "l!@#g";        // invalid file path
    private final static String user3 = "log/logger";   // folder hierarchy path
    private final static String user4 = "";             // null file path

    @Before
    public void setup() {

    }

    @Test
    public void testLogPath() {

        // Test for default file path
        CfgLog config = new CfgLog();
        assertTrue(config.isValidPath());
        assertEquals("log", config.getLogPath());

        // Test for valid file path
        config.logPath = user1;
        assertTrue(config.isValidPath());
        assertEquals("logger", config.getLogPath());

        // Test for invalid file path
        config.logPath = user2;
        assertFalse(config.isValidPath());

        // Test for folder hierarchy path
        config.logPath = user3;
        assertTrue(config.isValidPath());
        assertEquals("log/logger", config.getLogPath());

        // Test for null path
        config.logPath = user4;
        assertTrue(config.isValidPath());
        assertEquals("", config.getLogPath());

    }

}