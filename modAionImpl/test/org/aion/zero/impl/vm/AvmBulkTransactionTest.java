package org.aion.zero.impl.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.log.AionLoggerFactory;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.valid.TransactionTypeRule;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.vm.LongLivedAvm;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.contracts.Statefulness;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
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
        // reduce default logging levels
        Map<String, String> cfg = new HashMap<>();
        cfg.put("API", "ERROR");
        cfg.put("CONS", "ERROR");
        cfg.put("DB", "ERROR");
        cfg.put("GEM", "ERROR");
        cfg.put("P2P", "ERROR");
        cfg.put("ROOT", "ERROR");
        cfg.put("SYNC", "ERROR");
        cfg.put("TX", "ERROR");
        cfg.put("VM", "ERROR");
        AionLoggerFactory.init(cfg);

        LongLivedAvm.createAndStartLongLivedAvm();
    }

    @AfterClass
    public static void tearDownAvm() {
        LongLivedAvm.destroy();
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
        TransactionTypeRule.allowAVMContractTransaction();
    }

    @After
    public void tearDown() {
        this.blockchain = null;
        this.deployerKey = null;
        TransactionTypeRule.disallowAVMContractTransaction();
    }

    /** Ensures that contracts with empty jars cannot be deployed on the AVM. */
    @Test
    public void deployEmptyContract() {
        AionTransaction deployEmptyContractTx =
                AionTransaction.create(
                        deployerKey,
                        BigInteger.ZERO.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        new byte[0],
                        5_000_000L,
                        10_123_456_789L,
                        TransactionTypes.AVM_CREATE_CODE);

        Block parentBlock = blockchain.getBestBlock();
        AionBlock block =
                blockchain.createBlock(
                        parentBlock,
                        List.of(deployEmptyContractTx),
                        false,
                        parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult =
                blockchain.tryToConnectAndFetchSummary(block);

        // ensure block was imported
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());

        // ensure the tx failed
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);
        System.out.println(receipt);
        assertEquals(receipt.isSuccessful(), false);
    }

    /** Ensures that AVM contracts can be deployed and called within the same block. */
    @Test
    public void importBlockWithContractDeploysAndSubsequentCalls() {
        BigInteger expectedNonce = getNonce(deployerKey);
        BigInteger initialBalance = getBalance(deployerKey);

        // note: 3 contract deployments would pass the block energy limit
        int nbCreateTransactions = 2;
        int nbCallTransactions = 2;
        int nbTransactions = nbCreateTransactions + nbCreateTransactions * nbCallTransactions;

        List<AionTransaction> transactions = new ArrayList<>();

        for (int j = 0; j < nbCreateTransactions; j++) {
            // create contract transaction
            AionTransaction deployTx = makeAvmContractCreateTransaction(deployerKey, expectedNonce);
            expectedNonce = expectedNonce.add(BigInteger.ONE);

            AionAddress deployedContract = TxUtil.calculateContractAddress(deployTx);
            transactions.add(deployTx);

            // subsequent call transactions
            for (int i = 0; i < nbCallTransactions; i++) {
                transactions.add(
                        makeAvmContractCallTransaction(
                                deployerKey, expectedNonce, deployedContract));
                expectedNonce = expectedNonce.add(BigInteger.ONE);
            }
        }

        // process the transactions in bulk
        AionBlockSummary blockSummary = sendTransactionsInBulkInSingleBlock(transactions);

        // verify all transactions were successful
        assertEquals(nbTransactions, blockSummary.getSummaries().size());
        for (AionTxExecSummary transactionSummary : blockSummary.getSummaries()) {
            System.out.println(transactionSummary.getReceipt());
            assertTrue(transactionSummary.getReceipt().isSuccessful());
        }

        BigInteger expectedBalance = initialBalance;
        for (int i = 0; i < nbTransactions; i++) {
            BigInteger energyUsed =
                    BigInteger.valueOf(
                            blockSummary.getSummaries().get(i).getReceipt().getEnergyUsed());
            expectedBalance = expectedBalance.subtract(energyUsed);
        }

        assertEquals(expectedBalance, getBalance(deployerKey));
        assertEquals(expectedNonce, getNonce(deployerKey));
    }

    /**
     * Ensures that the block described below is processed correctly, where the block contains the
     * following transactions:
     *
     * <ol>
     *   <li>a FVM contract deployment,
     *   <li>an AVM contract deployment,
     *   <li>a FVM call to the first contract,
     *   <li>an AVM call to the second contract.
     * </ol>
     */
    @Test
    public void importBlockWithContractAndCallsForBothVMs() {
        BigInteger expectedNonce = getNonce(deployerKey);
        BigInteger initialBalance = getBalance(deployerKey);

        List<AionTransaction> transactions = new ArrayList<>();

        // deploy on FVM
        String contractCode =
                "0x605060405234156100105760006000fd5b5b600a600060005081909090555060006000505460016000506000600060005054815260100190815260100160002090506000508190909055506064600260005060000160005081909090555060c8600260005060010160005081909090555060026000506001016000505460016000506000600260005060000160005054815260100190815260100160002090506000508190909055505b6100ae565b610184806100bd6000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680631677b0ff14610049578063209652551461007657806362eb702a146100a057610043565b60006000fd5b34156100555760006000fd5b61007460048080359060100190919080359060100190919050506100c4565b005b34156100825760006000fd5b61008a610111565b6040518082815260100191505060405180910390f35b34156100ac5760006000fd5b6100c26004808035906010019091905050610123565b005b8160026000506000016000508190909055508060026000506001016000508190909055508082016001600050600084815260100190815260100160002090506000508190909055505b5050565b60006000600050549050610120565b90565b806000600050819090905550600181016001600050600083815260100190815260100160002090506000508190909055505b505600a165627a7a723058205b6e690d70d3703337452467437dc7c4e863ee4ad34b24cc516e2afa71e334700029";
        AionTransaction deployTxFVM =
                AionTransaction.create(
                        deployerKey,
                        expectedNonce.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        ByteUtil.hexStringToBytes(contractCode),
                        5_000_000L,
                        energyPrice,
                        TransactionTypes.DEFAULT);

        AionAddress fvmContract = TxUtil.calculateContractAddress(deployTxFVM);
        transactions.add(deployTxFVM);
        expectedNonce = expectedNonce.add(BigInteger.ONE);

        // deploy on AVM
        AionTransaction deployTxAVM = makeAvmContractCreateTransaction(deployerKey, expectedNonce);
        AionAddress avmContract = TxUtil.calculateContractAddress(deployTxAVM);
        transactions.add(deployTxAVM);
        expectedNonce = expectedNonce.add(BigInteger.ONE);

        // call to FVM contract
        AionTransaction contractCallTx =
                AionTransaction.create(
                        deployerKey,
                        expectedNonce.toByteArray(),
                        fvmContract,
                        BigInteger.ZERO.toByteArray(),
                        Hex.decode("62eb702a00000000000000000000000000000006"),
                        2_000_000L,
                        energyPrice,
                        TransactionTypes.DEFAULT);
        transactions.add(contractCallTx);
        expectedNonce = expectedNonce.add(BigInteger.ONE);

        // call to AVM contract
        transactions.add(makeAvmContractCallTransaction(deployerKey, expectedNonce, avmContract));
        expectedNonce = expectedNonce.add(BigInteger.ONE);

        // process the transactions in bulk
        AionBlockSummary blockSummary = sendTransactionsInBulkInSingleBlock(transactions);

        // verify all transactions were successful
        assertEquals(transactions.size(), blockSummary.getSummaries().size());
        for (AionTxExecSummary transactionSummary : blockSummary.getSummaries()) {
            assertTrue(transactionSummary.getReceipt().isSuccessful());
        }

        BigInteger expectedBalance = initialBalance;
        for (int i = 0; i < transactions.size(); i++) {
            BigInteger energyUsed =
                    BigInteger.valueOf(
                            blockSummary.getSummaries().get(i).getReceipt().getEnergyUsed());
            expectedBalance = expectedBalance.subtract(energyUsed);
        }

        assertEquals(expectedBalance, getBalance(deployerKey));
        assertEquals(expectedNonce, getNonce(deployerKey));
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
            transactions.add(
                    makeValueTransferTransaction(
                            this.deployerKey,
                            accounts.get(i),
                            transferAmounts.get(i),
                            expectedDeployerNonce));
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
            BigInteger energyUsed =
                    BigInteger.valueOf(
                            blockSummary.getSummaries().get(i).getReceipt().getEnergyUsed());
            expectedDeployerBalance =
                    expectedDeployerBalance.subtract(energyUsed).subtract(transferAmounts.get(i));
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
        AionBlockSummary initialSummary =
                sendTransactionsInBulkInSingleBlock(
                        Collections.singletonList(
                                makeAvmContractCreateTransaction(
                                        this.deployerKey, expectedDeployerNonce)));
        expectedDeployerNonce = expectedDeployerNonce.add(BigInteger.ONE);

        // Grab the address of the newly deployed contract.
        AionAddress deployedContract =
                new AionAddress(initialSummary.getReceipts().get(0).getTransactionOutput());

        int numAvmCreateTransactions = 2;
        int numAvmCallTransactions = 10;
        int numTransactions = numAvmCreateTransactions + numAvmCallTransactions;

        // Grab the initial data we need to track.
        BigInteger initialBalanceDeployer = getBalance(this.deployerKey);

        // Make the create transactions.
        List<AionTransaction> transactions = new ArrayList<>();
        for (int i = 0; i < numAvmCreateTransactions; i++) {
            transactions.add(
                    makeAvmContractCreateTransaction(this.deployerKey, expectedDeployerNonce));
            expectedDeployerNonce = expectedDeployerNonce.add(BigInteger.ONE);
        }

        // Make the call transactions.
        for (int i = 0; i < numAvmCallTransactions; i++) {
            transactions.add(
                    makeAvmContractCallTransaction(
                            this.deployerKey, expectedDeployerNonce, deployedContract));
            expectedDeployerNonce = expectedDeployerNonce.add(BigInteger.ONE);
        }

        // Process the transactions in bulk.
        AionBlockSummary blockSummary = sendTransactionsInBulkInSingleBlock(transactions);

        // Verify all transactions were successful.
        assertEquals(numTransactions, blockSummary.getSummaries().size());
        for (AionTxExecSummary transactionSummary : blockSummary.getSummaries()) {
            assertTrue(transactionSummary.getReceipt().isSuccessful());
        }

        List<AionAddress> contracts = new ArrayList<>();
        BigInteger expectedDeployerBalance = initialBalanceDeployer;
        for (int i = 0; i < numTransactions; i++) {
            BigInteger energyUsed =
                    BigInteger.valueOf(
                            blockSummary.getSummaries().get(i).getReceipt().getEnergyUsed());
            expectedDeployerBalance = expectedDeployerBalance.subtract(energyUsed);

            // The first batch are creates, so grab the new contract addresses.
            if (i < numAvmCreateTransactions) {
                contracts.add(
                        new AionAddress(
                                blockSummary
                                        .getSummaries()
                                        .get(i)
                                        .getReceipt()
                                        .getTransactionOutput()));
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
        int count =
                getDeployedStatefulnessCountValue(
                        this.deployerKey, expectedDeployerNonce, deployedContract);
        assertEquals(numAvmCallTransactions, count);
    }

    // Deploys the Statefulness.java contract
    private AionTransaction makeAvmContractCreateTransaction(ECKey sender, BigInteger nonce) {
        byte[] jar = getJarBytes();
        return AionTransaction.create(
                sender,
                nonce.toByteArray(),
                null,
                new byte[0],
                jar,
                5_000_000,
                this.energyPrice,
                TransactionTypes.AVM_CREATE_CODE);
    }

    private AionTransaction makeAvmContractCallTransaction(
            ECKey sender, BigInteger nonce, AionAddress contract) {
        return AionTransaction.create(
                sender,
                nonce.toByteArray(),
                contract,
                new byte[0],
                abiEncodeMethodCall("incrementCounter"),
                2_000_000,
                this.energyPrice,
                TransactionTypes.DEFAULT);
    }

    private AionTransaction makeValueTransferTransaction(
            ECKey sender, ECKey beneficiary, BigInteger value, BigInteger nonce) {

        return AionTransaction.create(
                sender,
                nonce.toByteArray(),
                new AionAddress(beneficiary.getAddress()),
                value.toByteArray(),
                new byte[0],
                2_000_000,
                this.energyPrice,
                TransactionTypes.DEFAULT);
    }

    private int getDeployedStatefulnessCountValue(
            ECKey sender, BigInteger nonce, AionAddress contract) {

        AionTransaction transaction =
                AionTransaction.create(
                        sender,
                        nonce.toByteArray(),
                        contract,
                        new byte[0],
                        abiEncodeMethodCall("getCount"),
                        2_000_000,
                        this.energyPrice,
                        TransactionTypes.DEFAULT);

        AionBlockSummary summary =
                sendTransactionsInBulkInSingleBlock(Collections.singletonList(transaction));
        return (int) ABIUtil.decodeOneObject(summary.getReceipts().get(0).getTransactionOutput());
    }

    private AionBlockSummary sendTransactionsInBulkInSingleBlock(
            List<AionTransaction> transactions) {
        Block parentBlock = this.blockchain.getBestBlock();
        AionBlock block =
                this.blockchain.createBlock(
                        parentBlock, transactions, false, parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult =
                this.blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        return connectResult.getRight();
    }

    private BigInteger getNonce(AionAddress address) {
        return this.blockchain.getRepository().getNonce(address);
    }

    private BigInteger getNonce(ECKey address) {
        return getNonce(new AionAddress(address.getAddress()));
    }

    private BigInteger getBalance(AionAddress address) {
        return this.blockchain.getRepository().getBalance(address);
    }

    private BigInteger getBalance(ECKey address) {
        return getBalance(new AionAddress(address.getAddress()));
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
        return new CodeAndArguments(
                        JarBuilder.buildJarForMainAndClassesAndUserlib(Statefulness.class),
                        new byte[0])
                .encodeToBytes();
    }

    private byte[] abiEncodeMethodCall(String method, Object... arguments) {
        return ABIUtil.encodeMethodArguments(method, arguments);
    }

    private List<BigInteger> getRandomValues(int num, int lowerBound, int upperBound) {
        List<BigInteger> values = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            values.add(BigInteger.valueOf(RandomUtils.nextInt(lowerBound, upperBound)));
        }
        return values;
    }

    /**
     * Since we are using random values here ... we want to be able to reproduce these amounts if a
     * test fails, so we print them off to console every time.
     */
    private void printValuesUsed(List<BigInteger> values) {
        System.out.println(
                "sendValueTransferTransactionsInBulk test is using the following values:");
        for (BigInteger value : values) {
            System.out.print(" " + value + " ");
        }
    }
}
