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
            // maybe use equalsIgnoreCase here?
            if (level.name().equals(_level)) return true;
        }
        return false;
    }
}
