package org.aion.zero.impl.vm;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.Collections;
import org.aion.avm.provider.schedule.AvmVersionSchedule;
import org.aion.avm.provider.types.AvmConfigurations;
import org.aion.avm.stub.AvmVersion;
import org.aion.avm.stub.IAvmResourceFactory;
import org.aion.avm.stub.IContractFactory.AvmContract;
import org.aion.avm.stub.IEnergyRules;
import org.aion.avm.stub.IEnergyRules.TransactionType;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.ECKey;
import org.aion.vm.common.TxNrgRule;
import org.aion.zero.impl.core.ImportResult;
import org.aion.base.TransactionTypeRule;
import org.aion.types.AionAddress;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.base.AionTxReceipt;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class AvmInternalTxTest {
    private static TestResourceProvider resourceProvider;
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;

    @BeforeClass
    public static void setupAvm() throws Exception {
        resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());

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

    @AfterClass
    public static void tearDownAvm() throws Exception {
        TransactionTypeRule.disallowAVMContractTransaction();
        AvmConfigurations.clear();
        resourceProvider.close();
    }

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .withAvmEnabled()
                        .build();
        this.blockchain = bundle.bc;
        this.deployerKey = bundle.privateKeys.get(0);
    }

    @After
    public void tearDown() {
        this.blockchain = null;
        this.deployerKey = null;
    }

    @Test
    public void testDeployAndCallContract() {
        AvmVersion version = AvmVersion.VERSION_1;

        TransactionTypeRule.allowAVMContractTransaction();
        // Deploy the contract.
        byte[] jar = getJarBytes(version);
        AionTransaction transaction =
                AionTransaction.create(
                        deployerKey,
                        new byte[0],
                        null,
                        new byte[0],
                        jar,
                        5_000_000,
                        1,
                        TransactionTypes.AVM_CREATE_CODE, null);

        AionBlock block =
                this.blockchain.createNewMiningBlock(
                        this.blockchain.getBestBlock(),
                        Collections.singletonList(transaction),
                        false);
        Pair<ImportResult, AionBlockSummary> connectResult =
                this.blockchain.tryToConnectAndFetchSummary(block);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);

        // Check the block was imported, the contract has the Avm prefix, and deployment succeeded.
        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(receipt.getTransactionOutput()[0]).isEqualTo(AddressSpecs.A0_IDENTIFIER);
        assertThat(receipt.isSuccessful()).isTrue();

        AionAddress contract = new AionAddress(receipt.getTransactionOutput());
        // verify that the output is indeed the contract address
        assertThat(TxUtil.calculateContractAddress(transaction)).isEqualTo(contract);

        IAvmResourceFactory factory = (version == AvmVersion.VERSION_1) ? resourceProvider.factoryForVersion1 : null;
        byte[] call = factory.newStreamingEncoder().encodeOneString("recursivelyGetValue").getEncoding();
        makeCall(BigInteger.ONE, contract, call);
    }

    private byte[] getJarBytes(AvmVersion version) {
        IAvmResourceFactory factory = (version == AvmVersion.VERSION_1) ? resourceProvider.factoryForVersion1 : null;
        return factory.newContractFactory().getDeploymentBytes(AvmContract.INTERNAL_TRANSACTION);
    }

    private void makeCall(BigInteger nonce, AionAddress contract, byte[] call) {
        AionTransaction transaction =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        contract,
                        new byte[0],
                        call,
                        2_000_000,
                        1,
                        TransactionTypes.DEFAULT, null);

        AionBlock block =
                this.blockchain.createNewMiningBlock(
                        this.blockchain.getBestBlock(),
                        Collections.singletonList(transaction),
                        false);

        Pair<ImportResult, AionBlockSummary> connectResult =
                this.blockchain.tryToConnectAndFetchSummary(block);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);

        // Check the block was imported and the transaction was successful.
        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(receipt.isSuccessful()).isTrue();
        System.out.println(block);
    }
}
