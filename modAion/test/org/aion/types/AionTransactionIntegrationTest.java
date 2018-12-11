package org.aion.types;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.zero.types.AionTransaction;
import org.junit.Test;

import java.math.BigInteger;

public class AionTransactionIntegrationTest {
    @Test
    public void testCase1() {
        byte[] nonce = BigInteger.ZERO.toByteArray();
        Address to = new Address("0xA0e855B1173E17D87Bb63e33fdFF36E6494fD2CAC42bc7f80d0a093Ee29d263A");
        byte[] value = ByteUtil.bigIntegerToBytes(new BigInteger("203261307462270040000000000"));
        System.out.println(ByteUtil.toHexString(value));
        byte[] data = null;
        long nrg = 2000000L;
        long nrgPrice = 10000000000L;
        AionTransaction tx = new AionTransaction(nonce, to, value, data, nrg, nrgPrice);
        System.out.println(ByteUtil.toHexString(tx.getEncodedRaw()));
    }
}
