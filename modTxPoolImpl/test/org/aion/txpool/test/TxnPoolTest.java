package org.aion.txpool.test;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.txpool.ITxPool;
import org.aion.txpool.zero.TxPoolA0;
import org.aion.types.AionAddress;
import org.aion.util.types.AddressUtils;
import org.aion.util.types.ByteArrayWrapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

public class TxnPoolTest {

    private List<ECKey> key;
    private List<ECKey> key2;

    @Before
    public void Setup() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        int keyCnt = 10;

        if (key == null) {
            key = new ArrayList<>();
            System.out.println("gen key list----------------");
            for (int i = 0; i < keyCnt; i++) {
                key.add(ECKeyFac.inst().create());
            }
            System.out.println("gen key list finished-------");
        }

        if (key2 == null) {
            keyCnt = 10000;
            key2 = new ArrayList<>();
            System.out.println("gen key list 2--------------");
            for (int i = 0; i < keyCnt; i++) {
                key2.add(ECKeyFac.inst().create());
            }
            System.out.println("gen key list 2 finished-----");
        }
    }

    @Test
    public void getTxnPool() {
        ITxPool tp = new TxPoolA0();
        assertNotNull(tp);
    }

    @Test
    public void add1() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        ITxPool tp = new TxPoolA0(config);
        List<AionTransaction> txnl = getMockTransaction();

        tp.add(txnl);

        Assert.assertEquals(1, tp.size());
    }

    private List<AionTransaction> getMockTransaction() {
        return Collections.singletonList(
                AionTransaction.create(
                        key.get(0),
                        ByteUtils.fromHexString("0000000000000001"),
                        AddressUtils.wrapAddress(
                                "0000000000000000000000000000000000000000000000000000000000000001"),
                        ByteUtils.fromHexString("1"),
                        ByteUtils.fromHexString("1"),
                        10000L,
                        1L,
                        TransactionTypes.DEFAULT));
    }

    @Test
    public void remove() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        ITxPool tp = new TxPoolA0(config);
        List<AionTransaction> txnl = getMockTransaction();
        tp.add(txnl);
        Assert.assertEquals(1, tp.size());

        // must snapshot the insert transaction before remove
        tp.snapshot();

        tp.remove(txnl);
        Assert.assertEquals(0, tp.size());
    }

    @Test
    public void remove2() {
        Properties config = new Properties();
        config.put("tx-timeout", "100"); // 100 sec

        ITxPool tp = new TxPoolA0(config);
        List<AionTransaction> txl = new ArrayList<>();
        List<AionTransaction> txlrm = new ArrayList<>();
        int cnt = 20;
        for (int i = 0; i < cnt; i++) {
            AionTransaction tx = genTransaction(BigInteger.valueOf(i).toByteArray());
            tx.setNrgConsume(5000L);
            txl.add(tx);
            if (i < 10) {
                txlrm.add(tx);
            }
        }

        List rtn = tp.add(txl);
        Assert.assertEquals(rtn.size(), txl.size());

        txl = tp.snapshot();
        Assert.assertEquals(txl.size(), cnt);

        rtn = tp.remove(txlrm);
        Assert.assertEquals(10, rtn.size());
        Assert.assertEquals(10, tp.size());
    }

    @Test
    public void remove3() {
        Properties config = new Properties();
        config.put("tx-timeout", "100"); // 100 sec

        ITxPool tp = new TxPoolA0(config);
        List<AionTransaction> txl = new ArrayList<>();
        List<AionTransaction> txlrm = new ArrayList<>();
        int cnt = 20;
        for (int i = 0; i < cnt; i++) {
            AionTransaction tx = genTransaction(BigInteger.valueOf(i).toByteArray());
            tx.setNrgConsume(5000L);
            txl.add(tx);
            if (i < 10) {
                txlrm.add(tx);
            }
        }

        List rtn = tp.add(txl);
        Assert.assertEquals(rtn.size(), txl.size());

        txl = tp.snapshot();
        Assert.assertEquals(txl.size(), cnt);

        Map<AionAddress, BigInteger> account = new HashMap<>();
        account.put(txl.get(0).getSenderAddress(), BigInteger.valueOf(10));
        rtn = tp.remove(account);
        Assert.assertEquals(10, rtn.size());
        Assert.assertEquals(10, tp.size());
    }

    private AionTransaction genTransaction(byte[] nonce) {
        return AionTransaction.create(
                key.get(0),
                nonce,
                AddressUtils.wrapAddress(
                        "0000000000000000000000000000000000000000000000000000000000000001"),
                ByteUtils.fromHexString("1"),
                ByteUtils.fromHexString("1"),
                10000L,
                1L,
                TransactionTypes.DEFAULT);
    }

    private AionTransaction genTransaction(byte[] nonce, int _index) {
        return AionTransaction.create(
                key.get(_index),
                nonce,
                AddressUtils.wrapAddress(
                        "0000000000000000000000000000000000000000000000000000000000000001"),
                ByteUtils.fromHexString("1"),
                ByteUtils.fromHexString("1"),
                10000L,
                1L,
                TransactionTypes.DEFAULT);
    }

    private AionTransaction genTransactionRandomPrice(byte[] nonce, long price, ECKey key) {
        return AionTransaction.create(
                key,
                nonce,
                AddressUtils.wrapAddress("0000000000000000000000000000000000000000000000000000000000000001"),
                ByteUtils.fromHexString("1"),
                ByteUtils.fromHexString("1"),
                10000L,
                price,
                TransactionTypes.DEFAULT);
    }

    @Test
    public void timeout1() throws Exception {
        Properties config = new Properties();
        config.put("tx-timeout", "10"); // 10 sec

        ITxPool tp = new TxPoolA0(config);
        List<AionTransaction> txnl = getMockTransaction();
        txnl.get(0).setNrgConsume(30000L);
        tp.add(txnl);

        Thread.sleep(10999);

        tp.snapshot();
        Assert.assertEquals(0, tp.size());
    }

    @Test
    public void timeout2() throws Exception {
        Properties config = new Properties();
        config.put("tx-timeout", "1"); // 10 sec

        ITxPool tp = new TxPoolA0(config);
        List<AionTransaction> txnl = getMockTransaction();
        txnl.get(0).setNrgConsume(30000L);
        tp.add(txnl);

        Thread.sleep(8999);

        tp.snapshot();
        Assert.assertEquals(1, tp.size());
    }

    @Test
    public void snapshot() {
        Properties config = new Properties();
        config.put("tx-timeout", "10"); // 10 sec

        ITxPool tp = new TxPoolA0(config);
        List<AionTransaction> txnl = getMockTransaction();
        tp.add(txnl);

        tp.snapshot();
        Assert.assertEquals(1, tp.size());
    }

    @Test
    public void snapshot2() {
        Properties config = new Properties();
        config.put("tx-timeout", "100"); // 100 sec

        ITxPool tp = new TxPoolA0(config);
        List<AionTransaction> txl = new ArrayList<>();
        int cnt = 26;
        for (int i = 0; i < cnt; i++) {
            AionTransaction txe = genTransaction(BigInteger.valueOf(i).toByteArray());
            txe.setNrgConsume(5000L);
            txl.add(txe);
        }

        List rtn = tp.add(txl);
        Assert.assertEquals(rtn.size(), txl.size());

        txl = tp.snapshot();
        Assert.assertEquals(txl.size(), cnt);
    }

    @Test
    // @Ignore
    public void snapshot3() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 26;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            AionTransaction txn = genTransaction(nonce);
            txn.setNrgConsume(i + 1);
            txnl.add(txn);
        }
        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt);

        // sort the inserted txs
        List<AionTransaction> txl = tp.snapshot();

        long nonce = 0;
        for (AionTransaction tx : txl) {
            Assert.assertEquals((new BigInteger(tx.getNonce())).longValue(), nonce++);
        }
    }

    @Test
    public void snapshot4() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 26;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            AionTransaction txn = genTransaction(nonce);
            txn.setNrgConsume(50 - i);
            txnl.add(txn);
        }
        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt);

        // sort the inserted txs
        List<AionTransaction> txl = tp.snapshot();

        long nonce = 0;
        for (AionTransaction tx : txl) {
            Assert.assertEquals((new BigInteger(tx.getNonce())).longValue(), nonce++);
        }
    }

    @Test
    public void snapshot5() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 100;
        Random r = new Random();
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            AionTransaction txn = genTransaction(nonce);
            txn.setNrgConsume(r.nextInt(1000));
            txnl.add(txn);
        }
        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt);

        // sort the inserted txs
        List<AionTransaction> txl = tp.snapshot();
        Assert.assertEquals(tp.size(), txl.size());
        Assert.assertEquals(tp.snapshotAll().size(), txl.size());

        long nonce = 0;
        for (AionTransaction tx : txl) {
            Assert.assertEquals((new BigInteger(tx.getNonce())).longValue(), nonce++);
        }
    }

    @Test
    public void snapshot6() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 200;
        Random r = new Random();
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            AionTransaction txn = genTransactionRandomPrice(nonce, r.nextInt(1000), key.get(0));
            txn.setNrgConsume(r.nextInt(1000));
            txnl.add(txn);
        }
        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt);

        // sort the inserted txs
        List<AionTransaction> txl = tp.snapshot();
        Assert.assertEquals(tp.size(), txl.size());
        Assert.assertEquals(tp.snapshotAll().size(), txl.size());

        long nonce = 0;
        for (AionTransaction tx : txl) {
            Assert.assertEquals((new BigInteger(tx.getNonce())).longValue(), nonce++);
        }
    }

    @Test
    public void snapshot7() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 200;
        Random r = new Random();
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            AionTransaction txn = genTransactionRandomPrice(nonce, r.nextInt(1000), key.get(0));

            txn.setNrgConsume(r.nextInt(1000));
            txnl.add(txn);
        }
        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt);

        // sort the inserted txs
        List<AionTransaction> txl = tp.snapshot();
        Assert.assertEquals(tp.size(), txl.size());
        Assert.assertEquals(tp.snapshotAll().size(), txl.size());

        long nonce = 0;
        for (AionTransaction tx : txl) {
            Assert.assertEquals((new BigInteger(tx.getNonce())).longValue(), nonce++);
        }
    }

    @Test
    public void snapshot8() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 200;
        Random r = new Random();
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            AionTransaction txn = genTransactionRandomPrice(nonce, r.nextInt(1000), key.get(0));

            txn.setNrgConsume(r.nextInt(1000));
            txnl.add(txn);
        }
        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt);

        // sort the inserted txs
        List<AionTransaction> txl = tp.snapshot();
        Assert.assertEquals(tp.size(), txl.size());
        Assert.assertEquals(tp.snapshotAll().size(), txl.size());

        long nonce = 0;
        for (AionTransaction tx : txl) {
            Assert.assertEquals((new BigInteger(tx.getNonce())).longValue(), nonce++);
        }
    }

    @Test
    public void snapshot9() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        Map<ByteArrayWrapper, AionTransaction> txMap = new HashMap<>();

        int cnt = 25;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            AionTransaction txn = genTransaction(nonce, 0);

            txn.setNrgConsume(1);
            txnl.add(txn);
            txMap.put(ByteArrayWrapper.wrap(txn.getTransactionHash()), txn);
        }
        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt);

        List<AionTransaction> txnl2 = new ArrayList<>();
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            AionTransaction txn = genTransaction(nonce, 1);

            txn.setNrgConsume(1);
            txnl2.add(txn);
            txMap.put(ByteArrayWrapper.wrap(txn.getTransactionHash()), txn);
        }
        tp.add(txnl2);
        Assert.assertEquals(tp.size(), cnt * 2);

        // sort the inserted txs
        List<AionTransaction> txl = tp.snapshot();
        Assert.assertEquals(tp.size(), txl.size());
        Assert.assertEquals(tp.snapshotAll().size(), txl.size());

        for (AionTransaction tx : txl) {
            assertTrue(txMap.containsKey(ByteArrayWrapper.wrap(tx.getTransactionHash())));
            Assert.assertEquals(txMap.get(ByteArrayWrapper.wrap(tx.getTransactionHash())), tx);
        }
    }

    @Test
    public void snapshot10() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        Map<ByteArrayWrapper, AionTransaction> txMap = new HashMap<>();
        int cnt = 16;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            AionTransaction txn = genTransaction(nonce, 0);

            txn.setNrgConsume(1);
            txnl.add(txn);
            txMap.put(ByteArrayWrapper.wrap(txn.getTransactionHash()), txn);
        }

        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            AionTransaction txn = genTransaction(nonce, 1);

            txn.setNrgConsume(1);
            txnl.add(txn);
            txMap.put(ByteArrayWrapper.wrap(txn.getTransactionHash()), txn);
        }

        for (int i = 16; i < 16 + cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            AionTransaction txn = genTransaction(nonce, 0);

            txn.setNrgConsume(1);
            txnl.add(txn);
            txMap.put(ByteArrayWrapper.wrap(txn.getTransactionHash()), txn);
        }

        for (int i = 16; i < 16 + cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            AionTransaction txn = genTransaction(nonce, 1);

            txn.setNrgConsume(1);
            txnl.add(txn);
            txMap.put(ByteArrayWrapper.wrap(txn.getTransactionHash()), txn);
        }

        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt * 4);

        // sort the inserted txs
        List<AionTransaction> txl = tp.snapshot();
        Assert.assertEquals(tp.size(), txl.size());
        Assert.assertEquals(tp.snapshotAll().size(), txl.size());

        for (AionTransaction tx : txl) {
            assertTrue(txMap.containsKey(ByteArrayWrapper.wrap(tx.getTransactionHash())));
            Assert.assertEquals(txMap.get(ByteArrayWrapper.wrap(tx.getTransactionHash())), tx);
        }
    }

    @Test
    // TODO Fix this test
    public void snapshotWithSameTransactionTimestamp() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        Map<ByteArrayWrapper, AionTransaction> txMap = new HashMap<>();
        int cnt = 16;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            AionTransaction txn = genTransaction(nonce, 0);

            txn.setNrgConsume(1);
            txnl.add(txn);
            txMap.put(ByteArrayWrapper.wrap(txn.getTransactionHash()), txn);
        }

        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            AionTransaction txn = genTransaction(nonce, 1);

            txn.setNrgConsume(1);
            txnl.add(txn);
            txMap.put(ByteArrayWrapper.wrap(txn.getTransactionHash()), txn);
        }

        for (int i = 16; i < 16 + cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            AionTransaction txn = genTransaction(nonce, 0);

            txn.setNrgConsume(1);
            txnl.add(txn);
            txMap.put(ByteArrayWrapper.wrap(txn.getTransactionHash()), txn);
        }

        for (int i = 16; i < 16 + cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            AionTransaction txn = genTransaction(nonce, 1);

            txn.setNrgConsume(1);
            txnl.add(txn);
            txMap.put(ByteArrayWrapper.wrap(txn.getTransactionHash()), txn);
        }

        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt * 4);

        // sort the inserted txs
        List<AionTransaction> txl = tp.snapshot();
        Assert.assertEquals(tp.size(), txl.size());
        Assert.assertEquals(tp.snapshotAll().size(), txl.size());

        for (AionTransaction tx : txl) {
            assertTrue(txMap.containsKey(ByteArrayWrapper.wrap(tx.getTransactionHash())));
            Assert.assertEquals(txMap.get(ByteArrayWrapper.wrap(tx.getTransactionHash())), tx);
        }
    }

    @Test
    public void addRepeatedTxn() {
        Properties config = new Properties();
        config.put("tx-timeout", "10");

        ITxPool tp = new TxPoolA0(config);
        AionTransaction txn =
                AionTransaction.create(
                        key.get(0),
                        ByteUtils.fromHexString("0000000000000001"),
                        new AionAddress(key.get(0).getAddress()),
                        ByteUtils.fromHexString("1"),
                        ByteUtils.fromHexString("1"),
                        10000L,
                        1L,
                        TransactionTypes.DEFAULT);

        List<AionTransaction> txnl = new ArrayList<>();
        txnl.add(txn);
        txnl.add(txn);
        tp.add(txnl);

        Assert.assertEquals(1, tp.size());
    }

    @Test
    public void addRepeatedTxn2() {
        Properties config = new Properties();
        config.put("tx-timeout", "10");

        ITxPool tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 10;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            AionTransaction txn = genTransaction(nonce);
            txn.setNrgConsume(50);
            txnl.add(txn);
        }

        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt);

        byte[] nonce = new byte[Long.BYTES];
        nonce[Long.BYTES - 1] = (byte) 5;
        AionTransaction txn = genTransaction(nonce);
        txn.setNrgConsume(500);
        tp.add(txn);

        List<AionTransaction> snapshot = tp.snapshot();
        Assert.assertEquals(snapshot.size(), cnt);

        Assert.assertEquals(snapshot.get(5), txn);
    }

    @Test
    public void addRepeatedTxn3() {
        Properties config = new Properties();
        config.put("tx-timeout", "10");

        ITxPool tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 10;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            AionTransaction txn = genTransaction(nonce);
            txn.setNrgConsume(50);
            txnl.add(txn);
        }

        tp.add(txnl);
        tp.snapshot();
        Assert.assertEquals(tp.size(), cnt);

        byte[] nonce = new byte[Long.BYTES];
        nonce[Long.BYTES - 1] = (byte) 5;
        AionTransaction txn = genTransaction(nonce);
        txn.setNrgConsume(500);
        tp.add(txn);

        List<AionTransaction> snapshot = tp.snapshot();
        Assert.assertEquals(snapshot.size(), cnt);

        Assert.assertEquals(snapshot.get(5), txn);
    }

    @Test
    // TODO Both transactions will be added to the pool if they are added at the same time. Is this okay?
    public void addTxWithSameNonce() {
        Properties config = new Properties();
        config.put("tx-timeout", "10");
        ITxPool tp = new TxPoolA0(config);

        AionTransaction txn = genTransaction(BigInteger.ONE.toByteArray());
        txn.setNrgConsume(100);
        AionTransaction txn2 = genTransaction(BigInteger.ONE.toByteArray());
        txn2.setNrgConsume(100);

        List<AionTransaction> txnl = new ArrayList<>();

        txnl.add(txn);
        tp.add(txnl);

        txnl.clear();
        txnl.add(txn2);
        tp.add(txnl);

        Assert.assertEquals(1, tp.size());

        List<AionTransaction> txl = tp.snapshot();
        Assert.assertEquals(1, txl.size());
        Assert.assertArrayEquals(txl.get(0).getTimestamp(), txn2.getTimestamp()); // TODO Check this is the expected behaviour
    }

    @Test
    public void noncebyAccountTest() {
        Properties config = new Properties();
        config.put("tx-timeout", "10");

        TxPoolA0 tp = new TxPoolA0(config);

        AionAddress acc = new AionAddress(key.get(0).getAddress());

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 100;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) (i + 1);
            AionTransaction txn =
                    AionTransaction.create(
                            key.get(0),
                            nonce,
                            AddressUtils.wrapAddress(
                                    "0000000000000000000000000000000000000000000000000000000000000001"),
                            ByteUtils.fromHexString("1"),
                            ByteUtils.fromHexString("1"),
                            10000L,
                            1L,
                            TransactionTypes.DEFAULT);
            txn.setNrgConsume(100L);
            txnl.add(txn);
        }
        tp.add(txnl);

        Assert.assertEquals(tp.size(), cnt);

        // sort the inserted txs
        tp.snapshot();

        List<BigInteger> nl = tp.getNonceList(acc);

        for (int i = 0; i < cnt; i++) {
            Assert.assertEquals(nl.get(i), BigInteger.valueOf(i + 1));
        }
    }

    @Test
    public void noncebyAccountTest2() {
        Properties config = new Properties();
        config.put("tx-timeout", "10");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 100;
        for (ECKey aKey1 : key) {
            for (int i = 0; i < cnt; i++) {
                byte[] nonce = new byte[Long.BYTES];
                nonce[Long.BYTES - 1] = (byte) (i + 1);
                AionTransaction txn =
                        AionTransaction.create(
                                aKey1,
                                nonce,
                                AddressUtils.wrapAddress(
                                        "0000000000000000000000000000000000000000000000000000000000000001"),
                                ByteUtils.fromHexString("1"),
                                ByteUtils.fromHexString("1"),
                                10000L,
                                1L,
                                TransactionTypes.DEFAULT);
                txn.setNrgConsume(100L);
                txnl.add(txn);
            }
        }

        tp.add(txnl);

        Assert.assertEquals(tp.size(), cnt * key.size());

        // sort the inserted txs
        tp.snapshot();

        for (ECKey aKey : key) {
            List<BigInteger> nl = tp.getNonceList(new AionAddress(aKey.getAddress()));
            for (int i = 0; i < cnt; i++) {
                Assert.assertEquals(nl.get(i), BigInteger.valueOf(i + 1));
            }
        }
    }

    @Test
    public void feemapTest() {
        Properties config = new Properties();
        config.put("tx-timeout", "10");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 100;
        byte[] nonce = new byte[Long.BYTES];
        for (int i = 0; i < cnt; i++) {
            nonce[Long.BYTES - 1] = (byte) i;

            AionTransaction txn =
                    AionTransaction.create(
                            key2.get(i),
                            nonce,
                            AddressUtils.wrapAddress(
                                    "0000000000000000000000000000000000000000000000000000000000000001"),
                            ByteUtils.fromHexString("1"),
                            ByteUtils.fromHexString("1"),
                            10000L,
                            1L,
                            TransactionTypes.DEFAULT);

            txn.setNrgConsume(i + 1);
            txnl.add(txn);
        }
        tp.add(txnl);

        Assert.assertEquals(tp.size(), cnt);

        // sort the inserted txs
        tp.snapshot();

        List<BigInteger> nl = tp.getFeeList();

        long val = 100;
        for (int i = 0; i < cnt; i++) {
            Assert.assertEquals(0, nl.get(i).compareTo(BigInteger.valueOf(val--)));
        }
    }

    @Test
    public void TxnfeeCombineTest() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 10;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) (i + 1);

            AionTransaction txn = genTransaction(nonce);

            txn.setNrgConsume(i + 1);
            txnl.add(txn);
        }
        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt);

        // sort the inserted txs
        tp.snapshot();

        List<BigInteger> nl = tp.getFeeList();

        Assert.assertEquals(1, nl.size());
        Assert.assertEquals(0, nl.get(0).compareTo(BigInteger.valueOf(55 / 10)));
    }

    @Test
    public void TxnfeeCombineTest2() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 17;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) (i + 1);

            AionTransaction txn = genTransaction(nonce);
            txn.setNrgConsume(i + 1);
            txnl.add(txn);
        }
        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt);

        // sort the inserted txs
        tp.snapshot();

        List<BigInteger> nl = tp.getFeeList();

        Assert.assertEquals(2, nl.size());
        Assert.assertEquals(0, nl.get(0).compareTo(BigInteger.valueOf(17)));
        Assert.assertEquals(0, nl.get(1).compareTo(BigInteger.valueOf(136 / 16)));
    }

    @Test
    // @Ignore
    /* 100K new transactions in pool around 1200ms (cold-call)
     */ public void benchmarkSnapshot() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 10000;
        for (ECKey aKey1 : key) {
            for (int i = 0; i < cnt; i++) {
                AionTransaction txn =
                        AionTransaction.create(
                                aKey1,
                                BigInteger.valueOf(i).toByteArray(),
                                AddressUtils.wrapAddress(
                                        "0000000000000000000000000000000000000000000000000000000000000001"),
                                ByteUtils.fromHexString("1"),
                                ByteUtils.fromHexString("1"),
                                10000L,
                                1L,
                                TransactionTypes.DEFAULT);
                txn.setNrgConsume(100L);
                txnl.add(txn);
            }
        }

        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt * key.size());

        // sort the inserted txs
        long start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        for (ECKey aKey : key) {
            List<BigInteger> nl = tp.getNonceList(new AionAddress(aKey.getAddress()));
            for (int i = 0; i < cnt; i++) {
                Assert.assertEquals(nl.get(i), BigInteger.valueOf(i));
            }
        }
    }

    @Test
    /* 100K new transactions in pool around 650ms (cold-call)

      1K new transactions insert to the pool later around 150ms to snap (including sort)
    */ public void benchmarkSnapshot2() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 10000;
        for (ECKey aKey2 : key) {
            for (int i = 0; i < cnt; i++) {
                AionTransaction txn =
                        AionTransaction.create(
                                aKey2,
                                BigInteger.valueOf(i).toByteArray(),
                                AddressUtils.wrapAddress(
                                        "0000000000000000000000000000000000000000000000000000000000000001"),
                                ByteUtils.fromHexString("1"),
                                ByteUtils.fromHexString("1"),
                                10000L,
                                1L,
                                TransactionTypes.DEFAULT);
                txn.setNrgConsume(100L);
                txnl.add(txn);
            }
        }

        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt * key.size());

        // sort the inserted txs
        long start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        int cnt2 = 100;
        txnl.clear();
        for (ECKey aKey1 : key) {
            for (int i = 0; i < cnt2; i++) {
                AionTransaction txn =
                        AionTransaction.create(
                                aKey1,
                                BigInteger.valueOf(cnt + i).toByteArray(),
                                AddressUtils.wrapAddress(
                                        "0000000000000000000000000000000000000000000000000000000000000001"),
                                ByteUtils.fromHexString("1"),
                                ByteUtils.fromHexString("1"),
                                10000L,
                                1L,
                                TransactionTypes.DEFAULT);
                txn.setNrgConsume(100L);
                txnl.add(txn);
            }
        }

        tp.add(txnl);
        Assert.assertEquals(tp.size(), (cnt + cnt2) * key.size());

        start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("2nd time spent: " + (System.currentTimeMillis() - start) + " ms.");

        for (ECKey aKey : key) {
            List<BigInteger> nl = tp.getNonceList(new AionAddress(aKey.getAddress()));
            for (int i = 0; i < cnt + cnt2; i++) {
                Assert.assertEquals(nl.get(i), BigInteger.valueOf(i));
            }
        }
    }

    @Test
    @Ignore
    /* 1M new transactions with 10000 accounts (100 txs per account)in pool snapshot around 10s (cold-call)
      gen new txns 55s (spent a lot of time to sign tx)
      put txns into pool 2.5s
      snapshot txn 5s
    */ public void benchmarkSnapshot3() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 100;
        System.out.println("Gen new transactions --");
        long start = System.currentTimeMillis();
        for (ECKey aKey21 : key2) {
            for (int i = 0; i < cnt; i++) {
                AionTransaction txn =
                        AionTransaction.create(
                                aKey21,
                                BigInteger.valueOf(i).toByteArray(),
                                AddressUtils.wrapAddress(
                                        "0000000000000000000000000000000000000000000000000000000000000001"),
                                ByteUtils.fromHexString("1"),
                                ByteUtils.fromHexString("1"),
                                10000L,
                                1L,
                                TransactionTypes.DEFAULT);
                txn.setNrgConsume(100L);
                txnl.add(txn);
            }
        }
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        System.out.println("Adding transactions into pool--");
        start = System.currentTimeMillis();
        tp.add(txnl);
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        Assert.assertEquals(tp.size(), cnt * key2.size());

        // sort the inserted txs
        System.out.println("Snapshoting --");
        start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        for (ECKey aKey2 : key2) {
            List<BigInteger> nl = tp.getNonceList(new AionAddress(aKey2.getAddress()));
            for (int i = 0; i < cnt; i++) {
                Assert.assertEquals(nl.get(i), BigInteger.valueOf(i));
            }
        }
    }

    @Test
    /* 100K new transactions in pool around 350ms (cold-call)
     */ public void benchmarkSnapshot4() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        List<AionTransaction> txnlrm = new ArrayList<>();
        int cnt = 100000;
        int rmCnt = 10;
        System.out.println("gen new transactions...");
        long start = System.currentTimeMillis();
        for (int i = 0; i < cnt; i++) {
            AionTransaction txn =
                    AionTransaction.create(
                            key.get(0),
                            BigInteger.valueOf(i).toByteArray(),
                            AddressUtils.wrapAddress(
                                    "0000000000000000000000000000000000000000000000000000000000000001"),
                            ByteUtils.fromHexString("1"),
                            ByteUtils.fromHexString("1"),
                            10000L,
                            1L,
                            TransactionTypes.DEFAULT);
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
        Assert.assertEquals(tp.size(), cnt);

        // sort the inserted txs
        System.out.println("Snapshoting...");
        start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        System.out.println("Removing the first 10 txns...");
        start = System.currentTimeMillis();
        List rm = tp.remove(txnlrm);
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");
        Assert.assertEquals(rm.size(), rmCnt);
        Assert.assertEquals(tp.size(), cnt - rmCnt);

        System.out.println("Re-Snapshot after some txns was been removed...");
        start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        List<BigInteger> nl = tp.getNonceList(new AionAddress(key.get(0).getAddress()));
        for (int i = 0; i < nl.size(); i++) {
            Assert.assertEquals(nl.get(i), BigInteger.valueOf(i).add(BigInteger.valueOf(rmCnt)));
        }
    }

    @Test
    /* 100K new transactions in pool around 350ms (cold-call)

      the second time snapshot is around 35ms
    */ public void benchmarkSnapshot5() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolA0 tp = new TxPoolA0(config);

        List<AionTransaction> txnl = new ArrayList<>();
        int cnt = 10000;
        for (ECKey aKey1 : key) {
            for (int i = 0; i < cnt; i++) {
                AionTransaction txn =
                        AionTransaction.create(
                                aKey1,
                                BigInteger.valueOf(i).toByteArray(),
                                AddressUtils.wrapAddress(
                                        "0000000000000000000000000000000000000000000000000000000000000001"),
                                ByteUtils.fromHexString("1"),
                                ByteUtils.fromHexString("1"),
                                10000L,
                                1L,
                                TransactionTypes.DEFAULT);
                txn.setNrgConsume(100L);
                txnl.add(txn);
            }
        }

        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt * key.size());

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
            List<BigInteger> nl = tp.getNonceList(new AionAddress(aKey.getAddress()));
            for (int i = 0; i < cnt; i++) {
                Assert.assertEquals(nl.get(i), BigInteger.valueOf(i));
            }
        }
    }

    @Test
    public void testSnapshotAll() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey key = ECKeyFac.inst().create();

        List<AionTransaction> txs = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            AionTransaction tx =
                    AionTransaction.create(
                            key,
                            BigInteger.valueOf(i).toByteArray(),
                            AddressUtils.wrapAddress(
                                    "0000000000000000000000000000000000000000000000000000000000000001"),
                            ByteUtils.fromHexString("1"),
                            ByteUtils.fromHexString("1"),
                            10000L,
                            1L,
                            TransactionTypes.DEFAULT);
            tx.setNrgConsume(1);
            txs.add(tx);
        }

        Properties config = new Properties();
        ITxPool tp = new TxPoolA0(config);

        tp.add(txs.subList(0, 500));
        assertEquals(500, tp.snapshot().size());
        assertEquals(500, tp.snapshotAll().size());

        tp.remove(txs.subList(0, 100));
        assertEquals(400, tp.snapshot().size());
        assertEquals(400, tp.snapshotAll().size());
    }

    @Test
    public void testSnapshotAll2() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey key = ECKeyFac.inst().create();

        List<AionTransaction> txs = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            AionTransaction tx =
                    AionTransaction.create(
                            key,
                            BigInteger.valueOf(i).toByteArray(),
                            AddressUtils.wrapAddress(
                                    "0000000000000000000000000000000000000000000000000000000000000001"),
                            ByteUtils.fromHexString("1"),
                            ByteUtils.fromHexString("1"),
                            10000L,
                            1L,
                            TransactionTypes.DEFAULT);
            tx.setNrgConsume(1);
            txs.add(tx);
        }

        Properties config = new Properties();
        ITxPool tp = new TxPoolA0(config);

        tp.add(txs.subList(0, 17));
        assertEquals(17, tp.snapshot().size());
        assertEquals(17, tp.snapshotAll().size());
    }

    @Test
    public void testRemove2() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey key = ECKeyFac.inst().create();

        List<AionTransaction> txs = new ArrayList<>();
        for (int i = 0; i < 95; i++) {
            AionTransaction tx =
                    AionTransaction.create(
                            key,
                            BigInteger.valueOf(i).toByteArray(),
                            AddressUtils.wrapAddress(
                                    "0000000000000000000000000000000000000000000000000000000000000001"),
                            ByteUtils.fromHexString("1"),
                            ByteUtils.fromHexString("1"),
                            10000L,
                            1L,
                            TransactionTypes.DEFAULT);
            tx.setNrgConsume(100L);
            txs.add(tx);
        }

        Properties config = new Properties();
        ITxPool tp = new TxPoolA0(config);

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
