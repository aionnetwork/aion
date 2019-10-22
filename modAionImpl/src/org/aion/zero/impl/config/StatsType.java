package org.aion.zero.impl.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Enumerates the different types of statistics gathered by the kernel. Used for determining which
 * statistics to display.
 *
 * @author Alexandra Roatis
 */
public enum StatsType {
    ALL,
    REQUESTS,
    SEEDS,
    LEECHES,
    RESPONSES,
    SYSTEMINFO,
    NONE; // used as default for invalid settings

    private static final List<StatsType> allSpecificTypes =
            Collections.unmodifiableList(
                    Arrays.asList(REQUESTS, SEEDS, LEECHES, RESPONSES, SYSTEMINFO));

    /**
     * List of all the specific types of statistics that can be displayed, i.e. excluding the {@link
     * #ALL} and {@link #NONE}.
     */
    public static List<StatsType> getAllSpecificTypes() {
        return allSpecificTypes;
    }
}
