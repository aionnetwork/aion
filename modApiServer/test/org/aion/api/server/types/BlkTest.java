package org.aion.api.server.types;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import org.aion.crypto.HashUtil;
import org.aion.types.AionAddress;
import org.aion.util.string.StringUtils;
import org.aion.zero.impl.core.BloomFilter;
import org.aion.zero.impl.types.MiningBlock;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class BlkTest {

    @Test
    public void aionBlockDetailsToJsonTest(){
        JSONObject nullObject = Blk.aionBlockDetailsToJson(null, Collections.emptyList(), null, null, null);
        assertNull(nullObject);
        byte[] emptyByteArray = new byte[32];
        Arrays.fill(emptyByteArray, (byte) 0);
        AionAddress coinBase = new AionAddress(Arrays.copyOf(emptyByteArray, 32));
        byte[] parentHash = HashUtil.h256("parent".getBytes());
        byte[] bloom = BloomFilter.create().getBloomFilterBytes();
        byte[] difficulty = BigInteger.TEN.toByteArray();
        long blockNumber = 1;
        long timestamp = System.currentTimeMillis();
        byte[] extraData = new byte[0];
        byte[] nonce = BigInteger.valueOf(100).toByteArray();
        byte[] receiptsRoot = Arrays.copyOf(emptyByteArray, 32);
        byte[] transactionsRoot = Arrays.copyOf(emptyByteArray, 32);
        byte[] stateRoot = Arrays.copyOf(emptyByteArray, 32);
        byte[] solution = new byte[256];
        long nrgConsumed = 0L;
        long nrgLimit = 0;
        BigInteger totalDifficulty = BigInteger.valueOf(20);
        MiningBlock block = new MiningBlock(parentHash,
            coinBase,
            bloom,
            difficulty,
            blockNumber,
            timestamp,
            extraData,
            nonce,
            receiptsRoot,
            transactionsRoot,
            stateRoot,
            Collections.emptyList(),
            solution,
            nrgConsumed,
            nrgLimit);
        byte[] hash = block.getHash();

        JSONObject blkObject = Blk.aionBlockDetailsToJson(block,
            Collections.emptyList(), timestamp - 10, totalDifficulty, BigInteger.valueOf(1).pow(18));

        assertEquals(blkObject.get("miner"), StringUtils.toJsonHex(coinBase.toString()));
        assertEquals(blkObject.get("number"), blockNumber);
        assertEquals(blkObject.get("logsBloom"), StringUtils.toJsonHex(block.getLogBloom()));
        assertEquals(blkObject.get("stateRoot"), StringUtils.toJsonHex(stateRoot));
        assertEquals(blkObject.get("blockTime"), 10L);
        assertEquals(blkObject.get("timestamp"), StringUtils.toJsonHex(timestamp));
        assertEquals(blkObject.get("hash"), StringUtils.toJsonHex(hash));
        assertEquals(blkObject.get("parentHash"), StringUtils.toJsonHex(parentHash));
        assertEquals(blkObject.get("receiptsRoot"), StringUtils.toJsonHex(receiptsRoot));
        assertEquals(blkObject.get("difficulty"), StringUtils.toJsonHex(difficulty));
        assertEquals(blkObject.get("totalDifficulty"), StringUtils.toJsonHex(totalDifficulty));
        assertEquals(blkObject.get("nonce"), StringUtils.toJsonHex(nonce));
        assertEquals(blkObject.get("solution"), StringUtils.toJsonHex(solution));
        assertEquals(blkObject.get("gasUsed"), StringUtils.toJsonHex(block.getHeader().getEnergyConsumed()));
        assertEquals(blkObject.get("gasLimit"), StringUtils.toJsonHex(block.getHeader().getEnergyLimit()));
        assertEquals(blkObject.get("nrgUsed"), StringUtils.toJsonHex(block.getHeader().getEnergyConsumed()));
        assertEquals(blkObject.get("nrgLimit"), StringUtils.toJsonHex(block.getHeader().getEnergyLimit()));
        assertEquals(blkObject.get("extraData"), StringUtils.toJsonHex(block.getExtraData()));
        assertEquals(blkObject.get("size"), block.size());
        assertEquals(blkObject.get("numTransactions"), 0);
        assertEquals(blkObject.get("txTrieRoot"), StringUtils.toJsonHex(block.getTxTrieRoot()));
        assertEquals(blkObject.get("blockReward"), new NumericalValue(BigInteger.valueOf(1).pow(18)).toHexString());
        JSONArray array = (JSONArray) blkObject.get("transactions");
        assertTrue(array.isEmpty());

        block = new MiningBlock(parentHash,
            coinBase,
            bloom,
            difficulty,
            0,
            timestamp,
            extraData,
            nonce,
            receiptsRoot,
            transactionsRoot,
            stateRoot,
            Collections.emptyList(),
            solution,
            nrgConsumed,
            nrgLimit);

        blkObject = Blk.aionBlockDetailsToJson(block,
            Collections.emptyList(), null, totalDifficulty, BigInteger.valueOf(1).pow(18));
        assertEquals(blkObject.get("blockTime"), 0);

        block = new MiningBlock(parentHash,
            coinBase,
            bloom,
            difficulty,
            10,
            timestamp,
            extraData,
            nonce,
            receiptsRoot,
            transactionsRoot,
            stateRoot,
            Collections.emptyList(),
            solution,
            nrgConsumed,
            nrgLimit);

        blkObject = Blk.aionBlockDetailsToJson(block, Collections.emptyList(), null, totalDifficulty, BigInteger.valueOf(1).pow(18));
        assertEquals(blkObject.get("blockTime"), JSONObject.NULL);
    }

}
