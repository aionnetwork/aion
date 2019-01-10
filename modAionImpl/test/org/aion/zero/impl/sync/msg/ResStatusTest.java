package org.aion.zero.impl.sync.msg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.Test;

/** @author chris */
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
        assertTrue(Arrays.equals(genesisHash, rs2.getGenesisHash()));
    }
}
