package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.avm.provider.schedule.AvmVersionSchedule;
import org.aion.avm.provider.types.AvmConfigurations;
import org.aion.avm.stub.IEnergyRules;
import org.aion.avm.stub.IEnergyRules.TransactionType;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.crypto.ECKey;
import org.aion.vm.common.TxNrgRule;
import org.aion.zero.impl.core.ImportResult;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.AddressUtils;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.vm.AvmPathManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BlockchainEnergyTest {

    @Before
    public void setup() {
        // Configure the avm if it has not already been configured.
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForOnlySingleVersionSupport(0, 0);
        String projectRoot = AvmPathManager.getPathOfProjectRootDirectory();
        IEnergyRules energyRules = (t, l) -> {
            if (t == TransactionType.CREATE) {
                return TxNrgRule.isValidNrgContractCreate(l);
            } else {
                return TxNrgRule.isValidNrgTx(l);
            }
        };

        AvmConfigurations.initializeConfigurationsAsReadAndWriteable(schedule, projectRoot, energyRules);
    }

    @After
    public void tearDown() {
        AvmConfigurations.clear();
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

        // TODO: where is the 21000 defined? bad to define magic variables
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
                            21000L,
                            BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(9)).longValue(),
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
