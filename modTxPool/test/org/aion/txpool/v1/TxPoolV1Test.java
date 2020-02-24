package org.aion.txpool.v1;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.aion.base.AionTransaction;
import org.aion.base.PooledTransaction;
import org.aion.base.TransactionTypes;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.txpool.Constant;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.time.TimeInstant;
import org.aion.util.types.AddressUtils;
import org.aion.util.types.ByteArrayWrapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

public class TxPoolV1Test {

    private List<ECKey> key;
    private List<ECKey> key2;
    private Random r = new Random();

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
    public void add1() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolV1 tp = new TxPoolV1(config);
        List<PooledTransaction> txnl = getMockTransaction(0);

        tp.add(txnl);

        Assert.assertEquals(1, tp.size());
    }

    @Test
    public void add2() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolV1 tp = new TxPoolV1(config);
        PooledTransaction pooledTx = genTransaction(new byte[0], 0);

        tp.add(pooledTx);

        Assert.assertEquals(1, tp.size());
    }

    @Test(expected = NullPointerException.class)
    public void add3() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolV1 tp = new TxPoolV1(config);
        List<PooledTransaction> txl = null;

        tp.add(txl);
    }

    private List<PooledTransaction> getMockTransaction(long energyConsumed) {
        AionTransaction tx =
                AionTransaction.create(
                        key.get(0),
                        ByteUtils.fromHexString("0000000000000001"),
                        AddressUtils.wrapAddress(
                                "0000000000000000000000000000000000000000000000000000000000000001"),
                        ByteUtils.fromHexString("1"),
                        ByteUtils.fromHexString("1"),
                        Constant.MIN_ENERGY_CONSUME,
                        1L,
                        TransactionTypes.DEFAULT,
                        null);

        return Collections.singletonList(new PooledTransaction(tx, energyConsumed));
    }

    @Test
    public void remove() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolV1 tp = new TxPoolV1(config);
        List<PooledTransaction> txnl = getMockTransaction(0);
        tp.add(txnl);
        Assert.assertEquals(1, tp.size());

        tp.remove(txnl);
        Assert.assertEquals(0, tp.size());
    }

    @Test
    public void remove2() {
        Properties config = new Properties();
        config.put("tx-timeout", "100"); // 100 sec

        TxPoolV1 tp = new TxPoolV1(config);
        List<PooledTransaction> txl = new ArrayList<>();
        List<PooledTransaction> txlrm = new ArrayList<>();
        int cnt = 20;
        for (int i = 0; i < cnt; i++) {
            PooledTransaction tx =
                    genTransaction(BigInteger.valueOf(i).toByteArray(), Constant.MIN_ENERGY_CONSUME);
            txl.add(tx);
            if (i < 10) {
                txlrm.add(tx);
            }
        }

        List<PooledTransaction> rtn = tp.add(txl);
        Assert.assertEquals(rtn.size(), txl.size());

        List<AionTransaction> snapshot = tp.snapshot();
        Assert.assertEquals(snapshot.size(), cnt);

        rtn = tp.remove(txlrm);
        Assert.assertEquals(10, rtn.size());
        Assert.assertEquals(10, tp.size());
    }

    @Test
    public void remove3() {
        Properties config = new Properties();
        config.put("tx-timeout", "100"); // 100 sec

        TxPoolV1 txPool = new TxPoolV1(config);
        List<PooledTransaction> txl = new ArrayList<>();
        int cnt = 20;
        for (int i = 0; i < cnt; i++) {
            PooledTransaction tx =
                    genTransaction(BigInteger.valueOf(i).toByteArray(), Constant.MIN_ENERGY_CONSUME);
            txl.add(tx);
        }

        List<PooledTransaction> added = txPool.add(txl);
        Assert.assertEquals(added.size(), txl.size());

        List<AionTransaction> snapshot = txPool.snapshot();
        Assert.assertEquals(snapshot.size(), cnt);

        // We pass nonce 11 to the remove function, which should also remove any tx from the same
        // sender with a lower nonce
        Map<AionAddress, BigInteger> txWithNonceEleven = new HashMap<>();
        txWithNonceEleven.put(snapshot.get(0).getSenderAddress(), BigInteger.valueOf(11));
        List<PooledTransaction> removed = txPool.removeTxsWithNonceLessThan(txWithNonceEleven);
        assertEquals(11, removed.size());
        assertEquals(9, txPool.size());
        assertEquals(txPool.snapshot().get(0), txl.get(11).tx);
        assertEquals(txPool.snapshot().get(0).getNonceBI(), BigInteger.valueOf(11));
    }

    @Test
    public void remove4() {
        Properties config = new Properties();
        config.put("tx-timeout", "100"); // 100 sec

        TxPoolV1 txPool = new TxPoolV1(config);
        List<PooledTransaction> txl = new ArrayList<>();
        int cnt = 20;
        for (int i = 0; i < cnt; i++) {
            PooledTransaction tx = genTransaction(BigInteger.valueOf(i).toByteArray(), 5000L);
            txl.add(tx);
        }

        List<PooledTransaction> rtn = txPool.add(txl);
        Assert.assertEquals(rtn.size(), txl.size());

        List<AionTransaction> snapshot = txPool.snapshot();
        Assert.assertEquals(snapshot.size(), cnt);

        // We will remove the tx with nonce 10, which should only remove the given tx
        PooledTransaction txWithNonceTen = txl.get(10);
        PooledTransaction removed = txPool.remove(txWithNonceTen);
        assertEquals(txWithNonceTen, removed);
        assertEquals(19, txPool.size());
    }

    private PooledTransaction genTransaction(byte[] nonce, long energyConsumed) {
        AionTransaction tx =
                AionTransaction.create(
                        key.get(0),
                        nonce,
                        AddressUtils.wrapAddress(
                                "0000000000000000000000000000000000000000000000000000000000000001"),
                        ByteUtils.fromHexString("1"),
                        ByteUtils.fromHexString("1"),
                        Constant.MIN_ENERGY_CONSUME,
                        1L,
                        TransactionTypes.DEFAULT,
                        null);
        return new PooledTransaction(tx, energyConsumed);
    }

    private PooledTransaction genTransactionWithEnergyPrice(byte[] nonce, long energyPrice) {
        AionTransaction tx =
                AionTransaction.create(
                        key.get(0),
                        nonce,
                        AddressUtils.wrapAddress(
                                "0000000000000000000000000000000000000000000000000000000000000001"),
                        ByteUtils.fromHexString("1"),
                        ByteUtils.fromHexString("1"),
                        Constant.MIN_ENERGY_CONSUME,
                        energyPrice,
                        TransactionTypes.DEFAULT,
                        null);
        return new PooledTransaction(tx, Constant.MIN_ENERGY_CONSUME);
    }

    private PooledTransaction genTransaction(byte[] nonce, int _index) {
        AionTransaction tx =
                AionTransaction.create(
                        key.get(_index),
                        nonce,
                        AddressUtils.wrapAddress(
                                "0000000000000000000000000000000000000000000000000000000000000001"),
                        ByteUtils.fromHexString("1"),
                        ByteUtils.fromHexString("1"),
                        Constant.MIN_ENERGY_CONSUME,
                        1L,
                        TransactionTypes.DEFAULT,
                        null);
        return new PooledTransaction(tx, Constant.MIN_ENERGY_CONSUME);
    }

    private PooledTransaction genTransactionWithTimestamp(
        byte[] nonce, ECKey key, byte[] timeStamp) {
        AionTransaction tx =
                AionTransaction.createGivenTimestamp(
                        key,
                        nonce,
                        AddressUtils.wrapAddress(
                                "0000000000000000000000000000000000000000000000000000000000000001"),
                        ByteUtils.fromHexString("1"),
                        ByteUtils.fromHexString("1"),
                        Constant.MIN_ENERGY_CONSUME,
                        1L,
                        TransactionTypes.DEFAULT,
                        timeStamp,
                        null);
        return new PooledTransaction(tx, Constant.MIN_ENERGY_CONSUME);
    }

    private PooledTransaction genTransactionRandomPrice(
            byte[] nonce, ECKey key, long energyConsumed) {
        AionTransaction tx =
                AionTransaction.create(
                        key,
                        nonce,
                        AddressUtils.wrapAddress(
                                "0000000000000000000000000000000000000000000000000000000000000001"),
                        ByteUtils.fromHexString("1"),
                        ByteUtils.fromHexString("1"),
                        Constant.MIN_ENERGY_CONSUME,
                        1 + r.nextInt(1000),
                        TransactionTypes.DEFAULT,
                        null);
        return new PooledTransaction(tx, energyConsumed);
    }

    @Test
    public void timeout1() {
        Properties config = new Properties();
        config.put("tx-timeout", "10"); // 10 sec

        TxPoolV1 tp = new TxPoolV1(config);
        List<PooledTransaction> txnl = getMockTransaction(30000L);
        tp.add(txnl);

        tp.snapshot(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + 10);
        Assert.assertEquals(1, tp.size());

        tp.snapshot(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + 11);
        Assert.assertEquals(0, tp.size());
    }

    @Test
    public void timeout2() {
        Properties config = new Properties();
        config.put("tx-timeout", "1"); // still 10 sec

        TxPoolV1 tp = new TxPoolV1(config);
        List<PooledTransaction> txnl = getMockTransaction(30000L);
        tp.add(txnl);

        tp.snapshot(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + 10);
        Assert.assertEquals(1, tp.size());

        tp.snapshot(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + 11);
        Assert.assertEquals(0, tp.size());
    }

    @Test
    public void snapshot() {
        Properties config = new Properties();
        config.put("tx-timeout", "10"); // 10 sec

        TxPoolV1 tp = new TxPoolV1(config);
        List<PooledTransaction> txnl = getMockTransaction(0);
        tp.add(txnl);

        tp.snapshot();
        Assert.assertEquals(1, tp.size());
    }

    @Test
    public void snapshot2() {
        Properties config = new Properties();
        config.put("tx-timeout", "100"); // 100 sec

        TxPoolV1 tp = new TxPoolV1(config);
        List<PooledTransaction> txl = new ArrayList<>();
        int cnt = 26;
        for (int i = 0; i < cnt; i++) {
            PooledTransaction txe = genTransaction(BigInteger.valueOf(i).toByteArray(), 5000L);
            txl.add(txe);
        }

        List<PooledTransaction> rtn = tp.add(txl);
        Assert.assertEquals(rtn.size(), txl.size());

        List<AionTransaction> snapshot = tp.snapshot();
        Assert.assertEquals(snapshot.size(), cnt);
    }

    @Test
    public void snapshot3() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
        int cnt = 26;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            PooledTransaction txn = genTransaction(nonce, i + Constant.MIN_ENERGY_CONSUME);
            txnl.add(txn);
        }
        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt);

        // sort the inserted txs
        List<AionTransaction> snapshot = tp.snapshot();

        long nonce = 0;
        for (AionTransaction tx : snapshot) {
            Assert.assertEquals(tx.getNonceBI().longValue(), nonce++);
        }
    }

    @Test
    public void snapshot4() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
        int cnt = 26;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            PooledTransaction txn = genTransaction(nonce, Constant.MIN_ENERGY_CONSUME + (1000L - i));
            txnl.add(txn);
        }
        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt);

        // sort the inserted txs
        List<AionTransaction> snapshot = tp.snapshot();

        long nonce = 0;
        for (AionTransaction tx : snapshot) {
            Assert.assertEquals(tx.getNonceBI().longValue(), nonce++);
        }
    }

    @Test
    public void snapshot5() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
        int cnt = 100;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            PooledTransaction txn = genTransaction(nonce, Constant.MIN_ENERGY_CONSUME + r.nextInt(1000));
            txnl.add(txn);
        }
        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt);

        // sort the inserted txs
        List<AionTransaction> snapshot = tp.snapshot();
        Assert.assertEquals(tp.size(), snapshot.size());
        Assert.assertEquals(tp.snapshotAll().size(), snapshot.size());

        long nonce = 0;
        for (AionTransaction tx : snapshot) {
            Assert.assertEquals(tx.getNonceBI().longValue(), nonce++);
        }
    }

    @Test
    public void snapshot6() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
        int cnt = 200;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            PooledTransaction txn =
                    genTransactionRandomPrice(
                            nonce, key.get(0), Constant.MIN_ENERGY_CONSUME + r.nextInt(1000));
            txnl.add(txn);
        }
        tp.add(txnl);
        Assert.assertEquals(cnt, tp.size());

        List<AionTransaction> txl = tp.snapshot();
        List<AionTransaction> allSnapshotTx = new ArrayList<>(txl);
        while (!txl.isEmpty() && cnt-- != 0) {
            List<PooledTransaction> transactions = new ArrayList<>();
            for (AionTransaction tx : txl) {
                transactions.add(new PooledTransaction(tx, Constant.MIN_ENERGY_CONSUME));
            }

            List<PooledTransaction> pTxs = tp.remove(transactions);
            Assert.assertEquals(transactions.size(), pTxs.size());

            txl = tp.snapshot();
            allSnapshotTx.addAll(txl);
        }

        Assert.assertEquals(0, tp.size());

        long nonce = 0;
        for (AionTransaction tx : allSnapshotTx) {
            Assert.assertEquals(tx.getNonceBI().longValue(), nonce++);
        }
    }

    @Test
    public void snapshot7() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
        Map<ByteArrayWrapper, PooledTransaction> txMap = new HashMap<>();

        int cnt = 25;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            PooledTransaction pooledTx = genTransaction(nonce, 0);

            txnl.add(pooledTx);
            txMap.put(ByteArrayWrapper.wrap(pooledTx.tx.getTransactionHash()), pooledTx);
        }
        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt);

        List<PooledTransaction> txnl2 = new ArrayList<>();
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            PooledTransaction pooledTxn = genTransaction(nonce, 1);

            txnl2.add(pooledTxn);
            txMap.put(ByteArrayWrapper.wrap(pooledTxn.tx.getTransactionHash()), pooledTxn);
        }
        tp.add(txnl2);
        Assert.assertEquals(tp.size(), cnt * 2);

        // sort the inserted txs
        List<AionTransaction> snapshot = tp.snapshot();
        Assert.assertEquals(tp.size(), snapshot.size());
        Assert.assertEquals(tp.snapshotAll().size(), snapshot.size());

        for (AionTransaction tx : snapshot) {
            assertTrue(txMap.containsKey(ByteArrayWrapper.wrap(tx.getTransactionHash())));
            Assert.assertEquals(txMap.get(ByteArrayWrapper.wrap(tx.getTransactionHash())).tx, tx);
        }
    }

    @Test
    public void snapshot8() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
        Map<ByteArrayWrapper, PooledTransaction> txMap = new HashMap<>();
        int cnt = 16;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            PooledTransaction pooledTx = genTransaction(nonce, 0);

            txnl.add(pooledTx);
            txMap.put(ByteArrayWrapper.wrap(pooledTx.tx.getTransactionHash()), pooledTx);
        }

        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            PooledTransaction pooledTx = genTransaction(nonce, 1);

            txnl.add(pooledTx);
            txMap.put(ByteArrayWrapper.wrap(pooledTx.tx.getTransactionHash()), pooledTx);
        }

        for (int i = 16; i < 16 + cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            PooledTransaction pooledTx = genTransaction(nonce, 0);

            txnl.add(pooledTx);
            txMap.put(ByteArrayWrapper.wrap(pooledTx.tx.getTransactionHash()), pooledTx);
        }

        for (int i = 16; i < 16 + cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            PooledTransaction pooledTx = genTransaction(nonce, 1);

            txnl.add(pooledTx);
            txMap.put(ByteArrayWrapper.wrap(pooledTx.tx.getTransactionHash()), pooledTx);
        }

        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt * 4);

        // sort the inserted txs
        List<AionTransaction> snapshot = tp.snapshot();
        Assert.assertEquals(tp.size(), snapshot.size());
        Assert.assertEquals(tp.snapshotAll().size(), snapshot.size());

        for (AionTransaction tx : snapshot) {
            assertTrue(txMap.containsKey(ByteArrayWrapper.wrap(tx.getTransactionHash())));
            Assert.assertEquals(txMap.get(ByteArrayWrapper.wrap(tx.getTransactionHash())).tx, tx);
        }
    }

    @Test
    public void snapshotWithSameTransactionTimestamp() {
        Properties config = new Properties();
        config.put("tx-timeout", "100");

        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
        Map<ByteArrayWrapper, PooledTransaction> txMap = new HashMap<>();

        byte[] timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());
        final int cnt = 16;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            PooledTransaction pooledTx =
                    genTransactionWithTimestamp(nonce, key.get(0), timeStamp);

            txnl.add(pooledTx);
            txMap.put(ByteArrayWrapper.wrap(pooledTx.tx.getTransactionHash()), pooledTx);
        }

        timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            PooledTransaction pooledTx =
                    genTransactionWithTimestamp(nonce, key.get(1), timeStamp);

            txnl.add(pooledTx);
            txMap.put(ByteArrayWrapper.wrap(pooledTx.tx.getTransactionHash()), pooledTx);
        }

        timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());
        for (int i = cnt; i < 2 * cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            PooledTransaction pooledTx =
                    genTransactionWithTimestamp(nonce, key.get(0), timeStamp);

            txnl.add(pooledTx);
            txMap.put(ByteArrayWrapper.wrap(pooledTx.tx.getTransactionHash()), pooledTx);
        }

        timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());
        for (int i = cnt; i < 2 * cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            PooledTransaction pooledTx =
                    genTransactionWithTimestamp(nonce, key.get(1), timeStamp);

            txnl.add(pooledTx);
            txMap.put(ByteArrayWrapper.wrap(pooledTx.tx.getTransactionHash()), pooledTx);
        }

        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt * 4);

        // sort the inserted txs
        List<AionTransaction> txl = tp.snapshot();
        Assert.assertEquals(tp.size(), txl.size());
        Assert.assertEquals(tp.snapshotAll().size(), txl.size());

        for (AionTransaction tx : txl) {
            assertTrue(txMap.containsKey(ByteArrayWrapper.wrap(tx.getTransactionHash())));
            Assert.assertEquals(txMap.get(ByteArrayWrapper.wrap(tx.getTransactionHash())).tx, tx);
        }
    }

    @Test
    public void addRepeatedTxn() {
        Properties config = new Properties();
        config.put("tx-timeout", "10");

        TxPoolV1 tp = new TxPoolV1(config);
        AionTransaction txn =
                AionTransaction.create(
                        key.get(0),
                        ByteUtils.fromHexString("0000000000000001"),
                        new AionAddress(key.get(0).getAddress()),
                        ByteUtils.fromHexString("1"),
                        ByteUtils.fromHexString("1"),
                        Constant.MIN_ENERGY_CONSUME,
                        1L,
                        TransactionTypes.DEFAULT,
                        null);

        PooledTransaction pooledTx = new PooledTransaction(txn, 0);

        List<PooledTransaction> txnl = new ArrayList<>();
        txnl.add(pooledTx);
        txnl.add(pooledTx);
        tp.add(txnl);

        Assert.assertEquals(1, tp.size());
    }

    @Test
    public void addRepeatedTxn2() {
        Properties config = new Properties();
        config.put("tx-timeout", "10");

        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
        int cnt = 10;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            PooledTransaction pooledTx = genTransactionWithEnergyPrice(nonce, 1);
            txnl.add(pooledTx);
        }

        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt);

        byte[] nonce = new byte[Long.BYTES];
        nonce[Long.BYTES - 1] = (byte) 5;
        PooledTransaction pooledTx = genTransactionWithEnergyPrice(nonce, 2);
        tp.add(pooledTx);

        List<AionTransaction> snapshot = tp.snapshot();
        Assert.assertEquals(snapshot.size(), cnt);

        Assert.assertEquals(snapshot.get(5), pooledTx.tx);
    }

    @Test
    public void addRepeatedTxn3() {
        Properties config = new Properties();
        config.put("tx-timeout", "10");

        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
        int cnt = 10;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            PooledTransaction pooledTx = genTransactionWithEnergyPrice(nonce, 1);
            txnl.add(pooledTx);
        }

        tp.add(txnl);
        tp.snapshot();
        Assert.assertEquals(tp.size(), cnt);

        byte[] nonce = new byte[Long.BYTES];
        nonce[Long.BYTES - 1] = (byte) 5;
        PooledTransaction pooledTx = genTransactionWithEnergyPrice(nonce, 2);
        tp.add(pooledTx);

        List<AionTransaction> snapshot = tp.snapshot();
        Assert.assertEquals(snapshot.size(), cnt);

        Assert.assertEquals(snapshot.get(5), pooledTx.tx);
    }

    @Test
    public void addTxWithSameNonce() {
        Properties config = new Properties();
        config.put("tx-timeout", "10");
        TxPoolV1 tp = new TxPoolV1(config);

        PooledTransaction pooledTx =
                genTransaction(BigInteger.ONE.toByteArray(), Constant.MIN_ENERGY_CONSUME + 100);
        PooledTransaction pooledTx2 =
                genTransaction(BigInteger.ONE.toByteArray(), Constant.MIN_ENERGY_CONSUME + 101);

        tp.add(Collections.singletonList(pooledTx));
        tp.add(Collections.singletonList(pooledTx2));

        Assert.assertEquals(1, tp.size());

        List<AionTransaction> txl = tp.snapshot();
        Assert.assertEquals(1, txl.size());
        Assert.assertEquals(pooledTx.tx, txl.get(0));
    }

    @Test
    public void noncebyAccountTest() {
        Properties config = new Properties();
        config.put("tx-timeout", "10");

        TxPoolV1 tp = new TxPoolV1(config);

        AionAddress acc = new AionAddress(key.get(0).getAddress());

        List<PooledTransaction> txnl = new ArrayList<>();
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
                            Constant.MIN_ENERGY_CONSUME,
                            1L,
                            TransactionTypes.DEFAULT,
                            null);
            PooledTransaction pooledTx = new PooledTransaction(txn, Constant.MIN_ENERGY_CONSUME + 100);
            txnl.add(pooledTx);
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

        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
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
                                Constant.MIN_ENERGY_CONSUME,
                                1L,
                                TransactionTypes.DEFAULT,
                                null);
                PooledTransaction pooledTx = new PooledTransaction(txn, Constant.MIN_ENERGY_CONSUME + 100);
                txnl.add(pooledTx);
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

        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
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
                            Constant.MIN_ENERGY_CONSUME,
                            i + 1,
                            TransactionTypes.DEFAULT,
                            null);
            PooledTransaction pooledTx = new PooledTransaction(txn, i + Constant.MIN_ENERGY_CONSUME + 1);
            txnl.add(pooledTx);
        }
        tp.add(txnl);
        Assert.assertEquals(cnt, tp.size());

        List<AionTransaction> txSnapshot = tp.snapshot();
        assertEquals(cnt, txSnapshot.size());

        List<Long> nl = tp.getFeeList();
        assertEquals(cnt, nl.size());

        long val = 100;
        for (int i = 0; i < cnt; i++) {
            Assert.assertEquals(0, nl.get(i).compareTo(val--));
        }
    }

    @Test
    public void testSnapshotAll() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey key = ECKeyFac.inst().create();

        List<PooledTransaction> txs = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            AionTransaction tx =
                    AionTransaction.create(
                            key,
                            BigInteger.valueOf(i).toByteArray(),
                            AddressUtils.wrapAddress(
                                    "0000000000000000000000000000000000000000000000000000000000000001"),
                            ByteUtils.fromHexString("1"),
                            ByteUtils.fromHexString("1"),
                            Constant.MIN_ENERGY_CONSUME,
                            1L,
                            TransactionTypes.DEFAULT,
                            null);
            PooledTransaction pooledTx = new PooledTransaction(tx, Constant.MIN_ENERGY_CONSUME);
            txs.add(pooledTx);
        }

        Properties config = new Properties();
        TxPoolV1 tp = new TxPoolV1(config);

        tp.add(txs.subList(0, 476));
        assertEquals(476, tp.snapshot().size());
        assertEquals(476, tp.snapshotAll().size());

        tp.remove(txs.subList(0, 100));
        assertEquals(376, tp.snapshot().size());
        assertEquals(376, tp.snapshotAll().size());
    }

    @Test
    public void testSnapshotAll2() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey key = ECKeyFac.inst().create();

        List<PooledTransaction> txs = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            AionTransaction tx =
                    AionTransaction.create(
                            key,
                            BigInteger.valueOf(i).toByteArray(),
                            AddressUtils.wrapAddress(
                                    "0000000000000000000000000000000000000000000000000000000000000001"),
                            ByteUtils.fromHexString("1"),
                            ByteUtils.fromHexString("1"),
                            Constant.MIN_ENERGY_CONSUME,
                            1L,
                            TransactionTypes.DEFAULT,
                            null);
            PooledTransaction pooledTx = new PooledTransaction(tx, Constant.MIN_ENERGY_CONSUME);
            txs.add(pooledTx);
        }

        Properties config = new Properties();
        TxPoolV1 tp = new TxPoolV1(config);

        tp.add(txs.subList(0, 17));
        assertEquals(17, tp.snapshot().size());
        assertEquals(17, tp.snapshotAll().size());
    }

    @Test
    public void testRemove2() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey key = ECKeyFac.inst().create();

        List<PooledTransaction> txs = new ArrayList<>();
        for (int i = 0; i < 95; i++) {
            AionTransaction tx =
                    AionTransaction.create(
                            key,
                            BigInteger.valueOf(i).toByteArray(),
                            AddressUtils.wrapAddress(
                                    "0000000000000000000000000000000000000000000000000000000000000001"),
                            ByteUtils.fromHexString("1"),
                            ByteUtils.fromHexString("1"),
                            Constant.MIN_ENERGY_CONSUME,
                            1L,
                            TransactionTypes.DEFAULT,
                            null);
            PooledTransaction pooledTx = new PooledTransaction(tx, Constant.MIN_ENERGY_CONSUME);
            txs.add(pooledTx);
        }

        Properties config = new Properties();
        TxPoolV1 tp = new TxPoolV1(config);

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
