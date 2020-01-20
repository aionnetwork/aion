package org.aion.zero.impl.vm;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.aion.avm.stub.AvmVersion;
import org.aion.avm.stub.IAvmResourceFactory;
import org.aion.avm.stub.IContractFactory.AvmContract;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.crypto.ECKey;
import org.aion.zero.impl.core.ImportResult;
import org.aion.base.TransactionTypeRule;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.Log;
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

public class AvmLogAndInternalTransactionTest {
    private static TestResourceProvider resourceProvider;
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;
    private long minEnergyPrice = 10_000_000_000L;

    @BeforeClass
    public static void setupAvm() throws Exception {
        resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());
        AvmTestConfig.supportOnlyAvmVersion1();
    }

    @AfterClass
    public static void tearDownAvm() throws Exception {
        TransactionTypeRule.disallowAVMContractTransaction();
        AvmTestConfig.clearConfigurations();
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
    public void testLogAndInternalTransactionsOnSuccess() {
        AvmVersion version = AvmVersion.VERSION_1;

        AionAddress contract = deployContract(version, BigInteger.ZERO);
        AionAddress other = deployContract(version, BigInteger.ONE);

        Pair<ImportResult, AionBlockSummary> connectResult =
                callFireLogs(version, BigInteger.TWO, contract, other, "fireLogsOnSuccess");
        AionBlockSummary summary = connectResult.getRight();

        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        AionTxReceipt receipt = summary.getReceipts().get(0);
        assertTrue(receipt.isSuccessful());

        List<Log> logs = receipt.getLogInfoList();
        List<InternalTransaction> internalTransactions =
                summary.getSummaries().get(0).getInternalTransactions();

        assertEquals(3, logs.size());
        assertEquals(1, internalTransactions.size());
    }

    @Test
    public void testLogAndInternalTransactionsOnFailure() {
        AvmVersion version = AvmVersion.VERSION_1;

        AionAddress contract = deployContract(version, BigInteger.ZERO);
        AionAddress other = deployContract(version, BigInteger.ONE);

        Pair<ImportResult, AionBlockSummary> connectResult =
                callFireLogs(version, BigInteger.TWO, contract, other, "fireLogsAndFail");
        AionBlockSummary summary = connectResult.getRight();

        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        AionTxReceipt receipt = summary.getReceipts().get(0);
        assertFalse(receipt.isSuccessful());

        List<InternalTransaction> internalTransactions =
                summary.getSummaries().get(0).getInternalTransactions();
        List<Log> logs = receipt.getLogInfoList();

        assertEquals(0, logs.size());
        assertEquals(1, internalTransactions.size());
    }

    public Pair<ImportResult, AionBlockSummary> callFireLogs(
            AvmVersion version, BigInteger nonce, AionAddress address, AionAddress addressToCall, String methodName) {

        IAvmResourceFactory factory = (version == AvmVersion.VERSION_1) ? resourceProvider.factoryForVersion1 : resourceProvider.factoryForVersion2;
        byte[] data = factory.newStreamingEncoder().encodeOneString(methodName).encodeOneAddress(addressToCall).getEncoding();

        AionTransaction transaction =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        address,
                        new byte[0],
                        data,
                        2_000_000,
                        minEnergyPrice,
                        TransactionTypes.DEFAULT, null);

        AionBlock block =
                this.blockchain.createBlock(
                        this.blockchain.getBestBlock(),
                        Collections.singletonList(transaction),
                        false,
                        this.blockchain.getBestBlock().getTimestamp());
        return this.blockchain.tryToConnectAndFetchSummary(block);
    }

    public AionAddress deployContract(AvmVersion version, BigInteger nonce) {
        TransactionTypeRule.allowAVMContractTransaction();
        byte[] jar = getJarBytes(version);
        AionTransaction transaction =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        new byte[0],
                        jar,
                        5_000_000,
                        minEnergyPrice,
                        TransactionTypes.AVM_CREATE_CODE, null);

        AionBlock block =
                this.blockchain.createBlock(
                        this.blockchain.getBestBlock(),
                        Collections.singletonList(transaction),
                        false,
                        this.blockchain.getBestBlock().getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult =
                this.blockchain.tryToConnectAndFetchSummary(block);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);

        // Check the block was imported, the contract has the Avm prefix, and deployment succeeded.
        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(receipt.isSuccessful()).isTrue();
        return new AionAddress(receipt.getTransactionOutput());
    }

    private byte[] getJarBytes(AvmVersion version) {
        IAvmResourceFactory factory = (version == AvmVersion.VERSION_1) ? resourceProvider.factoryForVersion1 : resourceProvider.factoryForVersion2;
        return factory.newContractFactory().getDeploymentBytes(AvmContract.LOG_TARGET);
    }
}
