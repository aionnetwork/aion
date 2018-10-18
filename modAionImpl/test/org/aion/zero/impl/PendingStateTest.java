package org.aion.zero.impl;

import java.math.BigInteger;
import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.mcf.blockchain.TxResponse;
import static org.junit.Assert.*;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.types.AionTransaction;
import org.junit.Test;

public class PendingStateTest {

    @Test
    public void TestAddPendingTransaction() {

        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
            .withValidatorConfiguration("simple")
            .withDefaultAccounts()
            .build();
        StandaloneBlockchain bc = bundle.bc;

        CfgAion.inst().setGenesis(bc.getGenesis());

        AionHub hub = AionHub.createForTesting(CfgAion.inst(), bc, bc.getRepository());

        Address to = new Address(bundle.privateKeys.get(0).getAddress());
        ECKey signer = bundle.privateKeys.get(1);

        // Successful transaction

        AionTransaction tx = new AionTransaction(
            BigInteger.ZERO.toByteArray(),
            to,
            new byte[0],
            new byte[0],
            1_000_000L,
            10_000_000_000L
        );

        tx.sign(signer);

        assertEquals(hub.getPendingState().addPendingTransaction(tx),TxResponse.SUCCESS);

        // Invalid Nrg Price transaction

        tx = new AionTransaction(
            BigInteger.ZERO.toByteArray(),
            to,
            new byte[0],
            new byte[0],
            1_000_000L,
            1L
        );
        tx.sign(signer);

        assertEquals(hub.getPendingState().addPendingTransaction(tx),TxResponse.INVALID_TX_NRG_PRICE);
    }
}
