package org.aion.log;

import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * Wrapper for {@link Logger} objects that ensures a check for enabled is performed before the
 * attempt to log messages.
 *
 * @author Alexandra Roatis
 */
public class AionLogger implements Logger {
    private final Logger log;

    private AionLogger(Logger log) {
        this.log = log;
    }

    public static AionLogger wrap(Logger log) {
        return new AionLogger(log);
    }

    @Override
    public String getName() {
        return log.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return log.isTraceEnabled();
    }

    @Override
    public void trace(String msg) {
        if (log.isTraceEnabled()) {
            log.trace(msg);
        }
    }

    @Override
    public void trace(String format, Object arg) {
        if (log.isTraceEnabled()) {
            log.trace(format, arg);
        }
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        if (log.isTraceEnabled()) {
            log.trace(format, arg1, arg2);
        }
    }

    @Override
    public void trace(String format, Object... arguments) {
        if (log.isTraceEnabled()) {
            log.trace(format, arguments);
        }
    }

    @Override
    public void trace(String msg, Throwable t) {
        if (log.isTraceEnabled()) {
            log.trace(msg, t);
        }
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return log.isTraceEnabled(marker);
    }

    @Override
    public void trace(Marker marker, String msg) {
        if (log.isTraceEnabled(marker)) {
            log.trace(marker, msg);
        }
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        if (log.isTraceEnabled(marker)) {
            log.trace(marker, format, arg);
        }
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        if (log.isTraceEnabled(marker)) {
            log.trace(marker, format, arg1, arg2);
        }
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {
        if (log.isTraceEnabled(marker)) {
            log.trace(marker, format, argArray);
        }
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        if (log.isTraceEnabled(marker)) {
            log.trace(marker, msg, t);
        }
    }

    @Override
    public boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    @Override
    public void debug(String msg) {
        if (log.isDebugEnabled()) {
            log.debug(msg);
        }
    }

    @Override
    public void debug(String format, Object arg) {
        if (log.isDebugEnabled()) {
            log.debug(format, arg);
        }
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        if (log.isDebugEnabled()) {
            log.debug(format, arg1, arg2);
        }
    }

    @Override
    public void debug(String format, Object... arguments) {
        if (log.isDebugEnabled()) {
            log.debug(format, arguments);
        }
    }

    @Override
    public void debug(String msg, Throwable t) {
        if (log.isDebugEnabled()) {
            log.debug(msg, t);
        }
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return log.isDebugEnabled(marker);
    }

    @Override
    public void debug(Marker marker, String msg) {
        if (log.isDebugEnabled(marker)) {
            log.debug(marker, msg);
        }
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        if (log.isDebugEnabled(marker)) {
            log.debug(marker, format, arg);
        }
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        if (log.isDebugEnabled(marker)) {
            log.debug(marker, format, arg1, arg2);
        }
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        if (log.isDebugEnabled(marker)) {
            log.debug(marker, format, arguments);
        }
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        if (log.isDebugEnabled(marker)) {
            log.debug(marker, msg, t);
        }
    }

    @Override
    public boolean isInfoEnabled() {
        return log.isInfoEnabled();
    }

    @Override
    public void info(String msg) {
        if (log.isInfoEnabled()) {
            log.info(msg);
        }
    }

    @Override
    public void info(String format, Object arg) {
        if (log.isInfoEnabled()) {
            log.info(format, arg);
        }
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        if (log.isInfoEnabled()) {
            log.info(format, arg1, arg2);
        }
    }

    @Override
    public void info(String format, Object... arguments) {
        if (log.isInfoEnabled()) {
            log.info(format, arguments);
        }
    }

    @Override
    public void info(String msg, Throwable t) {
        if (log.isInfoEnabled()) {
            log.info(msg, t);
        }
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return log.isInfoEnabled(marker);
    }

    @Override
    public void info(Marker marker, String msg) {
        if (log.isInfoEnabled(marker)) {
            log.info(marker, msg);
        }
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        if (log.isInfoEnabled(marker)) {
            log.info(marker, format, arg);
        }
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        if (log.isInfoEnabled(marker)) {
            log.info(marker, format, arg1, arg2);
        }
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
        if (log.isInfoEnabled(marker)) {
            log.info(marker, format, arguments);
        }
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        if (log.isInfoEnabled(marker)) {
            log.info(marker, msg, t);
        }
    }

    @Override
    public boolean isWarnEnabled() {
        return log.isWarnEnabled();
    }

    @Override
    public void warn(String msg) {
        if (log.isWarnEnabled()) {
            log.warn(msg);
        }
    }

    @Override
    public void warn(String format, Object arg) {
        if (log.isWarnEnabled()) {
            log.warn(format, arg);
        }
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        if (log.isWarnEnabled()) {
            log.warn(format, arg1, arg2);
        }
    }

    @Override
    public void warn(String format, Object... arguments) {
        if (log.isWarnEnabled()) {
            log.warn(format, arguments);
        }
    }

    @Override
    public void warn(String msg, Throwable t) {
        if (log.isWarnEnabled()) {
            log.warn(msg, t);
        }
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return log.isWarnEnabled(marker);
    }

    @Override
    public void warn(Marker marker, String msg) {
        if (log.isWarnEnabled(marker)) {
            log.warn(marker, msg);
        }
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        if (log.isWarnEnabled(marker)) {
            log.warn(marker, format, arg);
        }
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        if (log.isWarnEnabled(marker)) {
            log.warn(marker, format, arg1, arg2);
        }
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        if (log.isWarnEnabled(marker)) {
            log.warn(marker, format, arguments);
        }
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        if (log.isWarnEnabled(marker)) {
            log.warn(marker, msg, t);
        }
    }

    @Override
    public boolean isErrorEnabled() {
        return log.isErrorEnabled();
    }

    @Override
    public void error(String msg) {
        if (log.isErrorEnabled()) {
            log.error(msg);
        }
    }

    @Override
    public void error(String format, Object arg) {
        if (log.isErrorEnabled()) {
            log.error(format, arg);
        }
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        if (log.isErrorEnabled()) {
            log.error(format, arg1, arg2);
        }
    }

    @Override
    public void error(String format, Object... arguments) {
        if (log.isErrorEnabled()) {
            log.error(format, arguments);
        }
    }

    @Override
    public void error(String msg, Throwable t) {
        if (log.isErrorEnabled()) {
            log.error(msg, t);
        }
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return log.isErrorEnabled(marker);
    }

    @Override
    public void error(Marker marker, String msg) {
        if (log.isErrorEnabled(marker)) {
            log.error(marker, msg);
        }
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        if (log.isErrorEnabled(marker)) {
            log.error(marker, format, arg);
        }
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        if (log.isErrorEnabled(marker)) {
            log.error(marker, format, arg1, arg2);
        }
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
        if (log.isErrorEnabled(marker)) {
            log.error(marker, format, arguments);
        }
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        if (log.isErrorEnabled(marker)) {
            log.error(marker, msg, t);
        }
    }
}
