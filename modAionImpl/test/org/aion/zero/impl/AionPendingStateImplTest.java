package org.aion.zero.impl;

import java.math.BigInteger;
import org.aion.base.type.Address;
import org.aion.crypto.ecdsa.ECKeySecp256k1;
import org.aion.mcf.blockchain.AddTxResponse;
import org.aion.zero.impl.blockchain.AionPendingStateImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.types.AionTransaction;
import org.junit.Test;

import static org.junit.Assert.*;

public class AionPendingStateImplTest {

    private byte[] defaultHash = "12345".getBytes();

    //TODO: Add more tests
    @Test
    public void testSendTransactionCachedNonce() {

        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;

        AionPendingStateImpl ps = AionPendingStateImpl.createForTesting(
            CfgAion.inst(), chain, chain.getRepository());

        assertNotNull(ps);

        AionTransaction mockTx = new AionTransaction(
            BigInteger.ONE.toByteArray(),
            Address.EMPTY_ADDRESS(),
            Address.EMPTY_ADDRESS(),
            BigInteger.ONE.toByteArray(),
            defaultHash,
            1L,
            1L
        );

        mockTx.sign(new ECKeySecp256k1());

        AddTxResponse rsp = ps.addPendingTransaction(mockTx);

        assertEquals(rsp, AddTxResponse.CACHED_NONCE);
    }
}
