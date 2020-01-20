package org.aion.zero.impl.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.avm.stub.AvmVersion;
import org.aion.avm.stub.IAvmResourceFactory;
import org.aion.avm.stub.IContractFactory.AvmContract;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.crypto.ECKey;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.core.ImportResult;
import org.aion.base.TransactionTypeRule;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.contracts.ContractUtils;
import org.aion.base.AionTxReceipt;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class AlternatingVmBlockTest {
    private static StandaloneBlockchain blockchain;
    private static ECKey deployerKey;
    private static TestResourceProvider resourceProvider;
    private long energyPrice = 10_000_000_000L;

    @BeforeClass
    public static void setupAvm() throws Exception {
        TransactionTypeRule.allowAVMContractTransaction();
        resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());
        AvmTestConfig.supportOnlyAvmVersion1();
    }

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .withAvmEnabled()
                        .build();
        blockchain = bundle.bc;
        deployerKey = bundle.privateKeys.get(0);
    }

    @AfterClass
    public static void tearDownAvm() throws Exception {
        TransactionTypeRule.disallowAVMContractTransaction();
        AvmTestConfig.clearConfigurations();
        resourceProvider.close();
    }

    /**
     * Tests constructing a block with the max number of alternating transactions without going over
     * the block energy limit. This should be successful.
     */
    @Test
    public void testFullBlockWithAlternatingVms() throws IOException {
        BigInteger nonce = BigInteger.ZERO;
        long avmContractDeployEnergyUsed = getAvmContractDeploymentCost(AvmVersion.VERSION_1, nonce);
        long fvmContractDeployEnergyUsed = getFvmContractDeploymentCost(nonce.add(BigInteger.ONE));

        List<AionTransaction> alternatingTransactions =
                makeAlternatingAvmFvmContractCreateTransactions(AvmVersion.VERSION_1, 4, nonce.add(BigInteger.TWO));

        Block parentBlock = blockchain.getBestBlock();
        AionBlock block =
                blockchain.createBlock(
                        parentBlock, alternatingTransactions, false, parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult =
                blockchain.tryToConnectAndFetchSummary(block);

        long expectedEnergyUsed =
                (avmContractDeployEnergyUsed * 2) + (fvmContractDeployEnergyUsed * 2);

        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        assertEquals(expectedEnergyUsed, connectResult.getRight().getBlock().getNrgConsumed());
        assertTrue(connectResult.getRight().getBlock().getNrgLimit() > expectedEnergyUsed);

        boolean isAvmTransaction = true;
        for (AionTxReceipt receipt : connectResult.getRight().getReceipts()) {
            if (isAvmTransaction) {
                assertEquals(avmContractDeployEnergyUsed, receipt.getEnergyUsed());
            } else {
                assertEquals(fvmContractDeployEnergyUsed, receipt.getEnergyUsed());
            }
            assertTrue(receipt.isSuccessful());

            isAvmTransaction = !isAvmTransaction;
        }
    }

    /** Tests hitting the block energy limit and surpassing it with 20 alternating transactions. */
    @Test
    public void testOverflowBlockWithAlternatingVms() throws Exception {
        List<AionTransaction> alternatingTransactions =
                makeAlternatingAvmFvmContractCreateTransactions(AvmVersion.VERSION_1, 20, BigInteger.ZERO);

        Block parentBlock = blockchain.getBestBlock();
        AionBlock block =
                blockchain.createBlock(
                        parentBlock, alternatingTransactions, false, parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult =
                blockchain.tryToConnectAndFetchSummary(block);

        // A correct block is produced but it does not contain all of the transactions. The last
        // transaction is rejected because
        // it would cause the block energy limit to be exceeded.
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        assertEquals(14, connectResult.getRight().getReceipts().size());
    }

    /**
     * Tests a special case of the alternating transactions: the first 14 don't overflow the limit,
     * the 15th does overflow it, but the 16th can fit into it.
     *
     * <p>The problem: the nonce no longer makes any sense because the 15th transaction is kicked
     * out.
     */
    @Test
    public void testOverflowBlockWithAlternatingVms2() throws Exception {
        List<AionTransaction> alternatingTransactions =
                makeAlternatingAvmFvmContractCreateTransactions(AvmVersion.VERSION_1, 16, BigInteger.ZERO);

        Block parentBlock = blockchain.getBestBlock();
        AionBlock block =
                blockchain.createBlock(
                        parentBlock, alternatingTransactions, false, parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult =
                blockchain.tryToConnectAndFetchSummary(block);

        // A correct block is produced but it does not contain all of the transactions. The second
        // last transaction is rejected
        // because it would cause the block energy limit to be exceeded, and the last transaction
        // now has an invalid nonce since
        // the previous transaction was rejected, so neither of these are included in the block.
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        assertEquals(14, connectResult.getRight().getReceipts().size());
    }

    private List<AionTransaction> makeAlternatingAvmFvmContractCreateTransactions(
            AvmVersion version, int totalNum, BigInteger initialNonce) throws IOException {
        List<AionTransaction> transactions = new ArrayList<>();

        BigInteger currentNonce = initialNonce;
        for (int i = 0; i < totalNum; i++) {

            if (i % 2 == 0) {
                transactions.add(makeAvmContractCreateTransaction(version, deployerKey, currentNonce));
            } else {
                transactions.add(makeFvmContractCreateTransaction(deployerKey, currentNonce));
            }

            currentNonce = currentNonce.add(BigInteger.ONE);
        }
        return transactions;
    }

    private AionTransaction makeAvmContractCreateTransaction(AvmVersion version, ECKey sender, BigInteger nonce) {
        byte[] jar = getJarBytes(version);
        return AionTransaction.create(
                sender,
                nonce.toByteArray(),
                null,
                new byte[0],
                jar,
                5_000_000,
                energyPrice,
                TransactionTypes.AVM_CREATE_CODE, null);
    }

    private byte[] getJarBytes(AvmVersion version) {
        IAvmResourceFactory factory = (version == AvmVersion.VERSION_1) ? resourceProvider.factoryForVersion1 : resourceProvider.factoryForVersion2;
        return factory.newContractFactory().getDeploymentBytes(AvmContract.STATEFULNESS);
    }

    private AionTransaction makeFvmContractCreateTransaction(ECKey sender, BigInteger nonce)
            throws IOException {
        byte[] contractBytes = ContractUtils.getContractDeployer("Ticker.sol", "Ticker");
        return AionTransaction.create(
                sender,
                nonce.toByteArray(),
                null,
                new byte[0],
                contractBytes,
                5_000_000,
                energyPrice,
                TransactionTypes.DEFAULT, null);
    }

    private long getAvmContractDeploymentCost(AvmVersion version, BigInteger nonce) {
        AionTransaction avmDeploy = makeAvmContractCreateTransaction(version, deployerKey, nonce);
        Block parentBlock = blockchain.getBestBlock();
        AionBlock block =
                blockchain.createBlock(
                        parentBlock,
                        Collections.singletonList(avmDeploy),
                        false,
                        parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult =
                blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        return connectResult.getRight().getReceipts().get(0).getEnergyUsed();
    }

    private long getFvmContractDeploymentCost(BigInteger nonce) throws IOException {
        AionTransaction fvmDeploy = makeFvmContractCreateTransaction(deployerKey, nonce);
        Block parentBlock = blockchain.getBestBlock();
        AionBlock block =
                blockchain.createBlock(
                        parentBlock,
                        Collections.singletonList(fvmDeploy),
                        false,
                        parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult =
                blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        return connectResult.getRight().getReceipts().get(0).getEnergyUsed();
    }
}
