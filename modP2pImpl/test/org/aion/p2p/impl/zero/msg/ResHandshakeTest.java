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

package org.aion.p2p.impl.zero.msg;

import static org.junit.Assert.*;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;
import org.junit.Test;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author chris
 */
public class ResHandshakeTest {

    @Test
    public void testRoute() {
        ResHandshake mh1 = new ResHandshake(true);
        assertEquals(Ver.V0, mh1.getHeader().getVer());
        assertEquals(Ctrl.NET, mh1.getHeader().getCtrl());
        assertEquals(Act.RES_HANDSHAKE, mh1.getHeader().getAction());
    }

    @Test
    public void testEncodeDecode() {

        ResHandshake mh1 = new ResHandshake(ThreadLocalRandom.current().nextBoolean());
        byte[] mhBytes = mh1.encode();
        ResHandshake mh2 = ResHandshake.decode(mhBytes);
        assertEquals(mh1.getSuccess(), mh2.getSuccess());

    }

}