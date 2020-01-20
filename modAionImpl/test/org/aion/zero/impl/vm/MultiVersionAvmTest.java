package org.aion.zero.impl.vm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import org.aion.avm.stub.AvmVersion;
import org.aion.avm.stub.IAvmResourceFactory;
import org.aion.avm.stub.IContractFactory.AvmContract;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypeRule;
import org.aion.base.TransactionTypes;
import org.aion.crypto.ECKey;
import org.aion.mcf.blockchain.Block;
import org.aion.types.AionAddress;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class MultiVersionAvmTest {
    private static long BLOCK_VERSION1_ENABLED = 10;
    private static long BLOCK_VERSION2_ENABLED = 20;
    private static TestResourceProvider resourceProvider;
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;
    private long energyPrice = 10_000_000_000L;

    @BeforeClass
    public static void setupClass() throws Exception {
        TransactionTypeRule.allowAVMContractTransaction();
        resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());

        AvmTestConfig.supportBothAvmVersions(BLOCK_VERSION1_ENABLED, BLOCK_VERSION2_ENABLED, 0);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        TransactionTypeRule.disallowAVMContractTransaction();
        AvmTestConfig.clearConfigurations();
        resourceProvider.close();
    }

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
            .withDefaultAccounts()
            .withValidatorConfiguration("simple")
            .withAvmEnabled()
            .build();
        this.blockchain = bundle.bc;
        this.deployerKey = bundle.privateKeys.get(0);
    }

    /**
     * We test deploying the same contract in version 1 and 2. We expect the same result each time.
     */
    @Test
    public void testDeployInBothAvmVersions() {
        Assert.assertEquals(0, this.blockchain.getBestBlock().getNumber());

        // Ensure we are at a block height where avm version 1 is enabled, then deploy.
        buildBlockchainToHeight(BLOCK_VERSION1_ENABLED);

        AionTransaction transactionForVersion1 = makeHelloWorldDeployTransaction(AvmVersion.VERSION_1, BigInteger.ZERO);

        Block parentBlock = this.blockchain.getBestBlock();
        AionBlock block = this.blockchain.createBlock(parentBlock, Collections.singletonList(transactionForVersion1), false, parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult = this.blockchain.tryToConnectAndFetchSummary(block);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        Assert.assertEquals(1, connectResult.getRight().getReceipts().size());
        Assert.assertTrue(connectResult.getRight().getReceipts().get(0).isSuccessful());

        // Now, climb to a block height where avm version 2 is enabled and deploy.
        buildBlockchainToHeight(BLOCK_VERSION2_ENABLED);

        AionTransaction transactionForVersion2 = makeHelloWorldDeployTransaction(AvmVersion.VERSION_2, BigInteger.ONE);

        parentBlock = this.blockchain.getBestBlock();
        block = this.blockchain.createBlock(parentBlock, Collections.singletonList(transactionForVersion2), false, parentBlock.getTimestamp());
        connectResult = this.blockchain.tryToConnectAndFetchSummary(block);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        Assert.assertEquals(1, connectResult.getRight().getReceipts().size());
        Assert.assertTrue(connectResult.getRight().getReceipts().get(0).isSuccessful());
    }

    /**
     * We test deploying & calling the same contract in version 1 and 2. We expect the same result
     * each time.
     */
    @Test
    public void testCallInBothAvmVersions() {
        Assert.assertEquals(0, this.blockchain.getBestBlock().getNumber());

        // Ensure we are at a block height where avm version 1 is enabled, then deploy.
        buildBlockchainToHeight(BLOCK_VERSION1_ENABLED);

        AionAddress contractForVersion1 = deployHelloWorldContract(AvmVersion.VERSION_1, BigInteger.ZERO);

        AionTransaction transactionForVersion1 = makeHelloWorldCallTransaction(AvmVersion.VERSION_1, BigInteger.ONE, contractForVersion1);

        Block parentBlock = this.blockchain.getBestBlock();
        AionBlock block = this.blockchain.createBlock(parentBlock, Collections.singletonList(transactionForVersion1), false, parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult = this.blockchain.tryToConnectAndFetchSummary(block);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        Assert.assertEquals(1, connectResult.getRight().getReceipts().size());
        Assert.assertTrue(connectResult.getRight().getReceipts().get(0).isSuccessful());

        // Now, climb to a block height where avm version 2 is enabled and deploy.
        buildBlockchainToHeight(BLOCK_VERSION2_ENABLED);

        AionAddress contractForVersion2 = deployHelloWorldContract(AvmVersion.VERSION_2, BigInteger.TWO);

        AionTransaction transactionForVersion2 = makeHelloWorldCallTransaction(AvmVersion.VERSION_2, BigInteger.valueOf(3), contractForVersion2);

        parentBlock = this.blockchain.getBestBlock();
        block = this.blockchain.createBlock(parentBlock, Collections.singletonList(transactionForVersion2), false, parentBlock.getTimestamp());
        connectResult = this.blockchain.tryToConnectAndFetchSummary(block);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        Assert.assertEquals(1, connectResult.getRight().getReceipts().size());
        Assert.assertTrue(connectResult.getRight().getReceipts().get(0).isSuccessful());
    }

    /**
     * The transaction hash contract can only be deployed in version 2 because the functionality
     * it makes use of does not exist in avm version 1.
     */
    @Test
    public void testDeployVersion2ContractWithOnlyVersion1Support() {
        Assert.assertEquals(0, this.blockchain.getBestBlock().getNumber());

        // Ensure we are at a block height where avm version 1 is enabled, then deploy.
        buildBlockchainToHeight(BLOCK_VERSION1_ENABLED);

        AionTransaction transaction = makeTransactionHashContract(BigInteger.ZERO);

        Block parentBlock = this.blockchain.getBestBlock();
        AionBlock block = this.blockchain.createBlock(parentBlock, Collections.singletonList(transaction), false, parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult = this.blockchain.tryToConnectAndFetchSummary(block);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        Assert.assertEquals(1, connectResult.getRight().getReceipts().size());
        Assert.assertTrue(connectResult.getRight().getReceipts().get(0).isSuccessful());

        AionAddress contract = new AionAddress(connectResult.getRight().getReceipts().get(0).getTransactionOutput());

        transaction = makeTransactionHashCallTransaction(BigInteger.ONE, contract);

        parentBlock = this.blockchain.getBestBlock();
        block = this.blockchain.createBlock(parentBlock, Collections.singletonList(transaction), false, parentBlock.getTimestamp());
        connectResult = this.blockchain.tryToConnectAndFetchSummary(block);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        Assert.assertEquals(1, connectResult.getRight().getReceipts().size());

        // We expect a failure here, because an exception will get thrown inside the contract!
        Assert.assertFalse(connectResult.getRight().getReceipts().get(0).isSuccessful());

        // Now we wait until avm version 2 is enabled before calling it again.
        buildBlockchainToHeight(BLOCK_VERSION2_ENABLED);

        transaction = makeTransactionHashCallTransaction(BigInteger.TWO, contract);

        parentBlock = this.blockchain.getBestBlock();
        block = this.blockchain.createBlock(parentBlock, Collections.singletonList(transaction), false, parentBlock.getTimestamp());
        connectResult = this.blockchain.tryToConnectAndFetchSummary(block);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        Assert.assertEquals(1, connectResult.getRight().getReceipts().size());
        Assert.assertTrue(connectResult.getRight().getReceipts().get(0).isSuccessful());

        // Verify that the contract does indeed return the transaction hash.
        Assert.assertArrayEquals(transaction.getTransactionHash(), connectResult.getRight().getReceipts().get(0).getTransactionOutput());
    }

    private void buildBlockchainToHeight(long height) {
        Block parentBlock = this.blockchain.getBestBlock();

        while (parentBlock.getNumber() < height) {
            AionBlock block = this.blockchain.createBlock(parentBlock, new ArrayList<>(), false, parentBlock.getTimestamp());
            Pair<ImportResult, AionBlockSummary> connectResult = this.blockchain.tryToConnectAndFetchSummary(block);
            Assert.assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());

            parentBlock = this.blockchain.getBestBlock();
        }
    }

    private AionAddress deployHelloWorldContract(AvmVersion version, BigInteger nonce) {
        AionTransaction transaction = makeHelloWorldDeployTransaction(version, nonce);

        Block parentBlock = this.blockchain.getBestBlock();
        AionBlock block = this.blockchain.createBlock(parentBlock, Collections.singletonList(transaction), false, parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult = this.blockchain.tryToConnectAndFetchSummary(block);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        Assert.assertEquals(1, connectResult.getRight().getReceipts().size());
        Assert.assertTrue(connectResult.getRight().getReceipts().get(0).isSuccessful());

        byte[] o = connectResult.getRight().getReceipts().get(0).getTransactionOutput();
        return new AionAddress(connectResult.getRight().getReceipts().get(0).getTransactionOutput());
    }

    private AionTransaction makeHelloWorldCallTransaction(AvmVersion version, BigInteger nonce, AionAddress contract) {
        IAvmResourceFactory factory = (version == AvmVersion.VERSION_1) ? resourceProvider.factoryForVersion1 : resourceProvider.factoryForVersion2;
        byte[] callData = factory.newStreamingEncoder().encodeOneString("sayHello").getEncoding();
        return AionTransaction.create(this.deployerKey, nonce.toByteArray(), contract, BigInteger.ZERO.toByteArray(), callData, 2_000_000,
            energyPrice, TransactionTypes.DEFAULT, null);
    }

    private AionTransaction makeTransactionHashCallTransaction(BigInteger nonce, AionAddress contract) {
        IAvmResourceFactory factory = resourceProvider.factoryForVersion2;
        return AionTransaction.create(this.deployerKey, nonce.toByteArray(), contract, BigInteger.ZERO.toByteArray(), new byte[0], 2_000_000,
            energyPrice, TransactionTypes.DEFAULT, null);
    }

    private AionTransaction makeHelloWorldDeployTransaction(AvmVersion version, BigInteger nonce) {
        byte[] jar = getHelloWorldJarBytes(version);
        return AionTransaction.create(this.deployerKey, nonce.toByteArray(), null, BigInteger.ZERO.toByteArray(), jar, 5_000_000,
            energyPrice, TransactionTypes.AVM_CREATE_CODE, null);
    }

    private AionTransaction makeTransactionHashContract(BigInteger nonce) {
        byte[] jar = getTransactionHashJarBytes();
        return AionTransaction.create(this.deployerKey, nonce.toByteArray(), null, BigInteger.ZERO.toByteArray(), jar, 5_000_000,
            energyPrice, TransactionTypes.AVM_CREATE_CODE, null);
    }

    private static byte[] getHelloWorldJarBytes(AvmVersion version) {
        IAvmResourceFactory factory = (version == AvmVersion.VERSION_1) ? resourceProvider.factoryForVersion1 : resourceProvider.factoryForVersion2;
        return factory.newContractFactory().getDeploymentBytes(AvmContract.HELLO_WORLD);
    }

    private static byte[] getTransactionHashJarBytes() {
        IAvmResourceFactory factory = resourceProvider.factoryForVersion2;
        return factory.newContractFactory().getDeploymentBytes(AvmContract.TRANSACTION_HASH);
    }
}
