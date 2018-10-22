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
package org.aion.gui.model;

import com.google.common.annotations.VisibleForTesting;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConsoleTail {
    private final ScheduledExecutorService timer;

    private LocalDateTime lastStatusTime;
    private String message;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-YY hh:mm a");

    public ConsoleTail() {
        this(Executors.newSingleThreadScheduledExecutor());
    }

    public ConsoleTail(ScheduledExecutorService timer) {
        this.timer = timer;
        this.message = null;
        this.lastStatusTime = null;

        this.timer.scheduleAtFixedRate(() -> makeStatus(), 0, 1, TimeUnit.MINUTES);
    }

    public void setMessage(String message) {
        this.message = message;
        this.lastStatusTime = LocalDateTime.now();
    }

    /**
     * Create a user-friendly string representing the time interval between now and the last status
     * update
     *
     * <p>The string will be as follows:
     *
     * <p>if interval is larger than 1 day: "YYYY-mm-dd HH:mm AM/PM" if interval is between 1 hour
     * and 1 day: "today HH:mm AM/PM" if interval is between 1 minute and 1 hour: "x minutes ago" if
     * interval is between 1 second and 1 minute: "less than a minute ago"
     */
    @VisibleForTesting
    String makeTimeIntervalString(LocalDateTime now) {
        long daysDiff = lastStatusTime.until(now, ChronoUnit.DAYS);
        if (daysDiff > 0) {
            return lastStatusTime.format(FORMATTER);
        } else if (lastStatusTime.until(now, ChronoUnit.HOURS) > 0) {
            return "today " + lastStatusTime.toLocalTime().format(FORMATTER);
        } else if (lastStatusTime.until(now, ChronoUnit.MINUTES) > 0) {
            return lastStatusTime.until(now, ChronoUnit.MINUTES) + " minutes ago";
        } else {
            return "less than a minute ago";
        }
    }

    /**
     * Create a user-friendly string representing the time interval between now and the last status
     * update
     *
     * <p>The string will be as follows:
     *
     * <p>if interval is larger than 1 day: "YYYY-mm-dd HH:mm AM/PM" if interval is between 1 hour
     * and 1 day: "today HH:mm AM/PM" if interval is between 1 minute and 1 hour: "x minutes ago" if
     * interval is between 1 second and 1 minute: "less than a minute ago"
     */
    private String makeTimeIntervalString() {
        return makeTimeIntervalString(LocalDateTime.now());
    }

    public String makeStatus() {
        if (message == null) {
            return "";
        } else {
            return String.format("%s (%s)", message, makeTimeIntervalString());
        }
    }
}
