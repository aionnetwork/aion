/*******************************************************************************
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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
package org.aion.base.util;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class ByteArrayWrapperTest {

    @Test
    public void testCollision() {
        java.util.HashMap<ByteArrayWrapper, Object> map = new java.util.HashMap<>();

        for (int i = 0; i < 2000; i++) {
            ByteBuffer buffer = ByteBuffer.allocate(20);
            buffer.putInt(i);

            map.put(new ByteArrayWrapper(buffer.array()), new Object());
        }

        int cnt1 = 0;
        for (ByteArrayWrapper k : map.keySet()) {
            if (map.get(k) == null) {
                System.out.println("1111 " + k);
                cnt1++;
            }
        }

        int cnt2 = 0;
        for (java.util.Map.Entry<ByteArrayWrapper, Object> e : map.entrySet()) {
            if (e.getValue() == null) {
                System.out.println("2222 " + e);
                cnt2++;
            }
        }

        assertEquals(0, cnt1);
        assertEquals(0, cnt2);
    }

}
