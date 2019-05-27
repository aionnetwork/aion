package org.aion.zero.impl.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.tx.TransactionTypes;
import org.aion.mcf.valid.TransactionTypeRule;
import org.aion.types.Address;
import org.aion.vm.VirtualMachineProvider;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.contracts.ContractUtils;
import org.aion.zero.impl.vm.contracts.Statefulness;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class AlternatingVmBlockTest {
    private static StandaloneBlockchain blockchain;
    private static ECKey deployerKey;

    @BeforeClass
    public static void setupAvm() {
        if (VirtualMachineProvider.isMachinesAreLive()) {
            return;
        }
        VirtualMachineProvider.initializeAllVirtualMachines();

        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
            .withDefaultAccounts()
            .withValidatorConfiguration("simple")
            .withAvmEnabled()
            .build();
        blockchain = bundle.bc;
        deployerKey = bundle.privateKeys.get(0);
        TransactionTypeRule.allowAVMContractTransaction();
    }

    @AfterClass
    public static void tearDownAvm() {
        if (VirtualMachineProvider.isMachinesAreLive()) {
            VirtualMachineProvider.shutdownAllVirtualMachines();
        }
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

        List<AionTransaction> alternatingTransactions = makeAlternatingAvmFvmContractCreateTransactions(4, nonce.add(BigInteger.TWO));

        AionBlock parentBlock = blockchain.getBestBlock();
        AionBlock block = blockchain.createBlock(parentBlock, alternatingTransactions, false, parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);

        long expectedEnergyUsed = (avmContractDeployEnergyUsed * 2) + (fvmContractDeployEnergyUsed * 2);

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

    /**
     * Tests hitting the block energy limit and surpassing it with 5 alternating transactions.
     */
    @Test
    public void testOverflowBlockWithAlternatingVms() throws Exception {
        List<AionTransaction> alternatingTransactions = makeAlternatingAvmFvmContractCreateTransactions(5, BigInteger.ZERO);

        AionBlock parentBlock = blockchain.getBestBlock();
        AionBlock block = blockchain.createBlock(parentBlock, alternatingTransactions, false, parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);

        assertEquals(ImportResult.INVALID_BLOCK, connectResult.getLeft());
    }

    /**
     * Tests a special case of the alternating transactions: the first 4 don't overflow the limit,
     * the 5th does overflow it, but the 6th can fit into it.
     *
     * The problem: the nonce no longer makes any sense because the 5th transaction is kicked out.
     */
    @Test
    public void testOverflowBlockWithAlternatingVms2() throws Exception {
        List<AionTransaction> alternatingTransactions = makeAlternatingAvmFvmContractCreateTransactions(6, BigInteger.ZERO);

        AionBlock parentBlock = blockchain.getBestBlock();
        AionBlock block = blockchain.createBlock(parentBlock, alternatingTransactions, false, parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);

        assertEquals(ImportResult.INVALID_BLOCK, connectResult.getLeft());
    }

    private List<AionTransaction> makeAlternatingAvmFvmContractCreateTransactions(int totalNum, BigInteger initialNonce) throws IOException {
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
        AionTransaction transaction = newTransaction(
            nonce,
            Address.wrap(sender.getAddress()),
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
        return new CodeAndArguments(JarBuilder.buildJarForMainAndClassesAndUserlib(Statefulness.class), new byte[0]).encodeToBytes();
    }

    private AionTransaction makeFvmContractCreateTransaction(ECKey sender, BigInteger nonce) throws IOException {
        byte[] contractBytes = ContractUtils.getContractDeployer("Ticker.sol", "Ticker");
        AionTransaction transaction =
            newTransaction(
                nonce,
                Address.wrap(sender.getAddress()),
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
        AionBlock block = blockchain.createBlock(parentBlock, Collections.singletonList(avmDeploy), false, parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        return connectResult.getRight().getReceipts().get(0).getEnergyUsed();
    }

    private long getFvmContractDeploymentCost(BigInteger nonce) throws IOException {
        AionTransaction fvmDeploy = makeFvmContractCreateTransaction(deployerKey, nonce);
        AionBlock parentBlock = blockchain.getBestBlock();
        AionBlock block = blockchain.createBlock(parentBlock, Collections.singletonList(fvmDeploy), false, parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        return connectResult.getRight().getReceipts().get(0).getEnergyUsed();
    }

    private AionTransaction newTransaction(BigInteger nonce, Address sender, Address destination, BigInteger value, byte[] data, long energyLimit, long energyPrice, byte vm) {
        return new AionTransaction(nonce.toByteArray(), sender, destination, value.toByteArray(), data, energyLimit, energyPrice, vm);
    }
}
