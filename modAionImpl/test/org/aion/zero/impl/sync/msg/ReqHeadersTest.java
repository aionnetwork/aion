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
 * <ether.camp> team through the ethereumJ library.
 * Ether.Camp Inc. (US) team through Ethereum Harmony.
 * John Tromp through the Equihash solver.
 * Samuel Neves through the BLAKE2 implementation.
 * Zcash project team.
 * Bitcoinj team.
 *
 */

package org.aion.zero.impl.sync.msg;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author chris
 */
public class ReqHeadersTest {

    @Test
    public void test() {
        long from = 7571;
        int take = 192;
        
        ReqBlocksHeaders rh = new ReqBlocksHeaders(from, take);
        ReqBlocksHeaders rhNew = ReqBlocksHeaders.decode(rh.encode());
        assertEquals(from, rhNew.getFromBlock());
        assertEquals(take, rhNew.getTake());
    }

}
