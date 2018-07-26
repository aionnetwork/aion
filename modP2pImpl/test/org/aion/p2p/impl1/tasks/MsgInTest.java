/*
 * Copyright (c) 2017-2018 Aion foundation.
 *      This file is part of the aion network project.
 *
 *      The aion network project is free software: you can redistribute it
 *      and/or modify it under the terms of the GNU General Public License
 *      as published by the Free Software Foundation, either version 3 of
 *      the License, or any later version.
 *
 *      The aion network project is distributed in the hope that it will
 *      be useful, but WITHOUT ANY WARRANTY; without even the implied
 *      warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *      See the GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with the aion network project source files.
 *      If not, see <https://www.gnu.org/licenses/>.
 *
 *  Contributors:
 *      Aion foundation.
 */

package org.aion.p2p.impl1.tasks;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.UUID;
import org.aion.p2p.P2pConstant;
import org.junit.Test;

public class MsgInTest {

    Random r = new Random();

    @Test
    public void TestGetNodeId() {
        int id = r.nextInt();
        MsgIn msg = new MsgIn(id, "", 1, new byte[0]);
        assertEquals(id, msg.getNodeId());
    }

    @Test
    public void TestGetDisPlayId() {
        String dsp = UUID.randomUUID().toString();
        MsgIn msg = new MsgIn(1, dsp, 1, new byte[0]);
        assertEquals(dsp, msg.getDisplayId());
    }

    @Test
    public void TestGetRoute() {
        int rut = r.nextInt();
        MsgIn msg = new MsgIn(1, "", rut, new byte[0]);
        assertEquals(rut, msg.getRoute());
    }

    @Test
    public void TestGetMsg() {
        int len = r.nextInt(P2pConstant.MAX_BODY_SIZE);
        MsgIn msg = new MsgIn(1, "", 1, new byte[len]);
        assertEquals(len, msg.getMsg().length);
    }
}
