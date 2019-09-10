package org.aion.api.server.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.crypto.HashUtil;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.string.StringUtils;
import org.aion.zero.impl.core.BloomFilter;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class TxTest {


    @Test
    public void testTxInfoToJsonDetails(){
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

        byte[] txNonce = ByteUtil.bigIntegerToBytes(BigInteger.ONE);
        AionAddress to = new AionAddress(emptyByteArray);
        AionAddress from = new AionAddress(emptyByteArray);
        byte[] value = ByteUtil.bigIntegerToBytes(BigInteger.TEN);
        byte[] data = Arrays.copyOf(emptyByteArray, 32);
        long txNrgLimit = 10L;
        long txNrgPrice = 10L;
        byte type = 0b1;
        long txNrgUsed = 5L;
        AionTransaction transaction = AionTransaction.createWithoutKey(txNonce,from, to, value, data, txNrgLimit, txNrgPrice, type);

        AionTxReceipt txReceipt = new AionTxReceipt();
        txReceipt.setTransaction(transaction);
        txReceipt.setNrgUsed(txNrgUsed);

        AionBlock block =
                new AionBlock(
                        parentHash,
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
                        List.of(transaction),
                        solution,
                        nrgConsumed,
                        nrgLimit);
        byte[] hash = block.getHash();

        JSONObject object = Tx.aionTxInfoToDetailsJSON(AionTxInfo.newInstance(txReceipt,hash,0), block);
        assertNotNull(object);
        assertEquals(ByteUtil.byteArrayToLong(txNonce), object.get("nonce"));
        assertEquals(StringUtils.toJsonHex(data), object.get("input"));
        assertEquals(StringUtils.toJsonHex(to.toByteArray()), object.get("to"));
        assertEquals(StringUtils.toJsonHex(from.toByteArray()), object.get("from"));
        assertEquals(txNrgLimit, object.get("nrg"));
        assertEquals(StringUtils.toJsonHex(value), object.get("value"));
        assertEquals(StringUtils.toJsonHex(txNrgPrice), object.get("nrgPrice"));
        assertEquals(new NumericalValue(transaction.getTimestamp()).toHexString(), object.get("timestamp"));
        assertEquals(StringUtils.toJsonHex(blockNumber), object.get("blockNumber"));
        assertEquals(StringUtils.toJsonHex(hash), object.get("blockHash"));
        assertEquals("", object.get("error"));
        assertEquals(new NumericalValue(value).toHexString(), object.get("value"));
        assertFalse(object.getBoolean("hasInternalTransactions"));
        assertTrue(object.getJSONArray("logs").isEmpty());
        assertEquals(new NumericalValue(txNrgUsed).toHexString(), object.get("nrgUsed") );
        assertEquals(new NumericalValue(txNrgUsed).toHexString(), object.get("gasUsed") );

        byte[] onesArray = new byte[32];
        byte[] twosArray = new byte[32];
        Arrays.fill(onesArray, (byte) 1);
        Arrays.fill(twosArray, (byte) 2);
        byte[] logData = new byte[32];
        Arrays.fill(logData, (byte) 7);
        List<byte[]> topics = List.of(onesArray, twosArray);
        Log txLog = Log.topicsAndData(to.toByteArray(), topics, logData);


        AionTransaction transactionWithLog = AionTransaction.createWithoutKey(txNonce,from, to, value, data, txNrgLimit, txNrgPrice, type);

        AionTxReceipt txReceiptWithLog = new AionTxReceipt();
        txReceiptWithLog.setLogs(List.of(txLog));
        txReceiptWithLog.setTransaction(transactionWithLog);
        txReceiptWithLog.setNrgUsed(txNrgUsed);

        AionBlock blockWithLog =
            new AionBlock(
                parentHash,
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
                List.of(transactionWithLog),
                solution,
                nrgConsumed,
                nrgLimit);
        byte[] hashWithLog = block.getHash();

        JSONObject jsonWithLogs = Tx.aionTxInfoToDetailsJSON(AionTxInfo.newInstance(txReceiptWithLog, hashWithLog, 0), blockWithLog);

        assertNotNull(jsonWithLogs);
        List<Object> jsonArray = jsonWithLogs.getJSONArray("logs").toList();

        for (Object log : jsonArray){
            assertEquals(StringUtils.toJsonHex(to.toByteArray()), ((Map)log).get("address"));
            assertEquals(StringUtils.toJsonHex(logData), ((Map)log).get("data"));
            ArrayList jsonTopics = (ArrayList) ((Map) log).get("topics");
            for (int i = 0; i <topics.size(); i++) {
                assertEquals(StringUtils.toJsonHex(topics.get(i)), jsonTopics.get(i));
            }
        }
    }
}
