package org.aion.zero.impl.blockchain;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.avm.stub.IContractFactory;
import org.aion.avm.stub.IContractFactory.AvmContract;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.base.InvokableTxUtil;
import org.aion.base.TransactionTypeRule;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.mcf.blockchain.Block;
import org.aion.types.AionAddress;
import org.aion.util.types.AddressUtils;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.types.MiningBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.AvmPathManager;
import org.aion.zero.impl.vm.AvmTestConfig;
import org.aion.zero.impl.vm.TestResourceProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;

public class MetaTransactionTest {

    private static IContractFactory contractFactory;
    private static TestResourceProvider resourceProvider;
    private StandaloneBlockchain blockchain;
    private ECKey proxyDeployer;
    private ECKey deployer;
    private ECKey freeloader;

    @BeforeClass
    public static void setupAvm() throws Exception {
        TransactionTypeRule.allowAVMContractTransaction();
        resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());
        AvmTestConfig.supportBothAvmVersions(0,1,0);
        contractFactory = resourceProvider.factoryForVersion2.newContractFactory();
    }

    @AfterClass
    public static void tearDownAvm() throws Exception {
        TransactionTypeRule.disallowAVMContractTransaction();
        AvmTestConfig.clearConfigurations();
        resourceProvider.close();
    }

    @Before
    public void setup() {
        // generate bc bundle with pruning enabled
        StandaloneBlockchain.Bundle bundle =
            new StandaloneBlockchain.Builder()
                .withDefaultAccounts()
                .withValidatorConfiguration("simple")
                .withAvmEnabled()
                .build();

        blockchain = bundle.bc;
        deployer = bundle.privateKeys.get(0);
        freeloader = bundle.privateKeys.get(1);
        proxyDeployer = bundle.privateKeys.get(2);
        buildBlockchainToHeight(2);
    }

    @Test
    public void testInlineBalanceTransfer() {

        AionAddress contractAddress = deployProxyContract();

        BigInteger freeloaderBalance = blockchain.getRepository().getBalance(new AionAddress(freeloader.getAddress()));

        byte[] innerTx =
            InvokableTxUtil.encodeInvokableTransaction(
                freeloader,
                BigInteger.ZERO,
                AddressUtils.ZERO_ADDRESS,
                freeloaderBalance,
                new byte[0],
                contractAddress);

        byte[] callData = encodeCallByteArray("callInline", innerTx);

        AionTransaction metaTx =
            AionTransaction.create(
                deployer,
                BigInteger.ZERO.toByteArray(),
                contractAddress,
                BigInteger.ZERO.toByteArray(),
                callData,
                1888888L,
                10_000_000_000L,
                TransactionTypes.DEFAULT,
                null);

        MiningBlock block2 =
            blockchain.createBlock(
                blockchain.getBestBlock(), Collections.singletonList(metaTx), false, blockchain.getBestBlock().getTimestamp());

        Pair<ImportResult, AionBlockSummary> connectResult2 = blockchain.tryToConnectAndFetchSummary(block2);
        AionTxReceipt receipt2 = connectResult2.getRight().getReceipts().get(0);

        assertEquals(ImportResult.IMPORTED_BEST, connectResult2.getLeft());
        assertTrue(receipt2.isSuccessful());
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getBalance(new AionAddress(freeloader.getAddress())));
    }

    @Test
    public void testInlineContractCreateAndCall() {

        AionAddress contractAddress = deployProxyContract();

        BigInteger freeloaderBalance = blockchain.getRepository().getBalance(new AionAddress(freeloader.getAddress()));

        byte[] innerDeploy =
            InvokableTxUtil.encodeInvokableTransaction(
                freeloader,
                BigInteger.ZERO,
                null,
                BigInteger.ZERO,
                contractFactory.getDeploymentBytes(AvmContract.META_TRANSACTION_PROXY),
                contractAddress);

        byte[] callData = encodeCallByteArray("createInline", innerDeploy);

        AionTransaction metaTx =
            AionTransaction.create(
                deployer,
                BigInteger.ZERO.toByteArray(),
                contractAddress,
                BigInteger.ZERO.toByteArray(),
                callData,
                1888888L,
                10_000_000_000L,
                TransactionTypes.DEFAULT,
                null);

        MiningBlock block2 =
            blockchain.createBlock(
                blockchain.getBestBlock(), Collections.singletonList(metaTx), false, blockchain.getBestBlock().getTimestamp());

        Pair<ImportResult, AionBlockSummary> connectResult2 = blockchain.tryToConnectAndFetchSummary(block2);
        AionTxReceipt receipt2 = connectResult2.getRight().getReceipts().get(0);

        assertEquals(ImportResult.IMPORTED_BEST, connectResult2.getLeft());
        assertTrue(receipt2.isSuccessful());
        AionAddress innerContractAddress = TxUtil.calculateContractAddress(freeloader.getAddress(), BigInteger.ZERO);

        byte[] innerCall =
            InvokableTxUtil.encodeInvokableTransaction(
                freeloader,
                BigInteger.ONE,
                AddressUtils.ZERO_ADDRESS,
                freeloaderBalance,
                contractFactory.getDeploymentBytes(AvmContract.META_TRANSACTION_PROXY),
                AddressUtils.ZERO_ADDRESS);

        byte[] callData2 = encodeCallByteArray("callInline", innerCall);

        AionTransaction metaTx2 =
            AionTransaction.create(
                deployer,
                BigInteger.ONE.toByteArray(),
                innerContractAddress,
                BigInteger.ZERO.toByteArray(),
                callData2,
                1888888L,
                10_000_000_000L,
                TransactionTypes.DEFAULT,
                null);

        MiningBlock block3 =
            blockchain.createBlock(
                blockchain.getBestBlock(), Collections.singletonList(metaTx2), false, blockchain.getBestBlock().getTimestamp());

        Pair<ImportResult, AionBlockSummary> connectResult3 = blockchain.tryToConnectAndFetchSummary(block3);
        // This line fails if java.util.List is not imported for some reason
        AionBlockSummary blockSummary = connectResult3.getRight();
        List<AionTxReceipt> receipts = blockSummary.getReceipts();
        AionTxReceipt receipt3 = receipts.get(0);

        assertEquals(ImportResult.IMPORTED_BEST, connectResult3.getLeft());
        assertTrue(receipt3.isSuccessful());
    }

    private AionAddress deployProxyContract() {

        AionTransaction contractDeploymentTx =
            AionTransaction.create(
                proxyDeployer,
                BigInteger.ZERO.toByteArray(),
                null,
                BigInteger.ZERO.toByteArray(),
                contractFactory.getDeploymentBytes(AvmContract.META_TRANSACTION_PROXY),
                5_000_000L,
                10_000_000_000L,
                TransactionTypes.AVM_CREATE_CODE,
                null);

        MiningBlock block =
            blockchain.createBlock(
                blockchain.getBestBlock(), Collections.singletonList(contractDeploymentTx), false, blockchain.getBestBlock().getTimestamp());

        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);

        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        assertTrue(receipt.isSuccessful());

        // ensure the contract information was saved
        AionAddress contractAddress = TxUtil.calculateContractAddress(contractDeploymentTx);
        assertNotNull(contractAddress);
        assertArrayEquals(contractAddress.toByteArray(), receipt.getTransactionOutput());
        return contractAddress;
    }

    private static byte[] encodeCallByteArray(String methodName, byte[] arg) {
        return resourceProvider.factoryForVersion2.newStreamingEncoder()
            .encodeOneString(methodName)
            .encodeOneByteArray(arg)
            .getEncoding();
    }

    private void buildBlockchainToHeight(long height) {
        Block parentBlock = blockchain.getBestBlock();

        while (parentBlock.getNumber() < height) {
            MiningBlock block = blockchain.createBlock(parentBlock, new ArrayList<>(), false, parentBlock.getTimestamp());
            Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);
            Assert.assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());

            parentBlock = blockchain.getBestBlock();
        }
    }
}
