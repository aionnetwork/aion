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

package org.aion.mcf.config;

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
    PEER_STATES,
    REQUESTS,
    SEEDS,
    LEECHES,
    RESPONSES,
    NONE; // used as default for invalid settings

    private static final List<StatsType> allSpecificTypes =
            Collections.unmodifiableList(
                    Arrays.asList(PEER_STATES, REQUESTS, SEEDS, LEECHES, RESPONSES));

    /**
     * List of all the specific types of statistics that can be displayed, i.e. excluding the {@link
     * #ALL} and {@link #NONE}.
     */
    public static List<StatsType> getAllSpecificTypes() {
        return allSpecificTypes;
    }
}
