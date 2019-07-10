package org.aion.zero.types;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.aion.base.AionTransaction;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.types.AionAddress;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

public class AionTransactionTest {

    private void assertTransactionEquals(AionTransaction tx, AionTransaction tx2) {
        assertArrayEquals(tx.getTransactionHash(), tx2.getTransactionHash());
        assertArrayEquals(tx.getNonce(), tx2.getNonce());
        assertArrayEquals(tx.getValue(), tx2.getValue());
        assertArrayEquals(tx.getData(), tx2.getData());
        assertEquals(tx.getEnergyLimit(), tx2.getEnergyLimit());
        assertEquals(tx.getEnergyPrice(), tx2.getEnergyPrice());
        assertEquals(tx.getTargetVM(), tx2.getTargetVM());

        assertArrayEquals(tx.getTimestamp(), tx2.getTimestamp());
        assertArrayEquals(tx.getSignature().toBytes(), tx2.getSignature().toBytes());

        assertArrayEquals(tx.getEncoded(), tx2.getEncoded());
    }

    @Test
    public void testSerializationZero() {
        byte[] nonce = RandomUtils.nextBytes(16);
        AionAddress to = new AionAddress(RandomUtils.nextBytes(32));
        byte[] value = RandomUtils.nextBytes(16);
        byte[] data = RandomUtils.nextBytes(64);
        long nrg = 0;
        long nrgPrice = 0;
        byte type = 0;

        ECKey key = ECKeyFac.inst().create();
        AionTransaction tx =
                new AionTransaction(
                        nonce,
                        new AionAddress(key.getAddress()),
                        to,
                        value,
                        data,
                        nrg,
                        nrgPrice,
                        type);
        tx.sign(key);

        AionTransaction tx2 = TxUtil.decode(tx.getEncoded());

        assertNotNull(tx2);
        assertTransactionEquals(tx, tx2);
    }

    @Test
    public void testClone() {
        byte[] nonce = RandomUtils.nextBytes(16);
        AionAddress to = new AionAddress(RandomUtils.nextBytes(32));
        byte[] value = RandomUtils.nextBytes(16);
        byte[] data = RandomUtils.nextBytes(64);
        long nrg = RandomUtils.nextLong(0, Long.MAX_VALUE);
        long nrgPrice = RandomUtils.nextLong(0, Long.MAX_VALUE);
        byte type = 1;

        ECKey key = ECKeyFac.inst().create();
        AionTransaction tx =
                new AionTransaction(
                        nonce,
                        new AionAddress(key.getAddress()),
                        to,
                        value,
                        data,
                        nrg,
                        nrgPrice,
                        type);
        tx.sign(key);

        AionTransaction tx2 = tx.clone();

        assertTransactionEquals(tx, tx2);
    }

    @Test
    public void testTransactionCost() {
        byte[] nonce = DataWordImpl.ONE.getData();
        byte[] from = RandomUtils.nextBytes(AionAddress.LENGTH);
        byte[] to = RandomUtils.nextBytes(AionAddress.LENGTH);
        byte[] value = DataWordImpl.ONE.getData();
        byte[] data = RandomUtils.nextBytes(128);
        long nrg = new DataWordImpl(1000L).longValue();
        long nrgPrice = DataWordImpl.ONE.longValue();

        AionTransaction tx =
                new AionTransaction(
                        nonce,
                        new AionAddress(from),
                        new AionAddress(to),
                        value,
                        data,
                        nrg,
                        nrgPrice);

        long expected = 21000;
        for (byte b : data) {
            expected += (b == 0) ? 4 : 64;
        }
        assertEquals(expected, TxUtil.calculateTransactionCost(tx));
    }

    @Test
    public void testTransactionCost2() {
        byte[] nonce = DataWordImpl.ONE.getData();
        byte[] from = RandomUtils.nextBytes(AionAddress.LENGTH);
        AionAddress to = null;
        byte[] value = DataWordImpl.ONE.getData();
        byte[] data = RandomUtils.nextBytes(128);
        long nrg = new DataWordImpl(1000L).longValue();
        long nrgPrice = DataWordImpl.ONE.longValue();

        AionTransaction tx =
                new AionTransaction(nonce, new AionAddress(from), to, value, data, nrg, nrgPrice);

        long expected = 200000 + 21000;
        for (byte b : data) {
            expected += (b == 0) ? 4 : 64;
        }
        assertEquals(expected, TxUtil.calculateTransactionCost(tx));
    }
}
