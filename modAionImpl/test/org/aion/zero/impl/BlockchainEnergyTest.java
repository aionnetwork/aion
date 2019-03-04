package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.types.Address;
import org.aion.mcf.core.ImportResult;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.junit.Test;

public class BlockchainEnergyTest {

    @Test
    public void testConsistentEnergyUsage() {
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withValidatorConfiguration("simple")
                        .withDefaultAccounts()
                        .build();
        StandaloneBlockchain bc = bundle.bc;

        // in cases where no transactions are included (no energy usage)
        // the default energy limit should persist (it should not degrade)
        AionBlock block = bc.createNewBlock(bc.getBestBlock(), Collections.EMPTY_LIST, true);
        assertThat(block.getNrgLimit()).isEqualTo(bc.getGenesis().getNrgLimit());
    }

    @Test
    public void testEnergyUsageRecorded() {
        final int DEFAULT_TX_AMOUNT = 21000;
        final Address RECEIPT_ADDR =
                Address.wrap(
                        "CAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFE");

        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .build();
        StandaloneBlockchain bc = bundle.bc;

        // TODO: where is the 21000 defined? bad to define magic variables
        int amount = (int) (bc.getGenesis().getNrgLimit() / DEFAULT_TX_AMOUNT);

        // (byte[] nonce, byte[] from, byte[] to, byte[] value, byte[] data, byte[] nrg, byte[]
        // nrgPrice)
        List<AionTransaction> txs = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            // this transaction should send one (1) AION coin from acc[0] to RECEIPT_ADDR
            AionTransaction atx =
                    new AionTransaction(
                            ByteUtil.intToBytes(i),
                            RECEIPT_ADDR,
                            BigInteger.ONE.toByteArray(),
                            ByteUtil.EMPTY_BYTE_ARRAY,
                            21000L,
                            BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(9)).longValue());
            atx.sign(bundle.privateKeys.get(0));
            txs.add(atx);
        }
        AionBlock block = bc.createNewBlock(bc.getBestBlock(), txs, true);
        ImportResult result = bc.tryToConnect(block);

        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        // proceed with connecting the next block, should observe an increase in energyLimit
        AionBlock secondBlock = bc.createNewBlock(bc.getBestBlock(), Collections.EMPTY_LIST, true);
        assertThat(secondBlock.getNrgLimit()).isEqualTo(block.getNrgLimit());
        System.out.println(
                String.format("%d > %d", secondBlock.getNrgLimit(), block.getNrgLimit()));
    }
}
