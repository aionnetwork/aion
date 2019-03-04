package org.aion.util.time;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeUtils {

    /*
     * Converts minutes to millis
     *
     * @param minutes time in minutes
     * @return corresponding millis value
     */
    //    public static long minutesToMillis(long minutes) {
    //        return minutes * 60 * 1000;
    //    }

    /**
     * Converts seconds to millis
     *
     * @param seconds time in seconds
     * @return corresponding millis value
     */
    public static long secondsToMillis(long seconds) {
        return seconds * 1000;
    }

    /**
     * Return formatted Date String: yyyy.MM.dd HH:mm:ss Based on Unix's time() input in seconds
     *
     * @param timestamp seconds since start of Unix-time
     * @return String formatted as - yyyy.MM.dd HH:mm:ss
     */
    public static String longToDateTime(long timestamp) {
        Date date = new Date(timestamp * 1000);
        DateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
        return formatter.format(date);
    }

    /*
     * Converts millis to minutes
     *
     * @param millis time in millis
     * @return time in minutes
     */
    //    public static long millisToMinutes(long millis) {
    //        return Math.round(millis / 60.0 / 1000.0);
    //    }

    /*
     * Converts millis to seconds
     *
     * @param millis time in millis
     * @return time in seconds
     */
    //    public static long millisToSeconds(long millis) {
    //        return Math.round(millis / 1000.0);
    //    }

    /*
     * Returns timestamp in the future after some millis passed from now
     *
     * @param millis millis count
     * @return future timestamp
     */
    //    public static long timeAfterMillis(long millis) {
    //        return System.currentTimeMillis() + millis;
    //    }

}
