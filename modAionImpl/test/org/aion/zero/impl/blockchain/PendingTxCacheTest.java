/*
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
 * Contributors:
 *     Aion foundation.
 */

package org.aion.zero.impl.blockchain;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.base.type.AionAddress;
import org.aion.base.type.ITransaction;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.zero.types.AionTransaction;
import org.junit.Before;
import org.junit.Test;

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

        for (int i = startNonce; i < startNonce + num; i++) {

            AionTransaction tx =
                    new AionTransaction(
                            BigInteger.valueOf(i).toByteArray(),
                            AionAddress.wrap(key.get(keyIndex).getAddress()),
                            AionAddress.wrap(
                                    "0000000000000000000000000000000000000000000000000000000000000001"),
                            ByteUtil.hexStringToBytes("1"),
                            ByteUtil.hexStringToBytes("1"),
                            10000L,
                            1L);

            tx.sign(key.get(keyIndex));
            txn.add(tx);
        }

        return txn;
    }

    private List<AionTransaction> getMockBigTransaction(
            int startNonce, int num, int keyIndex, int size) {

        List<AionTransaction> txn = new ArrayList<>();

        String data = "";
        for (int i = 0; i < size; i++) {
            data += "1";
        }

        for (int i = startNonce; i < startNonce + num; i++) {

            AionTransaction tx =
                    new AionTransaction(
                            BigInteger.valueOf(i).toByteArray(),
                            AionAddress.wrap(key.get(keyIndex).getAddress()),
                            AionAddress.wrap(
                                    "0000000000000000000000000000000000000000000000000000000000000001"),
                            ByteUtil.hexStringToBytes("1"),
                            ByteUtil.hexStringToBytes(data),
                            10000L,
                            1L);

            tx.sign(key.get(keyIndex));
            txn.add(tx);
        }

        return txn;
    }

    @Test
    public void addCacheTxTest() {

        PendingTxCache cache = new PendingTxCache(1);

        List<AionTransaction> txn = getMockTransaction(0, 10, 0);
        for (ITransaction tx : txn) {
            cache.addCacheTx((AionTransaction) tx);
        }

        assertTrue(cache.cacheTxSize() == txn.size());
    }

    @Test
    public void addCacheTxTest2() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 10, 0);
        txn.addAll(getMockTransaction(0, 10, 1));

        for (ITransaction tx : txn) {
            cache.addCacheTx((AionTransaction) tx);
        }

        assertTrue(cache.cacheTxSize() == txn.size());
    }

    @Test
    public void addCacheTxTest3() {

        PendingTxCache cache = new PendingTxCache(1000);

        List<AionTransaction> txn = new ArrayList<>();
        for (int i = 0; i < key.size(); i++) {
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

        for (ITransaction tx : txn) {
            cache.addCacheTx((AionTransaction) tx);
        }

        assertTrue(cache.cacheTxSize() == 15);

        Map<BigInteger, AionTransaction> cacheMap =
                cache.getCacheTx(AionAddress.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 15);
    }

    @Test
    public void flushTest1() {

        PendingTxCache cache = new PendingTxCache(1);

        List<AionTransaction> txn = getMockTransaction(0, 10, 0);

        List<AionTransaction> newCache;
        for (ITransaction tx : txn) {
            cache.addCacheTx((AionTransaction) tx);
        }

        assertTrue(cache.cacheTxSize() == 10);

        Map<AionAddress, BigInteger> map = new HashMap<>();
        map.put(AionAddress.wrap(key.get(0).getAddress()), BigInteger.TWO);

        newCache = cache.flush(map);
        assertTrue(newCache.size() == 1);

        Map<BigInteger, AionTransaction> cacheMap =
                cache.getCacheTx(AionAddress.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 8);
    }

    @Test
    public void flushTest2() {

        PendingTxCache cache = new PendingTxCache(1);

        List<AionTransaction> txn = getMockTransaction(0, 10, 0);

        for (ITransaction tx : txn) {
            cache.addCacheTx((AionTransaction) tx);
        }

        assertTrue(cache.cacheTxSize() == 10);

        Map<AionAddress, BigInteger> map = new HashMap<>();
        map.put(AionAddress.wrap(key.get(1).getAddress()), BigInteger.TWO);
        cache.flush(map);

        Map<BigInteger, AionTransaction> cacheMap =
                cache.getCacheTx(AionAddress.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 10);
    }

    @Test
    public void flushTest3() {

        PendingTxCache cache = new PendingTxCache(1);

        List<AionTransaction> txn = getMockTransaction(0, 10, 0);

        int singleTxSize = txn.get(0).getEncoded().length;

        int txSize = 0;
        for (ITransaction tx : txn) {
            cache.addCacheTx((AionTransaction) tx);
            txSize += tx.getEncoded().length;
        }

        assertTrue(cache.cacheTxSize() == 10);

        Map<AionAddress, BigInteger> map = new HashMap<>();
        map.put(AionAddress.wrap(key.get(0).getAddress()), BigInteger.TWO);
        cache.flush(map);

        assertTrue(cache.cacheSize() == (txSize - (singleTxSize << 1)));

        Map<BigInteger, AionTransaction> cacheMap =
                cache.getCacheTx(AionAddress.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 8);
    }

    @Test
    public void flushTest4() {

        PendingTxCache cache = new PendingTxCache(1);

        List<AionTransaction> txn = getMockTransaction(0, 10, 0);
        txn.addAll(getMockTransaction(0, 10, 1));

        int singleTxSize = txn.get(0).getEncoded().length;

        int txSize = 0;
        for (ITransaction tx : txn) {
            cache.addCacheTx((AionTransaction) tx);
            txSize += tx.getEncoded().length;
        }

        assertTrue(cache.cacheTxSize() == 20);

        Map<AionAddress, BigInteger> map = new HashMap<>();
        map.put(AionAddress.wrap(key.get(0).getAddress()), BigInteger.TWO);
        map.put(AionAddress.wrap(key.get(1).getAddress()), BigInteger.TWO);
        cache.flush(map);

        assertTrue(cache.cacheSize() == (txSize - (singleTxSize << 2)));

        Map<BigInteger, AionTransaction> cacheMap =
                cache.getCacheTx(AionAddress.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 8);

        cacheMap = cache.getCacheTx(AionAddress.wrap(key.get(1).getAddress()));
        assertTrue(cacheMap.size() == 8);
    }

    @Test
    public void flushTest5() {

        PendingTxCache cache = new PendingTxCache(256 * 100_000);
        int input = 10000;

        List<AionTransaction> txn = getMockTransaction(0, input, 0);
        txn.addAll(getMockTransaction(0, input, 1));

        for (ITransaction tx : txn) {
            cache.addCacheTx((AionTransaction) tx);
        }

        assertTrue(cache.cacheTxSize() == 20000);

        Map<AionAddress, BigInteger> map = new HashMap<>();
        map.put(AionAddress.wrap(key.get(0).getAddress()), BigInteger.valueOf(input + 1));
        map.put(AionAddress.wrap(key.get(1).getAddress()), BigInteger.valueOf(input + 1));
        cache.flush(map);

        assertTrue(cache.cacheSize() == 0);

        Map<BigInteger, AionTransaction> cacheMap =
                cache.getCacheTx(AionAddress.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 0);

        cacheMap = cache.getCacheTx(AionAddress.wrap(key.get(1).getAddress()));
        assertTrue(cacheMap.size() == 0);
    }

    @Test
    public void maxPendingSizeTest1() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 680, 0);

        for (ITransaction tx : txn) {
            cache.addCacheTx((AionTransaction) tx);
        }

        Map<BigInteger, AionTransaction> cacheMap =
                cache.getCacheTx(AionAddress.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 659);
    }

    @Test
    public void maxPendingSizeTest2() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 659, 0);

        for (ITransaction tx : txn) {
            cache.addCacheTx((AionTransaction) tx);
        }

        assertTrue(cache.cacheTxSize() == 659);
        AionTransaction tx = getMockTransaction(50, 1, 0).get(0);

        cache.addCacheTx(tx);
        assertTrue(cache.cacheTxSize() == 659);

        Map<BigInteger, AionTransaction> cacheMap =
                cache.getCacheTx(AionAddress.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 659);
    }

    @Test
    public void maxPendingSizeTest3() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 659, 0);

        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertTrue(cache.cacheTxSize() == 659);
        AionTransaction tx = getMockBigTransaction(50, 1, 0, 200).get(0);

        cache.addCacheTx(tx);
        assertTrue(cache.cacheTxSize() == 658);

        Map<BigInteger, AionTransaction> cacheMap =
                cache.getCacheTx(AionAddress.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 658);
    }

    @Test
    public void maxPendingSizeTest4() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 659, 0);

        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertTrue(cache.cacheTxSize() == 659);
        AionTransaction tx = getMockBigTransaction(50, 1, 0, 2000).get(0);

        cache.addCacheTx(tx);
        assertTrue(cache.cacheTxSize() == 652);

        Map<BigInteger, AionTransaction> cacheMap =
                cache.getCacheTx(AionAddress.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 652);
    }

    @Test
    public void maxPendingSizeTest5() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 100, 0);
        txn.addAll(getMockTransaction(101, 600, 0));

        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertTrue(cache.cacheTxSize() == 659);

        Map<BigInteger, AionTransaction> cacheMap =
                cache.getCacheTx(AionAddress.wrap(key.get(0).getAddress()));
        assertTrue(cache.cacheTxSize() == 659);

        AionTransaction tx = getMockBigTransaction(100, 1, 0, 2000).get(0);

        cache.addCacheTx(tx);
        assertTrue(cache.cacheTxSize() == 652);
    }

    @Test
    public void maxPendingSizeTest6() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 100, 0);
        txn.addAll(getMockTransaction(101, 600, 0));

        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertTrue(cache.cacheTxSize() == 659);

        Map<BigInteger, AionTransaction> cacheMap =
                cache.getCacheTx(AionAddress.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 659);

        AionTransaction tx = getMockBigTransaction(100, 1, 0, 199_500).get(0);

        cache.addCacheTx(tx);
        assertTrue(cache.cacheTxSize() == 659);
    }

    @Test
    public void maxPendingSizeTest7() {

        PendingTxCache cache = new PendingTxCache(1);
        List<AionTransaction> txn = getMockTransaction(0, 659, 0);

        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }

        assertTrue(cache.cacheTxSize() == 659);

        Map<BigInteger, AionTransaction> cacheMap =
                cache.getCacheTx(AionAddress.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == 659);

        AionTransaction tx = getMockBigTransaction(100, 1, 0, 199_500).get(0);

        cache.addCacheTx(tx);
        assertTrue(cache.cacheTxSize() == 659);
    }

    @Test
    public void benchmark1() {
        PendingTxCache cache = new PendingTxCache();

        int input = 80_000;
        int remove = 5;

        System.out.println("Gen 80K txs");
        List<AionTransaction> txn = getMockTransaction(0, input, 0);

        System.out.println("adding 80K txs to cache");

        long t1 = System.currentTimeMillis();
        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }
        long t2 = System.currentTimeMillis() - t1;
        System.out.println("add 80K txs took " + t2 + " ms cacheSize: " + cache.cacheSize());

        assertTrue(cache.cacheTxSize() == input);

        System.out.println("Gen another 80K txs");
        txn = getMockTransaction(0, input, 1);

        t1 = System.currentTimeMillis();
        for (AionTransaction tx : txn) {
            cache.addCacheTx(tx);
        }
        t2 = System.currentTimeMillis() - t1;
        System.out.println(
                "add another 80K txs took " + t2 + " ms cacheSize: " + cache.cacheSize());
        assertTrue(cache.cacheTxSize() == (input << 1));

        System.out.println("flush starting");
        Map<AionAddress, BigInteger> flushMap = new HashMap<>();
        flushMap.put(AionAddress.wrap(key.get(0).getAddress()), BigInteger.valueOf(remove));
        flushMap.put(AionAddress.wrap(key.get(1).getAddress()), BigInteger.valueOf(remove));

        t1 = System.currentTimeMillis();
        cache.flush(flushMap);
        t2 = System.currentTimeMillis() - t1;
        System.out.println("flush took " + t2 + " ms");

        Map<BigInteger, AionTransaction> cacheMap =
                cache.getCacheTx(AionAddress.wrap(key.get(0).getAddress()));
        assertTrue(cacheMap.size() == input - remove);

        cacheMap = cache.getCacheTx(AionAddress.wrap(key.get(1).getAddress()));
        assertTrue(cacheMap.size() == input - remove);
    }
}
