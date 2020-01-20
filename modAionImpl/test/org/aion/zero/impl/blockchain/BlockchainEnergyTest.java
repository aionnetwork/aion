package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.fastvm.FvmConstants.TRANSACTION_BASE_FEE;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.crypto.ECKey;
import org.aion.zero.impl.core.ImportResult;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.AddressUtils;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.vm.AvmTestConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BlockchainEnergyTest {

    @Before
    public void setup() {
        AvmTestConfig.supportOnlyAvmVersion1();
    }

    @After
    public void tearDown() {
        AvmTestConfig.clearConfigurations();
    }

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
        AionBlock block = bc.createNewMiningBlock(bc.getBestBlock(), Collections.EMPTY_LIST, true);
        assertThat(block.getNrgLimit()).isEqualTo(bc.getGenesis().getNrgLimit());
    }

    @Test
    public void testEnergyUsageRecorded() {
        final int DEFAULT_TX_AMOUNT = 21000;
        final AionAddress RECEIPT_ADDR =
                AddressUtils.wrapAddress(
                        "CAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFE");

        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .build();
        StandaloneBlockchain bc = bundle.bc;

        int amount = (int) (bc.getGenesis().getNrgLimit() / DEFAULT_TX_AMOUNT);

        List<AionTransaction> txs = new ArrayList<>();
        ECKey key = bundle.privateKeys.get(0);
        for (int i = 0; i < amount; i++) {
            // this transaction should send one (1) AION coin from acc[0] to RECEIPT_ADDR
            AionTransaction atx =
                    AionTransaction.create(
                            key,
                            ByteUtil.intToBytes(i),
                            RECEIPT_ADDR,
                            BigInteger.ONE.toByteArray(),
                            ByteUtil.EMPTY_BYTE_ARRAY,
                            TRANSACTION_BASE_FEE,
                            BigInteger.valueOf(10).multiply(BigInteger.TEN.pow(9)).longValue(),
                            TransactionTypes.DEFAULT, null);
            txs.add(atx);
        }
        AionBlock block = bc.createNewMiningBlock(bc.getBestBlock(), txs, true);
        ImportResult result = bc.tryToConnect(block);

        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        // proceed with connecting the next block, should observe an increase in energyLimit
        AionBlock secondBlock = bc.createNewMiningBlock(bc.getBestBlock(), Collections.EMPTY_LIST, true);
        assertThat(secondBlock.getNrgLimit()).isEqualTo(block.getNrgLimit());
        System.out.println(
                String.format("%d > %d", secondBlock.getNrgLimit(), block.getNrgLimit()));
    }
}
