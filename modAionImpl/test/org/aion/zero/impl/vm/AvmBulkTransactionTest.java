package org.aion.zero.impl.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.avm.api.ABIDecoder;
import org.aion.avm.api.ABIEncoder;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.base.type.AionAddress;
import org.aion.base.vm.VirtualMachineSpecs;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.core.ImportResult;
import org.aion.vm.VirtualMachineProvider;
import org.aion.vm.api.interfaces.Address;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.vm.contracts.Statefulness;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class AvmBulkTransactionTest {
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;
    private long energyPrice = 1;

    @BeforeClass
    public static void setupAvm() {
        VirtualMachineProvider.initializeAllVirtualMachines();
    }

    @AfterClass
    public static void tearDownAvm() {
        VirtualMachineProvider.shutdownAllVirtualMachines();
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

    @After
    public void tearDown() {
        this.blockchain = null;
        this.deployerKey = null;
    }

    @Test
    public void sendValueTransferTransactionsInBulkTest() {
        int numTransactions = 50;

        // Create the accounts.
        List<ECKey> accounts = getRandomAccounts(numTransactions);

        // Grab the initial data we need to track.
        BigInteger expectedDeployerNonce = getNonce(this.deployerKey);
        BigInteger initialBalanceDeployer = getBalance(this.deployerKey);

        // Declare the various transfer amounts.
        List<BigInteger> transferAmounts = getRandomValues(numTransactions, 500, 5_000_000);
        printValuesUsed(transferAmounts);

        // Make the transactions, then bundle them up together.
        List<AionTransaction> transactions = new ArrayList<>();
        for (int i = 0; i < numTransactions; i++) {
            transactions.add(makeValueTransferTransaction(this.deployerKey, accounts.get(i), transferAmounts.get(i), expectedDeployerNonce));
            expectedDeployerNonce = expectedDeployerNonce.add(BigInteger.ONE);
        }

        // Process the transactions in bulk.
        AionBlockSummary blockSummary = sendTransactionsInBulkInSingleBlock(transactions);

        // Verify all transactions were successful.
        assertEquals(numTransactions, blockSummary.getSummaries().size());
        for (AionTxExecSummary transactionSummary : blockSummary.getSummaries()) {
            assertTrue(transactionSummary.getReceipt().isSuccessful());
        }

        BigInteger expectedDeployerBalance = initialBalanceDeployer;
        for (int i = 0; i < numTransactions; i++) {
            BigInteger energyUsed = BigInteger.valueOf(blockSummary.getSummaries().get(i).getReceipt().getEnergyUsed());
            expectedDeployerBalance = expectedDeployerBalance.subtract(energyUsed).subtract(transferAmounts.get(i));
        }

        // Verify account states after the transactions have been processed.
        for (int i = 0; i < numTransactions; i++) {
            assertEquals(transferAmounts.get(i), getBalance(accounts.get(i)));
            assertEquals(BigInteger.ZERO, getNonce(accounts.get(i)));
        }
        assertEquals(expectedDeployerBalance, getBalance(this.deployerKey));
        assertEquals(expectedDeployerNonce, getNonce(this.deployerKey));
    }

    @Test
    public void sendContractCreationAndCallTransactionsInBulkTest() {
        BigInteger expectedDeployerNonce = getNonce(this.deployerKey);

        // First, deploy a contract that we can call into.
        AionBlockSummary initialSummary = sendTransactionsInBulkInSingleBlock(Collections.singletonList(makeAvmContractCreateTransaction(this.deployerKey, expectedDeployerNonce)));
        expectedDeployerNonce = expectedDeployerNonce.add(BigInteger.ONE);

        // Grab the address of the newly deployed contract.
        Address deployedContract = AionAddress.wrap(initialSummary.getReceipts().get(0).getTransactionOutput());

        int numAvmCreateTransactions = 10;
        int numAvmCallTransactions = 10;
        int numTransactions = numAvmCreateTransactions + numAvmCallTransactions;

        // Grab the initial data we need to track.
        BigInteger initialBalanceDeployer = getBalance(this.deployerKey);

        // Make the create transactions.
        List<AionTransaction> transactions = new ArrayList<>();
        for (int i = 0; i < numAvmCreateTransactions; i++) {
            transactions.add(makeAvmContractCreateTransaction(this.deployerKey, expectedDeployerNonce));
            expectedDeployerNonce = expectedDeployerNonce.add(BigInteger.ONE);
        }

        // Make the call transactions.
        for (int i = 0; i < numAvmCallTransactions; i++) {
            transactions.add(makeAvmContractCallTransaction(this.deployerKey, expectedDeployerNonce, deployedContract));
            expectedDeployerNonce = expectedDeployerNonce.add(BigInteger.ONE);
        }

        // Process the transactions in bulk.
        AionBlockSummary blockSummary = sendTransactionsInBulkInSingleBlock(transactions);

        // Verify all transactions were successful.
        assertEquals(numTransactions, blockSummary.getSummaries().size());
        for (AionTxExecSummary transactionSummary : blockSummary.getSummaries()) {
            assertTrue(transactionSummary.getReceipt().isSuccessful());
        }

        List<Address> contracts = new ArrayList<>();
        BigInteger expectedDeployerBalance = initialBalanceDeployer;
        for (int i = 0; i < numTransactions; i++) {
            BigInteger energyUsed = BigInteger.valueOf(blockSummary.getSummaries().get(i).getReceipt().getEnergyUsed());
            expectedDeployerBalance = expectedDeployerBalance.subtract(energyUsed);

            // The first batch are creates, so grab the new contract addresses.
            if (i < numAvmCreateTransactions) {
                contracts.add(AionAddress.wrap(blockSummary.getSummaries().get(i).getReceipt().getTransactionOutput()));
            }
        }

        // Verify account states after the transactions have been processed.
        for (int i = 0; i < numAvmCreateTransactions; i++) {
            // Check that these contracts have code.
            assertTrue(this.blockchain.getRepository().getCode(contracts.get(i)).length > 0);
            assertEquals(BigInteger.ZERO, getBalance(contracts.get(i)));
            assertEquals(BigInteger.ZERO, getNonce(contracts.get(i)));
        }
        assertEquals(expectedDeployerBalance, getBalance(this.deployerKey));
        assertEquals(expectedDeployerNonce, getNonce(this.deployerKey));

        // Call into the contract to get its current 'count' to verify its state is correct.
        int count = getDeployedStatefulnessCountValue(this.deployerKey, expectedDeployerNonce, deployedContract);
        assertEquals(numAvmCallTransactions, count);
    }

    // Deploys the Statefulness.java contract
    private AionTransaction makeAvmContractCreateTransaction(ECKey sender, BigInteger nonce) {
        byte[] jar = getJarBytes();
        AionTransaction transaction = newTransaction(
            nonce,
            AionAddress.wrap(sender.getAddress()),
            null,
            BigInteger.ZERO,
            jar,
            5_000_000,
            this.energyPrice,
            VirtualMachineSpecs.AVM_CREATE_CODE);
        transaction.sign(this.deployerKey);
        return transaction;
    }

    private AionTransaction makeAvmContractCallTransaction(ECKey sender, BigInteger nonce, Address contract) {
        AionTransaction transaction = newTransaction(
            nonce,
            AionAddress.wrap(sender.getAddress()),
            contract,
            BigInteger.ZERO,
            abiEncodeMethodCall("incrementCounter"),
            2_000_000,
            this.energyPrice,
            VirtualMachineSpecs.AVM_CREATE_CODE);
        transaction.sign(this.deployerKey);
        return transaction;
    }

    private AionTransaction makeValueTransferTransaction(ECKey sender, ECKey beneficiary, BigInteger value, BigInteger nonce) {
        Address senderAddress = AionAddress.wrap(sender.getAddress());

        AionTransaction transaction = newTransaction(
            nonce,
            senderAddress,
            AionAddress.wrap(beneficiary.getAddress()),
            value,
            new byte[0],
            2_000_000,
            this.energyPrice,
            (byte) 0x1);
        transaction.sign(sender);
        return transaction;
    }

    private int getDeployedStatefulnessCountValue(ECKey sender, BigInteger nonce, Address contract) {
        Address senderAddress = AionAddress.wrap(sender.getAddress());

        AionTransaction transaction = newTransaction(
            nonce,
            senderAddress,
            contract,
            BigInteger.ZERO,
            abiEncodeMethodCall("getCount"),
            2_000_000,
            this.energyPrice,
            VirtualMachineSpecs.AVM_CREATE_CODE);
        transaction.sign(sender);

        AionBlockSummary summary = sendTransactionsInBulkInSingleBlock(Collections.singletonList(transaction));
        return (int) ABIDecoder.decodeOneObject(summary.getReceipts().get(0).getTransactionOutput());
    }

    private AionBlockSummary sendTransactionsInBulkInSingleBlock(List<AionTransaction> transactions) {
        AionBlock block = this.blockchain.createNewBlock(this.blockchain.getBestBlock(), transactions, false);
        Pair<ImportResult, AionBlockSummary> connectResult = this.blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        return connectResult.getRight();
    }

    private AionTransaction newTransaction(BigInteger nonce, Address sender, Address destination, BigInteger value, byte[] data, long energyLimit, long energyPrice, byte vm) {
        return new AionTransaction(nonce.toByteArray(), sender, destination, value.toByteArray(), data, energyLimit, energyPrice, vm);
    }

    private BigInteger getNonce(Address address) {
        return this.blockchain.getRepository().getNonce(address);
    }

    private BigInteger getNonce(ECKey address) {
        return getNonce(AionAddress.wrap(address.getAddress()));
    }

    private BigInteger getBalance(Address address) {
        return this.blockchain.getRepository().getBalance(address);
    }

    private BigInteger getBalance(ECKey address) {
        return getBalance(AionAddress.wrap(address.getAddress()));
    }

    private ECKey getRandomAccount() {
        return ECKeyFac.inst().create();
    }

    private List<ECKey> getRandomAccounts(int num) {
        List<ECKey> accounts = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            accounts.add(getRandomAccount());
        }
        return accounts;
    }

    private byte[] getJarBytes() {
        return new CodeAndArguments(JarBuilder.buildJarForMainAndClasses(Statefulness.class), new byte[0]).encodeToBytes();
    }

    private byte[] abiEncodeMethodCall(String method, Object... arguments) {
        return ABIEncoder.encodeMethodArguments(method, arguments);
    }

    private List<BigInteger> getRandomValues(int num, int lowerBound, int upperBound) {
        List<BigInteger> values = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            values.add(BigInteger.valueOf(RandomUtils.nextInt(lowerBound, upperBound)));
        }
        return values;
    }

    /**
     * Since we are using random values here ... we want to be able to reproduce these amounts if
     * a test fails, so we print them off to console every time.
     */
    private void printValuesUsed(List<BigInteger> values) {
        System.out.println("sendValueTransferTransactionsInBulk test is using the following values:");
        for (BigInteger value : values) {
            System.out.print(" " + value + " ");
        }
    }

}
