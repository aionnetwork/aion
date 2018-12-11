package org.aion.mcf.account;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

/**
 * Used to compare the formatted date on keystore files to determine the progenitor
 * (original/coinbase) key. Note that if the key is not valid, a series of (abitrary) choices are
 * made for the ordering of the invalid file name.
 *
 * <p>
 *
 * <p>The ordering (who is placed earlier) is determined as such:
 *
 * <ul>
 *   <li>ill formatted dates
 *   <li>ill formatted strings
 *   <li>nulls
 * </ul>
 *
 * <p>The class will silently fail if and guarantees (hopefully) to not throw any errors
 *
 * @author yao
 */
public class FileDateTimeComparator implements Comparator<File> {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    @Override
    public int compare(File arg0, File arg1) {
        if (arg0 == null && arg1 == null) return 0;

        if (arg0 == null) return 1;

        if (arg1 == null) return -1;

        String[] frag0 = arg0.getName().split("--");
        String[] frag1 = arg1.getName().split("--");

        // check frag length
        if (frag0.length != 3 && frag1.length != 3) return 0;

        if (frag0.length != 3) return 1;

        if (frag1.length != 3) return -1;

        // TODO: make sure this is the same as the pattern we use to encode

        // check time zone
        if (!frag0[0].equals("UTC") && !frag1[0].equals("UTC")) return 0;

        if (!frag0[0].equals("UTC")) return 1;

        if (!frag1[0].equals("UTC")) return -1;

        LocalDateTime frag0DateTime = null;
        LocalDateTime frag1DateTime = null;
        try {
            frag0DateTime = LocalDateTime.from(FORMATTER.parse(frag0[1]));
        } catch (Exception e) {
            // fail silently
        }
        try {
            frag1DateTime = LocalDateTime.from(FORMATTER.parse(frag1[1]));
        } catch (Exception e) {
            // fail silently
        }

        if (frag0DateTime == null && frag1DateTime == null) return 0;

        /** If only frag0 is null, then move it back */
        if (frag0DateTime == null) return 1;

        /** If only frag1 is null, then move it back */
        if (frag1DateTime == null) return -1;

        // lets do valuable comparisons here
        if (frag0DateTime.isBefore(frag1DateTime)) return -1;

        if (frag1DateTime.isBefore(frag0DateTime)) return 1;

        // wow completely equal!
        return 0;
    }
}
