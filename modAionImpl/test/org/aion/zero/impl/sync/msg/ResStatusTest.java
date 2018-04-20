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

import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author chris
 */
public class ResStatusTest {

    @Test
    public void test() {

        long bestBlockNumber = ThreadLocalRandom.current().nextLong();
        byte[] totalDifficulty = new byte[Byte.MAX_VALUE];
        ThreadLocalRandom.current().nextBytes(totalDifficulty);
        byte[] bestBlockHash = new byte[32];
        ThreadLocalRandom.current().nextBytes(bestBlockHash);
        byte[] genesisHash = new byte[32];

        ResStatus rs1 = new ResStatus(bestBlockNumber, totalDifficulty, bestBlockHash, genesisHash);
        ResStatus rs2 = ResStatus.decode(rs1.encode());

        assertEquals(bestBlockNumber, rs2.getBestBlockNumber());
        assertTrue(Arrays.equals(totalDifficulty, rs2.getTotalDifficulty()));
        assertTrue(Arrays.equals(bestBlockHash, rs2.getBestHash()));
    }

}
