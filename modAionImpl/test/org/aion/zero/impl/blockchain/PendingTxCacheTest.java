package org.aion.zero.impl.blockchain;

import org.aion.base.type.Address;
import org.aion.base.type.ITransaction;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.zero.types.AionTransaction;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class PendingTxCacheTest {
    private static List<ECKey> key;

    @Before
    public void Setup() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);

        int keyCnt = 100_001;
        System.out.println("gen key list----------------");
        if (key == null) {
            key = new ArrayList<>();
            for (int i = 0; i < keyCnt; i++) {
                key.add(ECKeyFac.inst().create());
            }
        }
        System.out.println("gen key list finished-------");
    }

    private List<AionTransaction> getMockTransaction(int startNonce, int num, int keyIndex) {

        List<AionTransaction> txn = new ArrayList<>();

        for (int i=startNonce ; i < startNonce+num ; i++) {

            AionTransaction tx = new AionTransaction(BigInteger.valueOf(i).toByteArray(), Address.wrap(key.get(keyIndex).getAddress()),
                    Address.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
                    ByteUtils.fromHexString("1"), ByteUtils.fromHexString("1"), 10000L, 1L);

            tx.sign(key.get(keyIndex));
            txn.add(tx);
        }

        return txn;
    }

    private List<AionTransaction> getMockBigTransaction(int startNonce, int num, int keyIndex, int size) {

        List<AionTransaction> txn = new ArrayList<>();

        String data = "";
        for (int i=0 ; i< size ; i++) {
            data += "1";
        }

        for (int i=startNonce ; i < startNonce+num ; i++) {

            AionTransaction tx = new AionTransaction(BigInteger.valueOf(i).toByteArray(), Address.wrap(key.get(keyIndex).getAddress()),
                    Address.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
                    ByteUtils.fromHexString("1"),
                    ByteUtils.fromHexString(data),
                    10000L, 1L);

            tx.sign(key.get(keyIndex));
            txn.add(tx);
        }

        return txn;
    }

    @Test
    public void addCacheTxTest() {

        PendingTxCache cache = new PendingTxCache(1);

        List<AionTransaction> txn = getMockTransaction(0, 10, 0);
        List<AionTransaction> newCache = new ArrayList<>();
        for (ITransaction tx : txn) {
            newCache.add(cache.addCacheTx((AionTransaction) tx).get(0));
        }

        assertTrue(newCache.size() == txn.size());
    }

    @Test
    public void addCacheTxTest2() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 10, 0);
        txn.addAll(getMockTransaction(0, 10, 1));

        List<AionTransaction> newCache = new ArrayList<>();
        for (ITransaction tx : txn) {
            newCache.add(cache.addCacheTx((AionTransaction) tx).get(0));
        }

        assertTrue(newCache.size() == txn.size());
    }

    @Test
    public void addCacheTxTest3() {

        PendingTxCache cache = new PendingTxCache(1000);

        List<AionTransaction> txn = new ArrayList<>();
        for (int i=0 ; i<key.size() ; i++) {
            txn.add(getMockTransaction(0, 1, i).get(0));
        }

        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertTrue(cache.getCacheTxAccount().size() == key.size() - 1);
    }

    @Test
    public void addCacheTxTest4() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 10, 0);
        txn.addAll(getMockTransaction(5, 10, 0));

        List<AionTransaction> newCache = new ArrayList<>();
        for (ITransaction tx : txn) {
            newCache.add(cache.addCacheTx((AionTransaction) tx).get(0));
        }

        assertTrue(newCache.size() == 20);

        Map<BigInteger,AionTransaction> cacheMap = cache.geCacheTx(Address.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 15);
    }

    @Test
    public void flushTest1() {

        PendingTxCache cache = new PendingTxCache(1);

        List<AionTransaction> txn = getMockTransaction(0, 10, 0);

        List<AionTransaction> newCache = new ArrayList<>();
        for (ITransaction tx : txn) {
            newCache.add(cache.addCacheTx((AionTransaction) tx).get(0));
        }

        assertTrue(newCache.size() == 10);

        Map<Address, BigInteger> map = new HashMap<>();
        map.put(Address.wrap(key.get(0).getAddress()), BigInteger.TWO);

        newCache = cache.flush(map);
        assertTrue(newCache.size() == 1);

        Map<BigInteger,AionTransaction> cacheMap = cache.geCacheTx(Address.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 8);
    }

    @Test
    public void flushTest2() {

        PendingTxCache cache = new PendingTxCache(1);

        List<AionTransaction> txn = getMockTransaction(0, 10, 0);

        List<AionTransaction> newCache = new ArrayList<>();
        for (ITransaction tx : txn) {
            newCache.add(cache.addCacheTx((AionTransaction) tx).get(0));
        }

        assertTrue(newCache.size() == 10);

        Map<Address, BigInteger> map = new HashMap<>();
        map.put(Address.wrap(key.get(1).getAddress()), BigInteger.TWO);
        cache.flush(map);

        Map<BigInteger,AionTransaction> cacheMap = cache.geCacheTx(Address.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 10);
    }

    @Test
    public void maxPendingSizeTest1() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 680, 0);

        List<AionTransaction> newCache = new ArrayList<>();
        for (ITransaction tx : txn) {
            newCache.add(cache.addCacheTx((AionTransaction) tx).get(0));
        }

        Map<BigInteger,AionTransaction> cacheMap = cache.geCacheTx(Address.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 659);
    }

    @Test
    public void maxPendingSizeTest2() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 659, 0);

        List<AionTransaction> newCache = new ArrayList<>();
        for (ITransaction tx : txn) {
            newCache.add(cache.addCacheTx((AionTransaction) tx).get(0));
        }

        assertTrue(newCache.size() == 659);
        AionTransaction tx = getMockTransaction(50, 1, 0).get(0);

        newCache = cache.addCacheTx(tx);
        assertTrue(newCache.size() == 659);

        Map<BigInteger,AionTransaction> cacheMap = cache.geCacheTx(Address.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 659);
    }


    @Test
    public void maxPendingSizeTest3() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 659, 0);

        List<AionTransaction> newCache = new ArrayList<>();
        for (AionTransaction tx : txn) {
            newCache.add(cache.addCacheTx( tx).get(0));
        }

        assertTrue(newCache.size() == 659);
        AionTransaction tx = getMockBigTransaction(50, 1, 0, 200).get(0);

        newCache = cache.addCacheTx(tx);
        assertTrue(newCache.size() == 658);

        Map<BigInteger,AionTransaction> cacheMap = cache.geCacheTx(Address.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 658);
    }

    @Test
    public void maxPendingSizeTest4() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 659, 0);

        List<AionTransaction> newCache = new ArrayList<>();
        for (AionTransaction tx : txn) {
            newCache.add(cache.addCacheTx(tx).get(0));
        }

        assertTrue(newCache.size() == 659);
        AionTransaction tx = getMockBigTransaction(50, 1, 0, 2000).get(0);

        newCache = cache.addCacheTx(tx);
        assertTrue(newCache.size() == 652);

        Map<BigInteger,AionTransaction> cacheMap = cache.geCacheTx(Address.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 652);
    }

    @Test
    public void maxPendingSizeTest5() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 100, 0);
        txn.addAll(getMockTransaction(101, 600, 0));

        List<AionTransaction> newCache = new ArrayList<>();
        for (AionTransaction tx : txn) {
            newCache.add(cache.addCacheTx(tx).get(0));
        }

        assertTrue(newCache.size() == 700);

        Map<BigInteger,AionTransaction> cacheMap = cache.geCacheTx(Address.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 659);

        AionTransaction tx = getMockBigTransaction(100, 1, 0, 2000).get(0);

        newCache = cache.addCacheTx(tx);
        assertTrue(newCache.size() == 652);
    }

    @Test
    public void maxPendingSizeTest6() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 100, 0);
        txn.addAll(getMockTransaction(101, 600, 0));

        List<AionTransaction> newCache = new ArrayList<>();
        for (AionTransaction tx : txn) {
            newCache.add(cache.addCacheTx(tx).get(0));
        }

        assertTrue(newCache.size() == 700);

        Map<BigInteger,AionTransaction> cacheMap = cache.geCacheTx(Address.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 659);

        AionTransaction tx = getMockBigTransaction(100, 1, 0, 199_500).get(0);

        newCache = cache.addCacheTx(tx);
        assertTrue(newCache.size() == 659);
    }

    @Test
    public void maxPendingSizeTest7() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 659, 0);

        List<AionTransaction> newCache = new ArrayList<>();
        for (AionTransaction tx : txn) {
            newCache.add(cache.addCacheTx(tx).get(0));
        }

        assertTrue(newCache.size() == 659);

        Map<BigInteger,AionTransaction> cacheMap = cache.geCacheTx(Address.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 659);

        AionTransaction tx = getMockBigTransaction(100, 1, 0, 199_500).get(0);

        newCache = cache.addCacheTx(tx);
        assertTrue(newCache.size() == 659);
    }
}
