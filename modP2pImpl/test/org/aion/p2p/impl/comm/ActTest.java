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

package org.aion.p2p.impl.comm;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.ThreadLocalRandom;
import org.junit.Test;

/** @author chris */
public class ActTest {

    @Test
    public void testFilter() {

        /*
         * active versions
         */
        assertEquals(Act.REQ_HANDSHAKE, Act.filter(Act.REQ_HANDSHAKE));
        assertEquals(Act.RES_HANDSHAKE, Act.filter(Act.RES_HANDSHAKE));
        assertEquals(Act.REQ_ACTIVE_NODES, Act.filter(Act.REQ_ACTIVE_NODES));
        assertEquals(Act.RES_ACTIVE_NODES, Act.filter(Act.RES_ACTIVE_NODES));

        /*
         * inactive versions
         */
        assertEquals(Act.UNKNOWN, Act.filter(Act.DISCONNECT));
        assertEquals(Act.UNKNOWN, Act.filter(Act.PING));
        assertEquals(Act.UNKNOWN, Act.filter(Act.PONG));

        byte a1 = (byte) ThreadLocalRandom.current().nextInt(7, Byte.MAX_VALUE + 1);

        System.out.println(a1);
        System.out.println(Act.UNKNOWN);

        assertEquals(Act.UNKNOWN, Act.filter(a1));
    }
}
