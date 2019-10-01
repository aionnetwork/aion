package org.aion.base;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.math.BigInteger;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.Transaction;
import org.aion.util.types.AddressUtils;
import org.junit.Test;

public class InvokableTransactionTest {

    private final ECKey key = ECKeyFac.inst().create();

    @Test
    public void encodeDecodeTest() {

        Transaction tx = Transaction.contractCallTransaction(
            new AionAddress(key.getAddress()),
            AddressUtils.ZERO_ADDRESS,
            new byte[0],
            BigInteger.ZERO,
            BigInteger.ZERO,
            new byte[0],
            1L,
            1L);

        AionAddress executor = AddressUtils.ZERO_ADDRESS;

        byte[] invokable =
            InvokableTxUtil.encodeInvokableTransaction(
                key,
                tx.nonce,
                tx.destinationAddress,
                tx.value,
                tx.copyOfTransactionData(),
                executor);

        InternalTransaction tx2 = InvokableTxUtil.decode(invokable, AddressUtils.ZERO_ADDRESS, 21000, 1);

        assertNotNull(tx2);
        assertEquals(tx.destinationAddress, tx2.destination);
        assertEquals(tx.nonce, tx2.senderNonce);
        assertEquals(tx.value, tx2.value);
        assertArrayEquals(tx.copyOfTransactionData(), tx2.copyOfData());
    }

    @Test
    public void encodeDecodeContractCreateTest() {

        Transaction tx = Transaction.contractCreateTransaction(
            new AionAddress(key.getAddress()),
            new byte[0],
            BigInteger.ZERO,
            BigInteger.ZERO,
            new byte[0],
            1L,
            1L);

        AionAddress executor = AddressUtils.ZERO_ADDRESS;

        byte[] invokable =
            InvokableTxUtil.encodeInvokableTransaction(
                key,
                tx.nonce,
                tx.destinationAddress,
                tx.value,
                tx.copyOfTransactionData(),
                executor);

        InternalTransaction tx2 = InvokableTxUtil.decode(invokable, AddressUtils.ZERO_ADDRESS, 1L, 1L);

        assertNotNull(tx2);
        assertEquals(tx.destinationAddress, tx2.destination);
        assertEquals(tx.nonce, tx2.senderNonce);
        assertEquals(tx.value, tx2.value);
        assertArrayEquals(tx.copyOfTransactionData(), tx2.copyOfData());
    }

    @Test
    public void nullExecutorTest() {

        Transaction tx = Transaction.contractCallTransaction(
            new AionAddress(key.getAddress()),
            AddressUtils.ZERO_ADDRESS,
            new byte[0],
            BigInteger.ZERO,
            BigInteger.ZERO,
            new byte[0],
            1L,
            1L);

        byte[] invokable =
            InvokableTxUtil.encodeInvokableTransaction(
                key,
                tx.nonce,
                tx.destinationAddress,
                tx.value,
                tx.copyOfTransactionData(),
                null);

        InternalTransaction tx2 = InvokableTxUtil.decode(invokable, getRandomAddress(), 21000, 1);

        assertNotNull(tx2);
        assertEquals(tx.destinationAddress, tx2.destination);
        assertEquals(tx.nonce, tx2.senderNonce);
        assertEquals(tx.value, tx2.value);
        assertArrayEquals(tx.copyOfTransactionData(), tx2.copyOfData());
    }

    @Test
    public void incorrectExecutorTest() {

        byte[] invokable =
            InvokableTxUtil.encodeInvokableTransaction(
                key,
                BigInteger.ZERO,
                AddressUtils.ZERO_ADDRESS,
                BigInteger.ZERO,
                new byte[0],
                getRandomAddress());

        InternalTransaction tx2 = InvokableTxUtil.decode(invokable, getRandomAddress(), 21000, 1);
        InternalTransaction tx3 = InvokableTxUtil.decode(invokable, AddressUtils.ZERO_ADDRESS, 21000, 1);
        assertNull(tx2);
        assertNull(tx3);
    }

    private AionAddress getRandomAddress() {
        return new AionAddress(ECKeyFac.inst().create().getAddress());
    }
}
