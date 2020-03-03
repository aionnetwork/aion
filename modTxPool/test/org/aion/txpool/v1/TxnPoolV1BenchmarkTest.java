package org.aion.txpool.v1;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import org.aion.base.AionTransaction;
import org.aion.base.PooledTransaction;
import org.aion.base.TransactionTypes;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.txpool.Constant;
import org.aion.txpool.Constant.TXPOOL_PROPERTY;
import org.aion.types.AionAddress;
import org.aion.util.types.AddressUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

public class TxnPoolV1BenchmarkTest {

    private List<ECKey> key;
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
    }

    @Test
    public void benchmarkSnapshot() {
        Properties config = new Properties();
        config.put(TXPOOL_PROPERTY.PROP_TX_TIMEOUT, "100");
        config.put(TXPOOL_PROPERTY.PROP_POOL_SIZE_MAX, "10000");

        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
        int cnt = 1000;
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
                                Constant.MIN_ENERGY_CONSUME,
                                1L,
                                TransactionTypes.DEFAULT,
                                null);
                PooledTransaction pooledTx = new PooledTransaction(txn, Constant.MIN_ENERGY_CONSUME);
                txnl.add(pooledTx);
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
    public void benchmarkSnapshot2() {
        Properties config = new Properties();
        config.put(TXPOOL_PROPERTY.PROP_TX_TIMEOUT, "100");
        config.put(TXPOOL_PROPERTY.PROP_POOL_SIZE_MAX, "10100");

        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
        int cnt = 1000;
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
                                Constant.MIN_ENERGY_CONSUME,
                                1L,
                                TransactionTypes.DEFAULT,
                                null);
                PooledTransaction pooledTx = new PooledTransaction(txn, Constant.MIN_ENERGY_CONSUME);
                txnl.add(pooledTx);
            }
        }

        tp.add(txnl);
        Assert.assertEquals(tp.size(), cnt * key.size());

        // sort the inserted txs
        long start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        int cnt2 = 10;
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
                                Constant.MIN_ENERGY_CONSUME,
                                1L,
                                TransactionTypes.DEFAULT,
                                null);
                PooledTransaction pooledTx = new PooledTransaction(txn, Constant.MIN_ENERGY_CONSUME);
                txnl.add(pooledTx);
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
    public void benchmarkSnapshot3() {
        Properties config = new Properties();
        config.put(TXPOOL_PROPERTY.PROP_TX_TIMEOUT, "100");
        config.put(TXPOOL_PROPERTY.PROP_POOL_SIZE_MAX, "10000");


        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
        int cnt = 1000;
        System.out.println("Gen new transactions --");
        long start = System.currentTimeMillis();
        for (ECKey aKey21 : key) {
            for (int i = 0; i < cnt; i++) {
                AionTransaction txn =
                        AionTransaction.create(
                                aKey21,
                                BigInteger.valueOf(i).toByteArray(),
                                AddressUtils.wrapAddress(
                                        "0000000000000000000000000000000000000000000000000000000000000001"),
                                ByteUtils.fromHexString("1"),
                                ByteUtils.fromHexString("1"),
                                Constant.MIN_ENERGY_CONSUME,
                                1L,
                                TransactionTypes.DEFAULT,
                                null);
                PooledTransaction pooledTx = new PooledTransaction(txn, Constant.MIN_ENERGY_CONSUME);
                txnl.add(pooledTx);
            }
        }
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        System.out.println("Adding transactions into pool--");
        start = System.currentTimeMillis();
        tp.add(txnl);
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        Assert.assertEquals(tp.size(), cnt * key.size());

        // sort the inserted txs
        System.out.println("Snapshoting --");
        start = System.currentTimeMillis();
        tp.snapshot();
        System.out.println("time spent: " + (System.currentTimeMillis() - start) + " ms.");

        for (ECKey aKey2 : key) {
            List<BigInteger> nl = tp.getNonceList(new AionAddress(aKey2.getAddress()));
            for (int i = 0; i < cnt; i++) {
                Assert.assertEquals(nl.get(i), BigInteger.valueOf(i));
            }
        }
    }

    @Test
    /* 100K new transactions in pool around 350ms (cold-call)
     */
    public void benchmarkSnapshot4() {
        Properties config = new Properties();
        config.put(TXPOOL_PROPERTY.PROP_TX_TIMEOUT, "100");
        config.put(TXPOOL_PROPERTY.PROP_POOL_SIZE_MAX, "10000");

        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
        List<PooledTransaction> txnlrm = new ArrayList<>();
        int cnt = 1000;
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
                            Constant.MIN_ENERGY_CONSUME,
                            1L,
                            TransactionTypes.DEFAULT,
                            null);
            PooledTransaction pooledTx = new PooledTransaction(txn, Constant.MIN_ENERGY_CONSUME);
            txnl.add(pooledTx);

            if (i < rmCnt) {
                txnlrm.add(pooledTx);
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
    */
    public void benchmarkSnapshot5() {
        Properties config = new Properties();
        config.put(TXPOOL_PROPERTY.PROP_TX_TIMEOUT, "100");
        config.put(TXPOOL_PROPERTY.PROP_POOL_SIZE_MAX, "10000");

        TxPoolV1 tp = new TxPoolV1(config);

        List<PooledTransaction> txnl = new ArrayList<>();
        int cnt = 1000;
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
                                Constant.MIN_ENERGY_CONSUME,
                                1L,
                                TransactionTypes.DEFAULT,
                                null);
                PooledTransaction pooledTx = new PooledTransaction(txn, Constant.MIN_ENERGY_CONSUME);
                txnl.add(pooledTx);
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
}
