package org.aion.factory;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKeyFac;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;

public class AionTransactionFactoryTest {
    private final AionTransactionFactory transactionFactory = new AionTransactionFactory();

    @Test
    public void createTransaction() {
        Address to = Address.wrap(RandomUtils.nextBytes(32));
        byte[] data = RandomUtils.nextBytes(64);
        AionTransaction tx = transactionFactory.createTransaction(BigInteger.ONE, to, BigInteger.ONE, data);

        assertThat(tx.getNonce()).isEqualTo(ByteUtil.bigIntegerToBytes(BigInteger.ONE));
        assertThat(tx.getTo()).isEqualTo(to);
        assertThat(tx.getValue()).isEqualTo(ByteUtil.bigIntegerToBytes(BigInteger.ONE));
        assertThat(tx.getData()).isEqualTo(data);
    }

    @Test
    public void createTransaction1() {
        Address to = Address.wrap(RandomUtils.nextBytes(32));
        byte[] data = RandomUtils.nextBytes(64);
        byte[] nonce = RandomUtils.nextBytes(16);
        byte[] value = RandomUtils.nextBytes(16);
        long nrg = 30000L;
        long nrgPrice = 1L;

        AionTransaction tx = transactionFactory.createTransaction(nonce, to, value, data, nrg, nrgPrice);

        assertThat(tx.getNonce()).isEqualTo(nonce);
        assertThat(tx.getTo()).isEqualTo(to);
        assertThat(tx.getValue()).isEqualTo(value);
        assertThat(tx.getData()).isEqualTo(data);
        assertThat(tx.getNrg()).isEqualTo(nrg);
        assertThat(tx.getNrgPrice()).isEqualTo(nrgPrice);
    }

    @Test
    public void createTransaction2() {
        Address to = Address.wrap(RandomUtils.nextBytes(32));
        byte[] data = RandomUtils.nextBytes(64);
        byte[] nonce = RandomUtils.nextBytes(16);
        byte[] value = RandomUtils.nextBytes(16);
        long nrg = 30000L;
        long nrgPrice = 1L;

        AionTransaction tx = new AionTransaction(nonce, to, value, data, nrg, nrgPrice);
        tx.sign(ECKeyFac.inst().create());

        byte[] encodedTx = tx.getEncoded();

        AionTransaction tx2 = transactionFactory.createTransaction(encodedTx);

        assertThat(tx2.getNonce()).isEqualTo(nonce);
        assertThat(tx2.getTo()).isEqualTo(to);
        assertThat(tx2.getValue()).isEqualTo(value);
        assertThat(tx2.getData()).isEqualTo(data);
        assertThat(tx2.getNrg()).isEqualTo(nrg);
        assertThat(tx2.getNrgPrice()).isEqualTo(nrgPrice);
    }

    @Test
    public void createTransaction3() {
        Address to = Address.wrap(RandomUtils.nextBytes(32));
        Address from = Address.wrap(RandomUtils.nextBytes(32));
        byte[] data = RandomUtils.nextBytes(64);
        byte[] nonce = RandomUtils.nextBytes(16);
        byte[] value = RandomUtils.nextBytes(16);
        long nrg = 30000L;
        long nrgPrice = 1L;

        AionTransaction tx = transactionFactory.createTransaction(nonce, from, to, value, data, nrg, nrgPrice);

        assertThat(tx.getNonce()).isEqualTo(nonce);
        assertThat(tx.getTo()).isEqualTo(to);
        assertThat(tx.getValue()).isEqualTo(value);
        assertThat(tx.getData()).isEqualTo(data);
        assertThat(tx.getNrgPrice()).isEqualTo(nrgPrice);
        assertThat(tx.getNrg()).isEqualTo(nrg);

    }
}
