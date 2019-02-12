package org.aion.zero.impl.sync.msg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.Test;

/** @author chris */
public class ResStatusTest {

    @Test
    public void testNew() {

        long bestBlockNumber = ThreadLocalRandom.current().nextLong();
        byte[] totalDifficulty = new byte[Byte.MAX_VALUE];
        ThreadLocalRandom.current().nextBytes(totalDifficulty);
        byte[] bestBlockHash = new byte[32];
        ThreadLocalRandom.current().nextBytes(bestBlockHash);
        byte[] genesisHash = new byte[32];
        byte apiVersion = (byte) ThreadLocalRandom.current().nextInt();
        short peerCount = (short) ThreadLocalRandom.current().nextInt();
        byte[] pendingTxCount = new byte[Byte.MAX_VALUE];
        ThreadLocalRandom.current().nextBytes(pendingTxCount);
        int latency = ThreadLocalRandom.current().nextInt();

        ResStatus rs1 =
                new ResStatus(
                        bestBlockNumber,
                        totalDifficulty,
                        bestBlockHash,
                        genesisHash,
                        apiVersion,
                        peerCount,
                        pendingTxCount,
                        latency);
        ResStatus rs2 = ResStatus.decode(rs1.encode());

        assertEquals(bestBlockNumber, rs2.getBestBlockNumber());
        assertTrue(Arrays.equals(totalDifficulty, rs2.getTotalDifficulty()));
        assertTrue(Arrays.equals(bestBlockHash, rs2.getBestHash()));
        assertTrue(Arrays.equals(genesisHash, rs2.getGenesisHash()));
        assertEquals(apiVersion, rs2.getApiVersion());
        assertEquals(peerCount, rs2.getPeerCount());
        assertTrue(Arrays.equals(pendingTxCount, rs2.getPendingTxCount()));
        assertEquals(latency, rs2.getLatency());
    }

    @Test
    public void testOld() {

        long bestBlockNumber = ThreadLocalRandom.current().nextLong();
        byte[] totalDifficulty = new byte[Byte.MAX_VALUE];
        ThreadLocalRandom.current().nextBytes(totalDifficulty);
        byte[] bestBlockHash = new byte[32];
        ThreadLocalRandom.current().nextBytes(bestBlockHash);
        byte[] genesisHash = new byte[32];

        int _len = 8 + 1 + totalDifficulty.length + 32 + 32;
        ByteBuffer bb = ByteBuffer.allocate(_len);
        bb.putLong(bestBlockNumber);
        bb.put((byte) totalDifficulty.length);
        bb.put(totalDifficulty);
        bb.put(bestBlockHash);
        bb.put(genesisHash);
        ResStatus rs2 = ResStatus.decode(bb.array());

        assertEquals(bestBlockNumber, rs2.getBestBlockNumber());
        assertTrue(Arrays.equals(totalDifficulty, rs2.getTotalDifficulty()));
        assertTrue(Arrays.equals(bestBlockHash, rs2.getBestHash()));
        assertTrue(Arrays.equals(genesisHash, rs2.getGenesisHash()));
        assertEquals(0, rs2.getApiVersion());
        assertEquals(0, rs2.getPeerCount());
        assertTrue(Arrays.equals(new byte[0], rs2.getPendingTxCount()));
        assertEquals(0, rs2.getLatency());
    }
}
