package org.aion.log;

public enum LogLevel {

    // loglevels, from highest (most verbose) to lowest (least verbose)
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    OFF;

    public static boolean contains(String _level) {
        for (LogLevel level : values()) {
            if (level.name().equalsIgnoreCase(_level)) return true;
        }
        return false;
    }
}
