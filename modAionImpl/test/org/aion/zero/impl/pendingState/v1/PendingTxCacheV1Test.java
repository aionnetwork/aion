package org.aion.zero.impl.pendingState.v1;

import static org.aion.zero.impl.pendingState.v1.PendingTxCacheV1.ACCOUNT_CACHE_MAX;
import static org.aion.zero.impl.pendingState.v1.PendingTxCacheV1.TX_PER_ACCOUNT_MAX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.AddressUtils;
import org.junit.Before;
import org.junit.Test;

public class PendingTxCacheV1Test {
    private static List<ECKey> key;

    @Before
    public void Setup() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);

        int keyCnt = 2_001;
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

        for (int i = startNonce; i < startNonce + num; i++) {

            AionTransaction tx =
                AionTransaction.create(
                    key.get(keyIndex),
                    BigInteger.valueOf(i).toByteArray(),
                    AddressUtils.wrapAddress(
                            "0000000000000000000000000000000000000000000000000000000000000001"),
                    ByteUtil.hexStringToBytes("1"),
                    ByteUtil.hexStringToBytes("1"),
                    10000L,
                    1L,
                    TransactionTypes.DEFAULT,
                    null);

            txn.add(tx);
        }

        return txn;
    }

    @Test
    public void addCacheTxTest() {

        PendingTxCacheV1 cache = new PendingTxCacheV1();

        List<AionTransaction> txn = getMockTransaction(0, 10, 0);
        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertEquals(txn.size(), cache.cacheTxSize());
    }

    @Test
    public void addCacheTxWith2SendersTest() {

        PendingTxCacheV1 cache = new PendingTxCacheV1();
        List<AionTransaction> txn = getMockTransaction(0, 10, 0);
        txn.addAll(getMockTransaction(0, 10, 1));

        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertEquals(txn.size(), cache.cacheTxSize());
        assertEquals(10, cache.getCacheTxBySender(new AionAddress(key.get(0).getAddress())).size());
        assertEquals(10, cache.getCacheTxBySender(new AionAddress(key.get(1).getAddress())).size());
    }

    @Test
    public void addCacheTxReachAccountCacheMaxTest() {

        PendingTxCacheV1 cache = new PendingTxCacheV1();

        List<AionTransaction> txn = new ArrayList<>();
        for (int i = 0; i < key.size(); i++) {
            txn.add(getMockTransaction(0, 1, i).get(0));
        }

        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertEquals(ACCOUNT_CACHE_MAX, cache.getCacheTxAccount().size());
    }

    @Test
    public void addTxReachAccountCacheMaxTest() {

        PendingTxCacheV1 cache = new PendingTxCacheV1();
        List<AionTransaction> txn = getMockTransaction(0, 500, 0);
        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertEquals(1, cache.getCacheTxAccount().size());
        assertEquals(500, cache.cacheTxSize());
        assertEquals(500, cache.getCacheTxBySender(new AionAddress(key.get(0).getAddress())).size());

        txn = getMockTransaction(500, 1, 0);
        assertEquals(1, txn.size());
        cache.addCacheTx(txn.get(0));

        assertEquals(500, cache.cacheTxSize());
        assertEquals(500, cache.getCacheTxBySender(new AionAddress(key.get(0).getAddress())).size());
    }

    @Test
    public void addCacheTxWithDuplicateNonceTransactionsTest() {

        PendingTxCacheV1 cache = new PendingTxCacheV1();
        List<AionTransaction> txn = getMockTransaction(0, 10, 0);
        txn.addAll(getMockTransaction(5, 10, 0));

        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertEquals(15, cache.cacheTxSize());

        List<AionTransaction> cachedTxs =
                new ArrayList<>(cache.getCacheTxBySender(new AionAddress(key.get(0).getAddress())).values());
        assertEquals(15, cachedTxs.size());
    }

    @Test
    public void flush2TxInOneAccountTest() {

        PendingTxCacheV1 cache = new PendingTxCacheV1();

        List<AionTransaction> txn = getMockTransaction(0, 10, 0);
        List<AionTransaction> newCache;
        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertEquals(10, cache.cacheTxSize());

        Map<AionAddress, BigInteger> map = new HashMap<>();
        map.put(new AionAddress(key.get(0).getAddress()), BigInteger.TWO);

        newCache = cache.removeSealedTransactions(map);
        assertEquals(2, newCache.size());

        List<AionTransaction> cachedTxs =
                new ArrayList<>(cache.getCacheTxBySender(new AionAddress(key.get(0).getAddress())).values());
        assertEquals(8, cachedTxs.size());
    }

    @Test
    public void flushTxWithOtherAccountTest() {

        PendingTxCacheV1 cache = new PendingTxCacheV1();

        List<AionTransaction> txn = getMockTransaction(0, 10, 0);

        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertEquals(10, cache.cacheTxSize());

        Map<AionAddress, BigInteger> map = new HashMap<>();
        map.put(new AionAddress(key.get(1).getAddress()), BigInteger.TWO);
        List<AionTransaction> flushedTx = cache.removeSealedTransactions(map);
        assertEquals(0, flushedTx.size());

        List<AionTransaction> cachedTxs =
                new ArrayList<>(cache.getCacheTxBySender(new AionAddress(key.get(0).getAddress())).values());
        assertEquals(10, cachedTxs.size());
    }

    @Test
    public void flushTxWith2AccountsTest() {

        PendingTxCacheV1 cache = new PendingTxCacheV1();

        List<AionTransaction> txn = getMockTransaction(0, 10, 0);
        txn.addAll(getMockTransaction(0, 10, 1));

        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertEquals(20, cache.cacheTxSize());

        Map<AionAddress, BigInteger> map = new HashMap<>();
        map.put(new AionAddress(key.get(0).getAddress()), BigInteger.TWO);
        map.put(new AionAddress(key.get(1).getAddress()), BigInteger.ONE);
        cache.removeSealedTransactions(map);

        List<AionTransaction> cachedTxs =
                new ArrayList<>(cache.getCacheTxBySender(new AionAddress(key.get(0).getAddress())).values());
        assertEquals(8, cachedTxs.size());

        cachedTxs = new ArrayList<>(cache.getCacheTxBySender(new AionAddress(key.get(1).getAddress())).values());
        assertEquals(9, cachedTxs.size());
    }

    @Test
    public void fullFlush2SendersUnderFullCachedInstanceTest() {

        PendingTxCacheV1 cache = new PendingTxCacheV1();

        List<AionTransaction> txn = new ArrayList<>();
        for (int i = 0; i < ACCOUNT_CACHE_MAX; i++) {
            List<AionTransaction> newTx = getMockTransaction(0, TX_PER_ACCOUNT_MAX, i);
            txn.addAll(newTx);
        }

        assertEquals(ACCOUNT_CACHE_MAX * TX_PER_ACCOUNT_MAX, txn.size());

        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertEquals(ACCOUNT_CACHE_MAX * TX_PER_ACCOUNT_MAX, cache.cacheTxSize());

        Map<AionAddress, BigInteger> map = new HashMap<>();
        map.put(new AionAddress(key.get(0).getAddress()), BigInteger.valueOf(TX_PER_ACCOUNT_MAX));
        map.put(new AionAddress(key.get(1).getAddress()), BigInteger.valueOf(TX_PER_ACCOUNT_MAX));
        List<AionTransaction> flushedTx = cache.removeSealedTransactions(map);
        assertEquals(TX_PER_ACCOUNT_MAX * 2, flushedTx.size());
        assertEquals(ACCOUNT_CACHE_MAX * TX_PER_ACCOUNT_MAX - TX_PER_ACCOUNT_MAX * 2, cache.cacheTxSize());

        for (int i = 0; i < ACCOUNT_CACHE_MAX; i++) {
            if (i == 0 || i == 1) {
                assertNull(cache.getCacheTxBySender(new AionAddress(key.get(i).getAddress())));
            } else {
                assertEquals(TX_PER_ACCOUNT_MAX, cache.getCacheTxBySender(new AionAddress(key.get(i).getAddress())).size());
            }
        }
    }

    @Test
    public void getRemovedTxHashWithoutPoolBackupTest() {
        PendingTxCacheV1 cache = new PendingTxCacheV1();
        assertNotNull(cache.pollRemovedTransactionForPoolBackup());
    }

    @Test
    public void getRemovedTxHashWithPoolBackupTest() {
        PendingTxCacheV1 cache = new PendingTxCacheV1(true);

        List<AionTransaction> txn = getMockTransaction(0, 10, 0);
        List<AionTransaction> newCache;
        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertEquals(10, cache.cacheTxSize());

        Map<AionAddress, BigInteger> map = new HashMap<>();
        map.put(new AionAddress(key.get(0).getAddress()), BigInteger.TWO);

        newCache = cache.removeSealedTransactions(map);
        assertEquals(2, newCache.size());
        assertEquals(0, newCache.get(0).getNonceBI().longValue());
        assertEquals(1, newCache.get(1).getNonceBI().longValue());

        List<AionTransaction> cachedTxs =
                new ArrayList<>(cache.getCacheTxBySender(new AionAddress(key.get(0).getAddress())).values());
        assertEquals(8, cachedTxs.size());

        List<AionTransaction> removedTxHash = cache.pollRemovedTransactionForPoolBackup();
        assertEquals(2, removedTxHash.size());
        for (int i = 0; i < removedTxHash.size(); i++) {
            assertEquals(newCache.get(i), removedTxHash.get(i));
        }
    }

    @Test
    public void clearRemovedTxHashForPoolBackupTest() {
        PendingTxCacheV1 cache = new PendingTxCacheV1(true);

        List<AionTransaction> txn = getMockTransaction(0, 10, 0);
        List<AionTransaction> newCache;
        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertEquals(10, cache.cacheTxSize());

        Map<AionAddress, BigInteger> map = new HashMap<>();
        map.put(new AionAddress(key.get(0).getAddress()), BigInteger.TWO);

        newCache = cache.removeSealedTransactions(map);
        assertEquals(2, newCache.size());

        List<AionTransaction> cachedTxs =
                new ArrayList<>(cache.getCacheTxBySender(new AionAddress(key.get(0).getAddress())).values());
        assertEquals(8, cachedTxs.size());

        List<AionTransaction> removedTxHash = cache.pollRemovedTransactionForPoolBackup();
        assertEquals(2, removedTxHash.size());
        for (int i = 0; i < removedTxHash.size(); i++) {
            assertEquals(newCache.get(i), removedTxHash.get(i));
        }

        assertEquals(0, cache.pollRemovedTransactionForPoolBackup().size());
    }

    @Test
    public void benchmark() {
        PendingTxCacheV1 cache = new PendingTxCacheV1();

        System.out.println("Gen 1M txs");
        List<AionTransaction> txn = new ArrayList<>();
        for (int i = 0; i < ACCOUNT_CACHE_MAX; i++) {
            List<AionTransaction> newTx = getMockTransaction(0, TX_PER_ACCOUNT_MAX, i);
            txn.addAll(newTx);
        }

        assertEquals(ACCOUNT_CACHE_MAX * TX_PER_ACCOUNT_MAX, txn.size());

        System.out.println("adding 1M txs to cache");

        long t1 = System.currentTimeMillis();
        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }
        long t2 = System.currentTimeMillis() - t1;
        System.out.println("add 1M txs took " + t2 + " ms cacheSize: " + cache.cacheTxSize());
        assertEquals(ACCOUNT_CACHE_MAX * TX_PER_ACCOUNT_MAX, cache.cacheTxSize());

        System.out.println("flush starting");
        int remove = 5;

        Map<AionAddress, BigInteger> flushMap = new HashMap<>();
        flushMap.put(new AionAddress(key.get(0).getAddress()), BigInteger.valueOf(remove));
        flushMap.put(new AionAddress(key.get(1).getAddress()), BigInteger.valueOf(remove));

        t1 = System.currentTimeMillis();
        cache.removeSealedTransactions(flushMap);
        t2 = System.currentTimeMillis() - t1;
        System.out.println("flush took " + t2 + " ms");

        List<AionTransaction> cachedTxs =
                new ArrayList<>(cache.getCacheTxBySender(new AionAddress(key.get(0).getAddress())).values());
        assertEquals(TX_PER_ACCOUNT_MAX - remove, cachedTxs.size());

        cachedTxs = new ArrayList<>(cache.getCacheTxBySender(new AionAddress(key.get(1).getAddress())).values());
        assertEquals(TX_PER_ACCOUNT_MAX - remove, cachedTxs.size());
        assertEquals(ACCOUNT_CACHE_MAX * TX_PER_ACCOUNT_MAX - remove * 2, cache.cacheTxSize());
    }

    @Test
    public void isInCacheTest() {
        PendingTxCacheV1 cache = new PendingTxCacheV1();

        List<AionTransaction> txn = getMockTransaction(0, 1, 0);
        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertEquals(txn.size(), cache.cacheTxSize());
        assertThat(cache.isInCache(new AionAddress(key.get(1).getAddress()), BigInteger.ZERO)).isFalse();
        assertThat(cache.isInCache(new AionAddress(key.get(0).getAddress()), BigInteger.ONE)).isFalse();
        assertThat(cache.isInCache(new AionAddress(key.get(0).getAddress()), BigInteger.ZERO)).isTrue();
    }

    @Test
    public void getNewPendingTransactionTest() {
        PendingTxCacheV1 cache = new PendingTxCacheV1();

        List<AionTransaction> txn = getMockTransaction(0, 5, 0);
        List<AionTransaction> txn2 = getMockTransaction(0, 5, 1);
        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        for (AionTransaction tx : txn2) {
            cache.addCacheTx(tx);
        }

        assertEquals(10, cache.cacheTxSize());
        assertThat(cache.getNewPendingTransactions(new HashMap<>()).isEmpty()).isTrue();
        Map<AionAddress, BigInteger> testMap = new HashMap<>();

        testMap.put(new AionAddress(key.get(0).getAddress()), BigInteger.ZERO);
        assertEquals(5, cache.getNewPendingTransactions(testMap).size());

        testMap.put(new AionAddress(key.get(1).getAddress()), BigInteger.ZERO);
        assertEquals(10, cache.getNewPendingTransactions(testMap).size());

        testMap.put(new AionAddress(key.get(0).getAddress()), BigInteger.TWO);
        assertEquals(8, cache.getNewPendingTransactions(testMap).size());

        testMap.put(new AionAddress(key.get(1).getAddress()), BigInteger.TWO);
        assertEquals(6, cache.getNewPendingTransactions(testMap).size());
    }

    @Test
    public void removeTransactionTest() {
        PendingTxCacheV1 cache = new PendingTxCacheV1();

        List<AionTransaction> txn = getMockTransaction(0, 2, 0);
        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertEquals(2, cache.cacheTxSize());

        cache.removeTransaction(new AionAddress(key.get(0).getAddress()), BigInteger.ZERO);
        assertEquals(1, cache.cacheTxSize());
        assertEquals(1, cache.getCacheTxAccount().size());

        cache.removeTransaction(new AionAddress(key.get(0).getAddress()), BigInteger.ZERO);
        assertEquals(1, cache.cacheTxSize());

        cache.removeTransaction(new AionAddress(key.get(1).getAddress()), BigInteger.ZERO);
        assertEquals(1, cache.cacheTxSize());
        assertEquals(1, cache.getCacheTxAccount().size());

        cache.removeTransaction(new AionAddress(key.get(0).getAddress()), BigInteger.ONE);
        assertEquals(0, cache.cacheTxSize());
        assertEquals(0, cache.getCacheTxAccount().size());

    }

    @Test
    public void flushTimeoutTransactionTest() {
        PendingTxCacheV1 cache = new PendingTxCacheV1();

        List<AionTransaction> txn = getMockTransaction(0, 2, 0);
        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertEquals(2, cache.cacheTxSize());

        List<AionTransaction> flushedTx = cache.flushTimeoutTxForTest();
        assertEquals(2, flushedTx.size());
        assertEquals(0, cache.cacheTxSize());
    }
}
