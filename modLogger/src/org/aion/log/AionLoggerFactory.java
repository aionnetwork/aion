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
package org.aion.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.rolling.helper.FileNamePattern;
import ch.qos.logback.core.util.FileSize;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used to override SimpleLogger current log level
 *
 * final public int TRACE_INT = 00; final public int DEBUG_INT = 10; finalConcurrentHashMap
 * public int INFO_INT = 20; final public int WARN_INT = 30; final public int ERROR_INT = 40;
 *
 * Default set to 50 which ignore output
 */
public class AionLoggerFactory {

    /**
     * Due to Cfg is abstract, use this static attribute to hold muti-chains config attribute
     * List<CfgLogModule>, which is chain neural.
     */
    private static Map<String, String> logModules;

    private static LoggerContext loggerContext;
    private static ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
    private static final PatternLayoutEncoder encoder = new PatternLayoutEncoder();

    /** Static declaration of logFile */
    private static boolean logFile;
    private static String logPath;
    private static RollingFileAppender fileAppender;

    static {
        logModules = new HashMap<>();
        String level = LogLevels.INFO.name();
        for (LogEnum module : LogEnum.values()) {
            logModules.put(module.name(), level);
        }
    }

    public static void init(final Map<String, String> _logModules) {
        init(_logModules, false, "log");
    }

    public static void init(final Map<String, String> _logModules, boolean _logToFile, String _logToPath) {

        logModules = _logModules;
        logFile = _logToFile;
        logPath = _logToPath;

        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        /* Toggles file appending configurations */
        if (logFile) {

            /* Initialize Rolling-File-Appender */
            String fileName = logPath + "/aionCurrentLog.dat";
            fileAppender = new RollingFileAppender();
            fileAppender.setContext(loggerContext);
            fileAppender.setName("aionlogger");
            fileAppender.setFile(fileName);

            /* Initialize Triggering-Policy (CONDITION) */
            SizeBasedTriggeringPolicy tp = new SizeBasedTriggeringPolicy();
            tp.setContext(loggerContext);
            tp.start();

            /* Initialize Rolling-Policy (BEHAVIOUR) */
            SizeAndTimeBasedRollingPolicy rp = new SizeAndTimeBasedRollingPolicy();
            rp.setContext(loggerContext);

            /*
             * To modify period of each rollover;
             * https://logback.qos.ch/manual/appenders.html#TimeBasedRollingPolicy
             * (Currently set to PER DAY)
             */
            FileNamePattern fnp =
                    new FileNamePattern(
                            logPath + "/%d{yyyy/MM, aux}/aion.%d{yyyy-MM-dd}.%i.log", loggerContext);
            rp.setFileNamePattern(fnp.getPattern());

            /*
             * To modify size of each rollover file;
             * https://logback.qos.ch/manual/appenders.html#SizeAndTimeBasedRollingPolicy
             * (Currently set to 100MB)
             */
            rp.setMaxFileSize(new FileSize(100 * 1000 * 1000));
            rp.setParent(fileAppender);
            rp.start();

            /* Sets TRIGGER & ROLLING policy */
            fileAppender.setTriggeringPolicy(tp);
            fileAppender.setRollingPolicy(rp);

            /* Set fileAppender configurations */
            fileAppender.setContext(loggerContext);
            fileAppender.setEncoder(encoder);
            fileAppender.setAppend(true);
            fileAppender.start();
        }

        encoder.setContext(loggerContext);
        encoder.setPattern("%date{yy-MM-dd HH:mm:ss.SSS} %-5level %-4c [%thread]: %message%n");
        encoder.start();

        appender.setContext(loggerContext);
        appender.setEncoder(encoder);
        appender.start();

        ch.qos.logback.classic.Logger rootlogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootlogger.detachAndStopAllAppenders();
    }

    private static ConcurrentMap<String, Logger> loggerMap =
            new ConcurrentHashMap<String, Logger>();

    public static Logger getLogger(String label) {

        Logger logger = loggerMap.get(label);
        return logger == null ? newLogger(label) : logger;
    }

    private static Logger newLogger(String label) {

        if (loggerContext == null) {
            // System.out.println("If you see this line, meaning you are under
            // the unit test!!! If you are not. should report an issue.");
            // init(new HashMap<>(), false);
            init(new HashMap<>());
        }

        ch.qos.logback.classic.Logger newlogger = loggerContext.getLogger(label);
        newlogger.addAppender(appender);

        /* Toggles file appending */
        if (logFile) {
            newlogger.addAppender(fileAppender);
        }

        boolean flag = false;
        Iterator<Entry<String, String>> it = logModules.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, String> logModule = it.next();
            if (logModule.getKey().equals(label)) {
                LogLevels logLevel = LogLevels.valueOf(logModule.getValue());
                switch (logLevel) {
                    case TRACE:
                        newlogger.setLevel(Level.TRACE);
                        flag = true;
                        break;
                    case ERROR:
                        newlogger.setLevel(Level.ERROR);
                        flag = true;
                        break;
                    case INFO:
                        newlogger.setLevel(Level.INFO);
                        flag = true;
                        break;
                    case DEBUG:
                        newlogger.setLevel(Level.DEBUG);
                        flag = true;
                        break;
                }
            }

            if (flag) break;
        }

        if (!flag) {
            newlogger.setLevel(Level.OFF);
        }

        Logger existLogger = loggerMap.putIfAbsent(label, newlogger);
        return existLogger == null ? newlogger : existLogger;
    }
}
