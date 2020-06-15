package org.aion.zero.impl.types;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.math.BigInteger;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.rlp.RLP;
import org.aion.rlp.SharedRLPList;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.DataWord;
import org.aion.types.AionAddress;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

public class AionTransactionTest {
    private ECKey key = ECKeyFac.inst().create();

    private void assertTransactionEquals(AionTransaction tx, AionTransaction tx2) {
        assertArrayEquals(tx.getTransactionHash(), tx2.getTransactionHash());
        assertArrayEquals(tx.getNonce(), tx2.getNonce());
        assertArrayEquals(tx.getValue(), tx2.getValue());
        assertArrayEquals(tx.getData(), tx2.getData());
        assertEquals(tx.getEnergyLimit(), tx2.getEnergyLimit());
        assertEquals(tx.getEnergyPrice(), tx2.getEnergyPrice());
        assertEquals(tx.getType(), tx2.getType());

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

        AionTransaction tx =
                AionTransaction.create(
                        key,
                        nonce,
                        to,
                        value,
                        data,
                        nrg,
                        nrgPrice,
                        type, null);

        AionTransaction tx2 = TxUtil.decodeUsingRlpSharedList(tx.getEncoded());
        assertNotNull(tx2);
        assertTransactionEquals(tx, tx2);
    }

    @Test
    public void testTransactionCost() {
        byte[] nonce = BigInteger.ONE.toByteArray();
        byte[] to = RandomUtils.nextBytes(AionAddress.LENGTH);
        byte[] value = BigInteger.ONE.toByteArray();
        byte[] data = RandomUtils.nextBytes(128);
        long nrg = new DataWord(1000L).longValue();
        long nrgPrice = DataWord.ONE.longValue();

        AionTransaction tx =
                AionTransaction.create(
                        key,
                        nonce,
                        new AionAddress(to),
                        value,
                        data,
                        nrg,
                        nrgPrice,
                        TransactionTypes.DEFAULT, null);

        long expected = 21000;
        for (byte b : data) {
            expected += (b == 0) ? 4 : 64;
        }
        assertEquals(expected, TxUtil.calculateTransactionCost(tx));
    }

    @Test
    public void testTransactionCost2() {
        byte[] nonce = BigInteger.ONE.toByteArray();
        byte[] value = BigInteger.ONE.toByteArray();
        byte[] data = RandomUtils.nextBytes(128);
        long nrg = new DataWord(1000L).longValue();
        long nrgPrice = DataWord.ONE.longValue();

        AionTransaction tx = AionTransaction.create(
                key,
                nonce,
                null,
                value,
                data,
                nrg,
                nrgPrice,
                TransactionTypes.DEFAULT, null);

        long expected = 200000 + 21000;
        for (byte b : data) {
            expected += (b == 0) ? 4 : 64;
        }
        assertEquals(expected, TxUtil.calculateTransactionCost(tx));
    }
    @Test
    public void testBeaconHashPresent() {
        byte[] nonce = RandomUtils.nextBytes(16);
        AionAddress to = new AionAddress(RandomUtils.nextBytes(32));
        byte[] value = RandomUtils.nextBytes(16);
        byte[] data = RandomUtils.nextBytes(64);
        long nrg = 0;
        long nrgPrice = 0;
        byte type = 0;
        byte[] beaconHash = ByteUtil.hexStringToBytes(
                "0xcafecafecafecafecafecafecafecafecafecafecafecafecafecafecafecafe");

        AionTransaction tx =
                AionTransaction.create(
                        key,
                        nonce,
                        to,
                        value,
                        data,
                        nrg,
                        nrgPrice,
                        type,
                        beaconHash);

        assertArrayEquals(
                "AionTransaction#getBeaconHash should equal the beacon hash provided in ctor",
                beaconHash,
                tx.getBeaconHash());

        SharedRLPList decoded = (SharedRLPList) RLP.decode2SharedList(tx.getEncoded()).get(0);
        assertEquals("wrong number of elements in RLP encoding of AionTransaction with beacon hash",
                TxUtil.RLP_TX_BEACON_HASH,
                decoded.size() - 1
        );
        assertArrayEquals(
                "beacon hash given in AionTransaction ctor should be present in its RLP encoding",
                decoded.get(TxUtil.RLP_TX_BEACON_HASH).getRLPData(),
                beaconHash
        );

        AionTransaction tx2 = TxUtil.decodeUsingRlpSharedList(tx.getEncoded());
        assertNotNull(tx2);
        assertTransactionEquals(tx, tx2);
    }

    @Test
    public void testBeaconHashAbsent() {
        byte[] nonce = RandomUtils.nextBytes(16);
        AionAddress to = new AionAddress(RandomUtils.nextBytes(32));
        byte[] value = RandomUtils.nextBytes(16);
        byte[] data = RandomUtils.nextBytes(64);
        long nrg = 0;
        long nrgPrice = 0;
        byte type = 0;

        AionTransaction tx =
                AionTransaction.create(
                        key,
                        nonce,
                        to,
                        value,
                        data,
                        nrg,
                        nrgPrice,
                        type,
                        null);

        assertNull("beacon hash should be null", tx.getBeaconHash());

        SharedRLPList decoded = (SharedRLPList) RLP.decode2SharedList(tx.getEncoded()).get(0);
        assertEquals("wrong number of elements in RLP encoding of AionTransaction without beacon hash",
                TxUtil.RLP_TX_SIG,
                decoded.size() - 1
        );

        AionTransaction tx2 = TxUtil.decodeUsingRlpSharedList(tx.getEncoded());
        assertNotNull(tx2);
        assertTransactionEquals(tx, tx2);
    }

}
