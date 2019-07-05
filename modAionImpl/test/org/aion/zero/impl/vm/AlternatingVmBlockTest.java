package org.aion.zero.impl.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.base.TransactionTypes;
import org.aion.mcf.valid.TransactionTypeRule;
import org.aion.types.AionAddress;
import org.aion.vm.LongLivedAvm;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.contracts.ContractUtils;
import org.aion.zero.impl.vm.contracts.Statefulness;
import org.aion.base.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class AlternatingVmBlockTest {
    private static StandaloneBlockchain blockchain;
    private static ECKey deployerKey;

    @BeforeClass
    public static void setupAvm() {
        LongLivedAvm.createAndStartLongLivedAvm();
        TransactionTypeRule.allowAVMContractTransaction();
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
    public static void tearDownAvm() {
        LongLivedAvm.destroy();
        TransactionTypeRule.disallowAVMContractTransaction();
    }

    /**
     * Tests constructing a block with the max number of alternating transactions without going over
     * the block energy limit. This should be successful.
     */
    @Test
    public void testFullBlockWithAlternatingVms() throws IOException {
        BigInteger nonce = BigInteger.ZERO;
        long avmContractDeployEnergyUsed = getAvmContractDeploymentCost(nonce);
        long fvmContractDeployEnergyUsed = getFvmContractDeploymentCost(nonce.add(BigInteger.ONE));

        List<AionTransaction> alternatingTransactions =
                makeAlternatingAvmFvmContractCreateTransactions(4, nonce.add(BigInteger.TWO));

        AionBlock parentBlock = blockchain.getBestBlock();
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

    /** Tests hitting the block energy limit and surpassing it with 5 alternating transactions. */
    @Test
    public void testOverflowBlockWithAlternatingVms() throws Exception {
        List<AionTransaction> alternatingTransactions =
                makeAlternatingAvmFvmContractCreateTransactions(5, BigInteger.ZERO);

        AionBlock parentBlock = blockchain.getBestBlock();
        AionBlock block =
                blockchain.createBlock(
                        parentBlock, alternatingTransactions, false, parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult =
                blockchain.tryToConnectAndFetchSummary(block);

        // A correct block is produced but it does not contain all of the transactions. The last
        // transaction is rejected because
        // it would cause the block energy limit to be exceeded.
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        assertEquals(4, connectResult.getRight().getReceipts().size());
    }

    /**
     * Tests a special case of the alternating transactions: the first 4 don't overflow the limit,
     * the 5th does overflow it, but the 6th can fit into it.
     *
     * <p>The problem: the nonce no longer makes any sense because the 5th transaction is kicked
     * out.
     */
    @Test
    public void testOverflowBlockWithAlternatingVms2() throws Exception {
        List<AionTransaction> alternatingTransactions =
                makeAlternatingAvmFvmContractCreateTransactions(6, BigInteger.ZERO);

        AionBlock parentBlock = blockchain.getBestBlock();
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
        assertEquals(4, connectResult.getRight().getReceipts().size());
    }

    private List<AionTransaction> makeAlternatingAvmFvmContractCreateTransactions(
            int totalNum, BigInteger initialNonce) throws IOException {
        List<AionTransaction> transactions = new ArrayList<>();

        BigInteger currentNonce = initialNonce;
        for (int i = 0; i < totalNum; i++) {

            if (i % 2 == 0) {
                transactions.add(makeAvmContractCreateTransaction(deployerKey, currentNonce));
            } else {
                transactions.add(makeFvmContractCreateTransaction(deployerKey, currentNonce));
            }

            currentNonce = currentNonce.add(BigInteger.ONE);
        }
        return transactions;
    }

    private AionTransaction makeAvmContractCreateTransaction(ECKey sender, BigInteger nonce) {
        byte[] jar = getJarBytes();
        AionTransaction transaction =
                newTransaction(
                        nonce,
                        new AionAddress(sender.getAddress()),
                        null,
                        BigInteger.ZERO,
                        jar,
                        5_000_000,
                        1,
                        TransactionTypes.AVM_CREATE_CODE);
        transaction.sign(deployerKey);
        return transaction;
    }

    private byte[] getJarBytes() {
        return new CodeAndArguments(
                        JarBuilder.buildJarForMainAndClassesAndUserlib(Statefulness.class),
                        new byte[0])
                .encodeToBytes();
    }

    private AionTransaction makeFvmContractCreateTransaction(ECKey sender, BigInteger nonce)
            throws IOException {
        byte[] contractBytes = ContractUtils.getContractDeployer("Ticker.sol", "Ticker");
        AionTransaction transaction =
                newTransaction(
                        nonce,
                        new AionAddress(sender.getAddress()),
                        null,
                        BigInteger.ZERO,
                        contractBytes,
                        5_000_000,
                        1,
                        TransactionTypes.DEFAULT);
        transaction.sign(deployerKey);
        return transaction;
    }

    private long getAvmContractDeploymentCost(BigInteger nonce) {
        AionTransaction avmDeploy = makeAvmContractCreateTransaction(deployerKey, nonce);
        AionBlock parentBlock = blockchain.getBestBlock();
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
        AionBlock parentBlock = blockchain.getBestBlock();
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

    private AionTransaction newTransaction(
            BigInteger nonce,
            AionAddress sender,
            AionAddress destination,
            BigInteger value,
            byte[] data,
            long energyLimit,
            long energyPrice,
            byte vm) {
        return new AionTransaction(
                nonce.toByteArray(),
                sender,
                destination,
                value.toByteArray(),
                data,
                energyLimit,
                energyPrice,
                vm);
    }
}
