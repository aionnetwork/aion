/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p;

import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;

/**
 * @author  chris
 */
public class VersionTest {

    @Test
    public void testFilter() {

        /*
         * active versions
         */
        short v0 = (byte)ThreadLocalRandom.current().nextInt(0, 2);
        assertEquals(v0, Version.filter(v0));

        /*
         * inactive versions
         */
        byte b1 = (byte)ThreadLocalRandom.current().nextInt(2, Short.MAX_VALUE);
        assertEquals(Version.UNKNOWN, Version.filter(b1));

    }
}
