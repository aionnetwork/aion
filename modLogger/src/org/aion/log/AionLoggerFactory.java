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

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.rolling.helper.FileNamePattern;
import ch.qos.logback.core.util.FileSize;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Design: An (ugly) static-class wrapper around logback.
 *
 * Currently, only way to configure this logger is through the Aion config file.
 *
 * Loglevels in Aion map to Loglevels in logback.
 *
 * Upon factory instantiation, LogEnum appropriate log-levels are assigned and the logger objects are instantiated.
 *
 * If a logger is requested by String that does not match the loggers defined in the LogEnum, the GEN (general) logger
 * is returned.
 *
 * @author github.com/ali-sharif
 */
public class AionLoggerFactory {

    private static final String DEFAULT_LOG_DIR = "log";
    private static final String DEFAULT_LOG_FILE_CURRENT = "aion.log";
    private static final String DEFAULT_LOG_PATTERN = "%date{yy-MM-dd HH:mm:ss.SSS} %-5level %-4c [%thread]: %message%n";

    // async appender settings. if running into performance issues or lost logs, here is what needs tweaking:
    // https://logging.apache.org/log4j/2.x/manual/async.html#Performance
    // https://logback.qos.ch/manual/appenders.html#AsyncAppender
    // https://stackoverflow.com/questions/46411704/configuration-and-performance-of-the-asyncappender-in-logback-framework
    private static final boolean ASYNC_LOGGER_INCLUDE_CALLER_DATA = false;
    private static final boolean ASYNC_LOGGER_NEVER_BLOCK = true; // may loose logging events, maybe worth exposing out through config
    private static final int ASYNC_LOGGER_MAX_FLUSH_TIME_MS = 10_000; // 10s, logback default is 1s
    private static final int ASYNC_LOGGER_DISCARDING_THRESHOLD = 0;
    private static final int ASYNC_LOGGER_QUEUE_SIZE = 8192;

    private static final LoggerContext context;

    // static class initialization guaranteed to be thread-safe;
    // https://docs.oracle.com/javase/specs/jls/se10/html/jls-12.html#jls-12.4.2
    static {
        context = (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    public static void init(Map<String, String> requestedLogLevels) {
        init(requestedLogLevels, false, "");
    }

    private static Map<LogEnum, Level> constructModuleLoglevelMap(Map<String, String> _moduleToLevelMap) {
        // condition the input hashmap so keys are all uppercase
        Map<String, String> moduleToLevelMap = new HashMap<>();
        _moduleToLevelMap.forEach((module, level) -> {
            moduleToLevelMap.put(module.toUpperCase(), level);
        });

        Map<LogEnum, Level> modules = new HashMap<>();
        for (LogEnum mod : LogEnum.values()) {
            String modName = mod.name().toUpperCase();
            String modLevel = moduleToLevelMap.get(modName);
            if (modLevel != null) {
                // if we can't translate log-level for some reason, default to INFO
                Level level = Level.toLevel(modLevel, Level.INFO);
                modules.put(mod, level);
            } else {
                modules.put(mod, Level.INFO);
            }
        }

        return modules;
    }

    private static List<Appender<ILoggingEvent>> constructAppenders(boolean shouldLogToFile, String _logDirectory) {
        List<Appender<ILoggingEvent>> appenders = new ArrayList<>();

        String logDirectory = DEFAULT_LOG_DIR;
        if (_logDirectory != null && !_logDirectory.trim().isEmpty())
            logDirectory = _logDirectory;

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(DEFAULT_LOG_PATTERN);
        encoder.start();

        ConsoleAppender<ILoggingEvent> consoleSync = new ConsoleAppender<>();
        consoleSync.setContext(context);
        consoleSync.setName("consoleSyncAppender"); // for logger debugging
        consoleSync.setEncoder(encoder);
        consoleSync.start();

        AsyncAppender consoleAsync = new AsyncAppender();
        consoleAsync.setContext(context);
        consoleAsync.setName("consoleAsyncAppender"); // for logger debugging
        consoleAsync.addAppender(consoleSync);

        consoleAsync.setIncludeCallerData(ASYNC_LOGGER_INCLUDE_CALLER_DATA);
        consoleAsync.setNeverBlock(ASYNC_LOGGER_NEVER_BLOCK);
        consoleAsync.setMaxFlushTime(ASYNC_LOGGER_MAX_FLUSH_TIME_MS);
        consoleAsync.setDiscardingThreshold(ASYNC_LOGGER_DISCARDING_THRESHOLD);
        consoleAsync.setQueueSize(ASYNC_LOGGER_QUEUE_SIZE);
        consoleAsync.start();

        appenders.add(consoleAsync);
        if (!shouldLogToFile) return  appenders;

        RollingFileAppender<ILoggingEvent> fileSync = new RollingFileAppender<>();

        SizeBasedTriggeringPolicy tp = new SizeBasedTriggeringPolicy();
        tp.setContext(context);
        tp.start();

        SizeAndTimeBasedRollingPolicy rp = new SizeAndTimeBasedRollingPolicy();
        rp.setContext(context);
        // roll-over each day
        // notice that we don't use the OS-agnostic File.separator here since logback is converts the FileNamePattern
        // to a unix-style path using ch.qos.logback.core.rolling.helper.FileFilterUtil.slashify
        FileNamePattern fnp = new FileNamePattern(logDirectory + "/%d{yyyy/MM, aux}/aion.%d{yyyy-MM-dd}.%i.log", context);
        rp.setFileNamePattern(fnp.getPattern());
        // max rollover file size = 100MB
        rp.setMaxFileSize(FileSize.valueOf("100mb"));
        rp.setParent(fileSync);
        rp.start();

        fileSync.setName("fileSyncAppender"); // for logger debugging
        fileSync.setContext(context);
        fileSync.setTriggeringPolicy(tp);
        fileSync.setRollingPolicy(rp);
        fileSync.setFile(logDirectory + File.separator + DEFAULT_LOG_FILE_CURRENT);
        fileSync.setEncoder(encoder);
        fileSync.setAppend(true);
        fileSync.start();

        AsyncAppender fileAsync = new AsyncAppender();
        fileAsync.setContext(context);
        fileAsync.setName("fileAsyncAppender"); // for logger debugging
        fileAsync.addAppender(fileSync);

        fileAsync.setIncludeCallerData(ASYNC_LOGGER_INCLUDE_CALLER_DATA);
        fileAsync.setNeverBlock(ASYNC_LOGGER_NEVER_BLOCK);
        fileAsync.setMaxFlushTime(ASYNC_LOGGER_MAX_FLUSH_TIME_MS);
        fileAsync.setDiscardingThreshold(ASYNC_LOGGER_DISCARDING_THRESHOLD);
        fileAsync.setQueueSize(ASYNC_LOGGER_QUEUE_SIZE);
        fileAsync.start();

        appenders.add(fileAsync);
        return appenders;
    }

    public synchronized static void init(
            Map<String, String> requestedLogLevels,
            boolean shouldLogToFile,
            String logDirectory) {

        Map<LogEnum, Level> modules = constructModuleLoglevelMap(requestedLogLevels);
        List<Appender<ILoggingEvent>> appenders = constructAppenders(shouldLogToFile, logDirectory);

        // remove all appenders from the root logger so we can override those appenders with our own.
        context.getLogger(Logger.ROOT_LOGGER_NAME).detachAndStopAllAppenders();

        // initialize the loggers
        modules.forEach((LogEnum module, Level level) -> {
            String loggerName = module.name();

            ch.qos.logback.classic.Logger logger = context.getLogger(loggerName);

            // make sure logs don't bubble up to root logger
            logger.setAdditive(false);

            // set log level
            logger.setLevel(level);

            // attach all appenders to all loggers
            appenders.forEach(logger::addAppender);
        });
    }

    // note: this method is thread safe; delegated all thread safety down to logback
    public static Logger getLogger(String label) {
        Logger logger = context.exists(label);
        if (logger != null) return logger;

        // root logger should always be available and should not return null
        return context.getLogger(Logger.ROOT_LOGGER_NAME);
    }
}