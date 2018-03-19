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
package org.aion.txpool.test;

import org.aion.base.type.Address;
import org.aion.base.type.ITransaction;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.txpool.ITxPool;
import org.aion.txpool.zero.TxPoolA0;
import org.aion.zero.types.AionTransaction;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;
import org.junit.Ignore;


import java.math.BigInteger;
import java.util.*;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TxnPoolTest {

    private static List<ECKey> key;
    private static List<ECKey> key2;

    @Before
    public void Setup() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);

        int keyCnt = 10;
        key = new ArrayList<>();

        System.out.println("gen key list----------------");
        for (int i = 0; i < keyCnt; i++) {
            key.add(ECKeyFac.inst().create());
        }
        System.out.println("gen key list finished-------");

        keyCnt = 10000;
        key2 = new ArrayList<>();
        System.out.println("gen key list 2--------------");
        for (int i = 0; i < keyCnt; i++) {
            key2.add(ECKeyFac.inst().create());
        }
        System.out.println("gen key list 2 finished-----");
    }

    @Test
    public void getTxnPool() {
        ITxPool<?> tp = new TxPoolA0<>();
        assertNotNull(tp);
    }

    @Test
    public void add1() {
        Properties config = new Properties();
        config.put("txn-timeout", "100");

        ITxPool<ITransaction> tp = new TxPoolA0<>(config);
        List<ITransaction> txnl = getMockTransaction();

        ((AionTransaction) txnl.get(0)).sign(key.get(0));
        tp.add(txnl);

        assertTrue(tp.size() == 1);
    }

    private List<ITransaction> getMockTransaction() {
        return Collections.singletonList(
                new AionTransaction(ByteUtils.fromHexString("0000000000000001"), Address.wrap(key.get(0).getAddress()),
                        Address.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
                        ByteUtils.fromHexString("1"), ByteUtils.fromHexString("1"), 10000L, 1L));
    }

    @Test
    public void remove() {
        Properties config = new Properties();
        config.put("txn-timeout", "100");

        ITxPool<ITransaction> tp = new TxPoolA0<>(config);
        List<ITransaction> txnl = getMockTransaction();
        ((AionTransaction) txnl.get(0)).sign(key.get(0));
        tp.add(txnl);
        assertTrue(tp.size() == 1);

        // must snapshot the insert transaction before remove
        tp.snapshot();

        tp.remove(txnl);
        assertTrue(tp.size() == 0);

    }

    @Test
    public void remove2() {
        Properties config = new Properties();
        config.put("txn-timeout", "100"); // 100 sec

        ITxPool<ITransaction> tp = new TxPoolA0<>(config);
        List<ITransaction> txl = new ArrayList<>();
        List<ITransaction> txlrm = new ArrayList<>();
        int cnt = 20;
        for (int i = 0; i < cnt; i++) {
            AionTransaction tx = (AionTransaction) genTransaction(BigInteger.valueOf(i).toByteArray());
            tx.setNrgConsume(5000L);
            tx.sign(key.get(0));
            txl.add(tx);
            if (i < 10) {
                txlrm.add(tx);
            }
        }

        List rtn = tp.add(txl);
        assertTrue(rtn.size() == txl.size());

        txl = tp.snapshot();
        assertTrue(txl.size() == cnt);

        rtn = tp.remove(txlrm);
        assertTrue(rtn.size() == 10);
        assertTrue(tp.size() == 10);
    }

    private ITransaction genTransaction(byte[] nonce) {
        return new AionTransaction(nonce, Address.wrap(key.get(0).getAddress()),
                Address.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
                ByteUtils.fromHexString("1"), ByteUtils.fromHexString("1"), 10000L, 1L);
    }

    @Test
    public void timeout1() throws Throwable {
        Properties config = new Properties();
        config.put("txn-timeout", "10"); // 10 sec

        ITxPool<ITransaction> tp = new TxPoolA0<>(config);
        List<ITransaction> txnl = getMockTransaction();
        ((AionTransaction) txnl.get(0)).sign(key.get(0));
        txnl.get(0).setNrgConsume(30000L);
        tp.add(txnl);

        Thread.sleep(10999);

        tp.snapshot();
        assertTrue(tp.size() == 0);
    }

    @Test
    public void timeout2() throws Throwable {
        Properties config = new Properties();
        config.put("txn-timeout", "1"); // 10 sec

        ITxPool<ITransaction> tp = new TxPoolA0<>(config);
        List<ITransaction> txnl = getMockTransaction();
        ((AionTransaction) txnl.get(0)).sign(key.get(0));
        txnl.get(0).setNrgConsume(30000L);
        tp.add(txnl);

        Thread.sleep(8999);

        tp.snapshot();
        assertTrue(tp.size() == 1);
    }

    @Test
    @Ignore

    public void snapshot() throws Throwable {
        Properties config = new Properties();
        config.put("txn-timeout", "10"); // 10 sec

        ITxPool<ITransaction> tp = new TxPoolA0<>(config);
        List<ITransaction> txnl = getMockTransaction();
        ((AionTransaction) txnl.get(0)).sign(key.get(0));
        tp.add(txnl);

        tp.snapshot();
        assertTrue(tp.size() == 1);
    }

    @Test

    public void snapshot2() throws Throwable {
        Properties config = new Properties();
        config.put("txn-timeout", "100"); // 100 sec

        ITxPool<ITransaction> tp = new TxPoolA0<>(config);
        List<ITransaction> txl = new ArrayList<>();
        int cnt = 26;
        for (int i = 0; i < cnt; i++) {
            AionTransaction txe = (AionTransaction) genTransaction(BigInteger.valueOf(i).toByteArray());
            txe.setNrgConsume(5000L);
            txe.sign(key.get(0));
            txl.add(txe);
        }

        List rtn = tp.add(txl);
        assertTrue(rtn.size() == txl.size());

        txl = tp.snapshot();
        assertTrue(txl.size() == cnt);
    }

    @Test
    //@Ignore
    public void snapshot3() {
        Properties config = new Properties();
        config.put("txn-timeout", "100");

        TxPoolA0<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 26;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            ITransaction txn = genTransaction(nonce);
            ((AionTransaction) txn).sign(key.get(0));
            txn.setNrgConsume(i + 1);
            txnl.add(txn);
        }
        tp.add(txnl);
        assertTrue(tp.size() == cnt);

        // sort the inserted txs
        List<ITransaction> txl = tp.snapshot();

        long nonce = 0;
        for (ITransaction tx : txl) {
            assertTrue((new BigInteger(tx.getNonce())).longValue() == nonce++);
        }
    }

    @Test
    public void snapshot4() {
        Properties config = new Properties();
        config.put("txn-timeout", "100");

        TxPoolA0<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 26;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            ITransaction txn = genTransaction(nonce);
            ((AionTransaction) txn).sign(key.get(0));
            txn.setNrgConsume(50 - i);
            txnl.add(txn);
        }
        tp.add(txnl);
        assertTrue(tp.size() == cnt);

        // sort the inserted txs
        List<ITransaction> txl = tp.snapshot();

        long nonce = 0;
        for (ITransaction tx : txl) {
            assertTrue((new BigInteger(tx.getNonce())).longValue() == nonce++);
        }
    }

    @Test
    public void addRepeatedTxn() {
        Properties config = new Properties();
        config.put("txn-timeout", "10");

        ITxPool<ITransaction> tp = new TxPoolA0<>(config);
        ITransaction txn = new AionTransaction(ByteUtils.fromHexString("0000000000000001"),
                Address.wrap(key.get(0).getAddress()), Address.wrap(key.get(0).getAddress()),
                ByteUtils.fromHexString("1"), ByteUtils.fromHexString("1"), 10000L, 1L);

        ((AionTransaction) txn).sign(key.get(0));

        List<ITransaction> txnl = new ArrayList<>();
        txnl.add(txn);
        txnl.add(txn);
        tp.add(txnl);

        assertTrue(tp.size() == 1);
    }

    @Test
    public void addRepeatedTxn2() {
        Properties config = new Properties();
        config.put("txn-timeout", "10");

        ITxPool<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 10;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            ITransaction txn = genTransaction(nonce);
            ((AionTransaction) txn).sign(key.get(0));
            txn.setNrgConsume(50);
            txnl.add(txn);
        }

        tp.add(txnl);
        assertTrue(tp.size() == cnt);

        byte[] nonce = new byte[Long.BYTES];
        nonce[Long.BYTES - 1] = (byte) 5;
        ITransaction txn = genTransaction(nonce);
        ((AionTransaction) txn).sign(key.get(0));
        txn.setNrgConsume(500);
        tp.add(txn);

        List<ITransaction> snapshot = tp.snapshot();
        assertTrue(snapshot.size() == cnt);

        assertTrue(snapshot.get(5).equals(txn));
    }

    @Test
    public void addRepeatedTxn3() {
        Properties config = new Properties();
        config.put("txn-timeout", "10");

        ITxPool<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 10;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            ITransaction txn = genTransaction(nonce);
            ((AionTransaction) txn).sign(key.get(0));
            txn.setNrgConsume(50);
            txnl.add(txn);
        }

        tp.add(txnl);
        tp.snapshot();
        assertTrue(tp.size() == cnt);

        byte[] nonce = new byte[Long.BYTES];
        nonce[Long.BYTES - 1] = (byte) 5;
        ITransaction txn = genTransaction(nonce);
        ((AionTransaction) txn).sign(key.get(0));
        txn.setNrgConsume(500);
        tp.add(txn);

        List<ITransaction> snapshot = tp.snapshot();
        assertTrue(snapshot.size() == cnt);

        assertTrue(snapshot.get(5).equals(txn));
    }

    @Test
    public void addTxWithSameNonce() {
        Properties config = new Properties();
        config.put("txn-timeout", "10");

        ITxPool<ITransaction> tp = new TxPoolA0<>(config);

        ITransaction txn = genTransaction(ByteUtils.fromHexString("0000000000000001"));
        txn.setNrgConsume(100);

        List<ITransaction> txnl = new ArrayList<>();

        ((AionTransaction) txn).sign(key.get(0));
        txnl.add(txn);

        ((AionTransaction) txn).sign(key.get(0));
        txnl.add(txn);
        long t = new BigInteger(txn.getTimeStamp()).longValue();

        tp.add(txnl);

        assertTrue(tp.size() == 1);

        List<ITransaction> txl = tp.snapshot();
        assertTrue(txl.size() == 1);
        assertTrue(new BigInteger(txl.get(0).getTimeStamp()).longValue() == t);

    }

    @Test
    public void noncebyAccountTest() {
        Properties config = new Properties();
        config.put("txn-timeout", "10");

        TxPoolA0<ITransaction> tp = new TxPoolA0<>(config);

        Address acc = Address.wrap(key.get(0).getAddress());

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 100;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) (i + 1);
            ITransaction txn = new AionTransaction(nonce, acc,
                    Address.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
                    ByteUtils.fromHexString("1"), ByteUtils.fromHexString("1"), 10000L, 1L);
            ((AionTransaction) txn).sign(key.get(0));
            txn.setNrgConsume(100L);
            txnl.add(txn);
        }
        tp.add(txnl);

        assertTrue(tp.size() == cnt);

        // sort the inserted txs
        tp.snapshot();

        List<BigInteger> nl = tp.getNonceList(acc);

        for (int i = 0; i < cnt; i++) {
            assertTrue(nl.get(i).equals(BigInteger.valueOf(i + 1)));
        }
    }

    @Test
    public void noncebyAccountTest2() {
        Properties config = new Properties();
        config.put("txn-timeout", "10");

        TxPoolA0<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 100;
        for (ECKey aKey1 : key) {
            Address acc = Address.wrap(aKey1.getAddress());
            for (int i = 0; i < cnt; i++) {
                byte[] nonce = new byte[Long.BYTES];
                nonce[Long.BYTES - 1] = (byte) (i + 1);
                ITransaction txn = new AionTransaction(nonce, acc,
                        Address.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
                        ByteUtils.fromHexString("1"), ByteUtils.fromHexString("1"), 10000L, 1L);
                ((AionTransaction) txn).sign(aKey1);
                txn.setNrgConsume(100L);
                txnl.add(txn);
            }
        }

        tp.add(txnl);

        assertTrue(tp.size() == cnt * key.size());

        // sort the inserted txs
        tp.snapshot();

        for (ECKey aKey : key) {
            List<BigInteger> nl = tp.getNonceList(Address.wrap(aKey.getAddress()));
            for (int i = 0; i < cnt; i++) {
                assertTrue(nl.get(i).equals(BigInteger.valueOf(i + 1)));
            }
        }
    }

    @Test
    public void feemapTest() {
        Properties config = new Properties();
        config.put("txn-timeout", "10");

        TxPoolA0<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 100;
        byte[] nonce = new byte[Long.BYTES];
        for (int i = 0; i < cnt; i++) {
            nonce[Long.BYTES - 1] = 1;

            Address addr = Address.wrap(key2.get(i).getAddress());
            ITransaction txn = new AionTransaction(nonce, addr,
                    Address.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
                    ByteUtils.fromHexString("1"), ByteUtils.fromHexString("1"), 10000L, 1L);

            ((AionTransaction) txn).sign(key.get(0));
            txn.setNrgConsume(i + 1);
            txnl.add(txn);
        }
        tp.add(txnl);

        assertTrue(tp.size() == cnt);

        // sort the inserted txs
        tp.snapshot();

        List<BigInteger> nl = tp.getFeeList();

        long val = 100;
        for (int i = 0; i < cnt; i++) {
            assertTrue(nl.get(i).compareTo(BigInteger.valueOf(val--)) == 0);
        }
    }

    @Test
    public void TxnfeeCombineTest() {
        Properties config = new Properties();
        config.put("txn-timeout", "100");

        TxPoolA0<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 10;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) (i + 1);

            ITransaction txn = genTransaction(nonce);

            ((AionTransaction) txn).sign(key.get(0));
            txn.setNrgConsume(i + 1);
            txnl.add(txn);
        }
        tp.add(txnl);
        assertTrue(tp.size() == cnt);

        // sort the inserted txs
        tp.snapshot();

        List<BigInteger> nl = tp.getFeeList();

        assertTrue(nl.size() == 1);
        assertTrue(nl.get(0).compareTo(BigInteger.valueOf(55 / 10)) == 0);
    }

    @Test
    public void TxnfeeCombineTest2() {
        Properties config = new Properties();
        config.put("txn-timeout", "100");

        TxPoolA0<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 26;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) (i + 1);

            ITransaction txn = genTransaction(nonce);
            ((AionTransaction) txn).sign(key.get(0));
            txn.setNrgConsume(i + 1);
            txnl.add(txn);
        }
        tp.add(txnl);
        assertTrue(tp.size() == cnt);

        // sort the inserted txs
        tp.snapshot();

        List<BigInteger> nl = tp.getFeeList();

        assertTrue(nl.size() == 2);
        assertTrue(nl.get(0).compareTo(BigInteger.valueOf(26)) == 0);
        assertTrue(nl.get(1).compareTo(BigInteger.valueOf(325 / 25)) == 0);
    }

    @Test
    public void bestNonceSet() throws Throwable {
        Properties config = new Properties();
        config.put("txn-timeout", "100");

        TxPoolA0<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 25;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            ITransaction txn = genTransaction(nonce);

            ((AionTransaction) txn).sign(key.get(0));
            txn.setNrgConsume(i + 1);
            txnl.add(txn);
        }
        tp.add(txnl);
        assertTrue(tp.size() == cnt);

        // sort the inserted txs
        tp.snapshot();
        Map.Entry<BigInteger, BigInteger> nonce = tp.bestNonceSet(Address.wrap(key.get(0).getAddress()));

        assertTrue(nonce.getKey().equals(BigInteger.ZERO));
        assertTrue(nonce.getValue().equals(BigInteger.valueOf(cnt - 1)));
    }

    @Test
    public void bestNonceSet2() throws Throwable {
        Properties config = new Properties();
        config.put("txn-timeout", "100");

        TxPoolA0<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 26;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            ITransaction txn = genTransaction(nonce);

            ((AionTransaction) txn).sign(key.get(0));
            txn.setNrgConsume(i + 1);
            txnl.add(txn);
        }

        tp.add(txnl);
        assertTrue(tp.size() == cnt);

        // sort the inserted txs
        tp.snapshot();
        Map.Entry<BigInteger, BigInteger> nonce = tp.bestNonceSet(Address.wrap(key.get(0).getAddress()));

        assertTrue(nonce.getKey().equals(BigInteger.ZERO));
        assertTrue(nonce.getValue().equals(BigInteger.valueOf(cnt - 1)));
    }

    @Test
    public void bestNonceSet3() throws Throwable {
        Properties config = new Properties();
        config.put("txn-timeout", "100");

        TxPoolA0<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 20;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            ITransaction txn = genTransaction(nonce);

            ((AionTransaction) txn).sign(key.get(0));
            txn.setNrgConsume(i + 1);
            txnl.add(txn);
        }

        byte[] nonce = new byte[Long.BYTES];
        nonce[Long.BYTES - 1] = (byte) 22;
        ITransaction txn = genTransaction(nonce);
        txnl.add(txn);
        tp.add(txnl);
        assertTrue(tp.size() == cnt);

        // sort the inserted txs
        tp.snapshot();
        Map.Entry<BigInteger, BigInteger> nonceSet = tp.bestNonceSet(Address.wrap(key.get(0).getAddress()));

        assertTrue(nonceSet.getKey().equals(BigInteger.ZERO));
        assertTrue(nonceSet.getValue().equals(BigInteger.valueOf(cnt - 1)));
    }

    @Test
    //@Ignore
    /* 100K new transactions in pool around 1200ms (cold-call)
     */ public void benchmarkSnapshot() {
        Properties config = new Properties();
        config.put("txn-timeout", "100");

        TxPoolA0<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 10000;
        for (ECKey aKey1 : key) {
            Address acc = Address.wrap(aKey1.getAddress());
            for (int i = 0; i < cnt; i++) {
                ITransaction txn = new AionTransaction(BigInteger.valueOf(i).toByteArray(), acc,
                        Address.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
                        ByteUtils.fromHexString("1"), ByteUtils.fromHexString("1"), 10000L, 1L);
                ((AionTransaction) txn).sign(aKey1);
                txn.setNrgConsume(100L);
                txnl.add(txn);
            }
        }

        tp.add(txnl);
        assertTrue(tp.size() == cnt * key.size());

        // sort the inserted txs
        long start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        for (ECKey aKey : key) {
            List<BigInteger> nl = tp.getNonceList(Address.wrap(aKey.getAddress()));
            for (int i = 0; i < cnt; i++) {
                assertTrue(nl.get(i).equals(BigInteger.valueOf(i)));
            }
        }
    }

    @Test
    /* 100K new transactions in pool around 650ms (cold-call)

       1K new transactions insert to the pool later around 70ms to snap (including sort)
     */ public void benchmarkSnapshot2() {
        Properties config = new Properties();
        config.put("txn-timeout", "100");

        TxPoolA0<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 10000;
        for (ECKey aKey2 : key) {
            Address acc = Address.wrap(aKey2.getAddress());
            for (int i = 0; i < cnt; i++) {
                ITransaction txn = new AionTransaction(BigInteger.valueOf(i).toByteArray(), acc,
                        Address.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
                        ByteUtils.fromHexString("1"), ByteUtils.fromHexString("1"), 10000L, 1L);
                ((AionTransaction) txn).sign(aKey2);
                txn.setNrgConsume(100L);
                txnl.add(txn);
            }
        }

        tp.add(txnl);
        assertTrue(tp.size() == cnt * key.size());

        // sort the inserted txs
        long start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        int cnt2 = 100;
        txnl.clear();
        for (ECKey aKey1 : key) {
            for (int i = 0; i < cnt2; i++) {
                ITransaction txn = new AionTransaction(BigInteger.valueOf(cnt + i).toByteArray(),
                        Address.wrap(aKey1.getAddress()),
                        Address.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
                        ByteUtils.fromHexString("1"), ByteUtils.fromHexString("1"), 10000L, 1L);
                ((AionTransaction) txn).sign(aKey1);
                txn.setNrgConsume(100L);
                txnl.add(txn);
            }
        }

        tp.add(txnl);
        assertTrue(tp.size() == (cnt + cnt2) * key.size());

        start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("2nd time spent: " + (System.currentTimeMillis() - start) + " ms.");

        for (ECKey aKey : key) {
            List<BigInteger> nl = tp.getNonceList(Address.wrap(aKey.getAddress()));
            for (int i = 0; i < cnt + cnt2; i++) {
                assertTrue(nl.get(i).equals(BigInteger.valueOf(i)));
            }
        }
    }

    @Test
    /* 1M new transactions with 10000 accounts (100 txs per account)in pool snapshot around 10s (cold-call)
       gen new txns 55s (spent a lot of time to sign tx)
       put txns into pool 2.5s
       snapshot txn 5s
     */ public void benchmarkSnapshot3() {
        Properties config = new Properties();
        config.put("txn-timeout", "100");

        TxPoolA0<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 100;
        System.out.println("Gen new transactions --");
        long start = System.currentTimeMillis();
        for (ECKey aKey21 : key2) {
            Address acc = Address.wrap(aKey21.getAddress());
            for (int i = 0; i < cnt; i++) {
                ITransaction txn = new AionTransaction(BigInteger.valueOf(i).toByteArray(), acc,
                        Address.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
                        ByteUtils.fromHexString("1"), ByteUtils.fromHexString("1"), 10000L, 1L);
                ((AionTransaction) txn).sign(aKey21);
                txn.setNrgConsume(100L);
                txnl.add(txn);
            }
        }
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        System.out.println("Adding transactions into pool--");
        start = System.currentTimeMillis();
        tp.add(txnl);
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        assertTrue(tp.size() == cnt * key2.size());

        // sort the inserted txs
        System.out.println("Snapshoting --");
        start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        for (ECKey aKey2 : key2) {
            List<BigInteger> nl = tp.getNonceList(Address.wrap(aKey2.getAddress()));
            for (int i = 0; i < cnt; i++) {
                assertTrue(nl.get(i).equals(BigInteger.valueOf(i)));
            }
        }
    }

    @Test
    /* 100K new transactions in pool around 350ms (cold-call)
     */ public void benchmarkSnapshot4() {
        Properties config = new Properties();
        config.put("txn-timeout", "100");

        TxPoolA0<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        List<ITransaction> txnlrm = new ArrayList<>();
        int cnt = 100000;
        int rmCnt = 10;
        Address acc = Address.wrap(key.get(0).getAddress());
        System.out.println("gen new transactions...");
        long start = System.currentTimeMillis();
        for (int i = 0; i < cnt; i++) {
            ITransaction txn = new AionTransaction(BigInteger.valueOf(i).toByteArray(), acc,
                    Address.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
                    ByteUtils.fromHexString("1"), ByteUtils.fromHexString("1"), 10000L, 1L);
            ((AionTransaction) txn).sign(key.get(0));
            txn.setNrgConsume(100L);
            txnl.add(txn);

            if (i < rmCnt) {
                txnlrm.add(txn);
            }
        }
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        System.out.println("Inserting txns...");
        start = System.currentTimeMillis();
        tp.add(txnl);
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");
        assertTrue(tp.size() == cnt);

        // sort the inserted txs
        System.out.println("Snapshoting...");
        start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        System.out.println("Removing the first 10 txns...");
        start = System.currentTimeMillis();
        List rm = tp.remove(txnlrm);
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");
        assertTrue(rm.size() == rmCnt);
        assertTrue(tp.size() == cnt - rmCnt);

        System.out.println("Re-Snapshot after some txns was been removed...");
        start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        List<BigInteger> nl = tp.getNonceList(Address.wrap(key.get(0).getAddress()));
        for (int i = 0; i < nl.size(); i++) {
            assertTrue(nl.get(i).equals(BigInteger.valueOf(i).add(BigInteger.valueOf(rmCnt))));
        }
    }

    @Test
    /* 100K new transactions in pool around 350ms (cold-call)

       the second time snapshot is around 35ms
     */ public void benchmarkSnapshot5() {
        Properties config = new Properties();
        config.put("txn-timeout", "100");

        TxPoolA0<ITransaction> tp = new TxPoolA0<>(config);

        List<ITransaction> txnl = new ArrayList<>();
        int cnt = 10000;
        for (ECKey aKey1 : key) {
            Address acc = Address.wrap(aKey1.getAddress());
            for (int i = 0; i < cnt; i++) {
                ITransaction txn = new AionTransaction(BigInteger.valueOf(i).toByteArray(), acc,
                        Address.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
                        ByteUtils.fromHexString("1"), ByteUtils.fromHexString("1"), 10000L, 1L);
                ((AionTransaction) txn).sign(aKey1);
                txn.setNrgConsume(100L);
                txnl.add(txn);
            }
        }

        tp.add(txnl);
        assertTrue(tp.size() == cnt * key.size());

        // sort the inserted txs
        System.out.println("1st time snapshot...");
        long start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("1st time spent: " + (System.currentTimeMillis() - start) + " ms.");

        System.out.println("2nd time snapshot...");
        start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("2nd time spent: " + (System.currentTimeMillis() - start) + " ms.");

        for (ECKey aKey : key) {
            List<BigInteger> nl = tp.getNonceList(Address.wrap(aKey.getAddress()));
            for (int i = 0; i < cnt; i++) {
                assertTrue(nl.get(i).equals(BigInteger.valueOf(i)));
            }
        }
    }

    @Test
    public void testSnapshotAll() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey key = ECKeyFac.inst().create();

        List<AionTransaction> txs = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            AionTransaction tx = new AionTransaction(BigInteger.valueOf(i).toByteArray(), Address.wrap(key.getAddress()),
                    Address.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
                    ByteUtils.fromHexString("1"), ByteUtils.fromHexString("1"), 10000L, 1L);
            tx.sign(key);
            txs.add(tx);
        }

        Properties config = new Properties();
        ITxPool<AionTransaction> tp = new TxPoolA0<>(config);

        tp.add(txs.subList(0, 500));
        assertEquals(500, tp.snapshot().size());
        assertEquals(500, tp.snapshotAll().size());

        tp.remove(txs.subList(0, 100));
        assertEquals(400, tp.snapshot().size());
        assertEquals(400, tp.snapshotAll().size());
    }

    @Test
    public void testRemove2() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey key = ECKeyFac.inst().create();

        List<AionTransaction> txs = new ArrayList<>();
        for (int i = 0; i < 95; i++) {
            AionTransaction tx = new AionTransaction(BigInteger.valueOf(i).toByteArray(), Address.wrap(key.getAddress()),
                    Address.wrap("0000000000000000000000000000000000000000000000000000000000000001"),
                    ByteUtils.fromHexString("1"), ByteUtils.fromHexString("1"), 10000L, 1L);
            tx.sign(key);
            tx.setNrgConsume(100L);
            txs.add(tx);
        }

        Properties config = new Properties();
        ITxPool<AionTransaction> tp = new TxPoolA0<>(config);

        tp.add(txs.subList(0, 26));
        assertEquals(26, tp.snapshot().size());
        assertEquals(26, tp.snapshotAll().size());

        tp.remove(txs.subList(0, 13));
        assertEquals(13, tp.snapshot().size());
        assertEquals(13, tp.snapshotAll().size());

        tp.add(txs.subList(26, 70));
        assertEquals(57, tp.snapshot().size());
        assertEquals(57, tp.snapshotAll().size());

        tp.remove(txs.subList(13, 40));
        assertEquals(30, tp.snapshot().size());
        assertEquals(30, tp.snapshotAll().size());
        // assume we don't remove tx 40
        tp.remove(txs.subList(41, 70));
        assertEquals(1, tp.snapshot().size());
        assertEquals(1, tp.snapshotAll().size());
    }
}
