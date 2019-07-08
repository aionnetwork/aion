package org.aion.zero.impl.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.base.TransactionTypes;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.types.AionAddress;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.contracts.ContractUtils;
import org.aion.base.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FvmBulkTransactionTest {
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;
    private long energyPrice = 1;

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle =
                (new StandaloneBlockchain.Builder())
                        .withValidatorConfiguration("simple")
                        .withDefaultAccounts()
                        .build();
        blockchain = bundle.bc;
        deployerKey = bundle.privateKeys.get(0);
    }

    @After
    public void tearDown() {
        blockchain = null;
        deployerKey = null;
    }

    @Test
    public void sendContractCreationAndCallTransactionsInBulkTest() throws IOException {
        BigInteger expectedDeployerNonce = getNonce(this.deployerKey);

        // First, deploy a contract that we can call into.
        AionBlockSummary initialSummary =
                sendTransactionsInBulkInSingleBlock(
                        Collections.singletonList(
                                makeFvmContractCreateTransaction(
                                        this.deployerKey, expectedDeployerNonce)));
        expectedDeployerNonce = expectedDeployerNonce.add(BigInteger.ONE);

        // Grab the address of the newly deployed contract.
        AionAddress deployedContract =
                initialSummary.getReceipts().get(0).getTransaction().getContractAddress();

        int numFvmCreateTransactions = 10;
        int numFvmCallTransactions = 10;
        int numTransactions = numFvmCreateTransactions + numFvmCallTransactions;

        // Grab the initial data we need to track.
        BigInteger initialBalanceDeployer = getBalance(this.deployerKey);

        // Make the create transactions.
        List<AionTransaction> transactions = new ArrayList<>();
        for (int i = 0; i < numFvmCreateTransactions; i++) {
            transactions.add(
                    makeFvmContractCreateTransaction(this.deployerKey, expectedDeployerNonce));
            expectedDeployerNonce = expectedDeployerNonce.add(BigInteger.ONE);
        }

        // Make the call transactions.
        for (int i = 0; i < numFvmCallTransactions; i++) {
            transactions.add(
                    makeFvmContractCallTransaction(
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
            if (i < numFvmCreateTransactions) {
                contracts.add(
                        blockSummary
                                .getSummaries()
                                .get(i)
                                .getReceipt()
                                .getTransaction()
                                .getContractAddress());
            }
        }

        // Verify account states after the transactions have been processed.
        for (int i = 0; i < numFvmCreateTransactions; i++) {
            // Check that these contracts have code.
            assertTrue(this.blockchain.getRepository().getCode(contracts.get(i)).length > 0);
            assertEquals(BigInteger.ZERO, getBalance(contracts.get(i)));
            assertEquals(BigInteger.ZERO, getNonce(contracts.get(i)));
        }
        assertEquals(expectedDeployerBalance, getBalance(this.deployerKey));
        assertEquals(expectedDeployerNonce, getNonce(this.deployerKey));

        // Call into the contract to get its current 'count' to verify its state is correct.
        int count =
                getDeployedTickerCountValue(
                        this.deployerKey, expectedDeployerNonce, deployedContract);
        assertEquals(numFvmCallTransactions, count);
    }

    private AionBlockSummary sendTransactionsInBulkInSingleBlock(
            List<AionTransaction> transactions) {
        AionBlock parentBlock = this.blockchain.getBestBlock();
        AionBlock block =
                this.blockchain.createBlock(
                        parentBlock, transactions, false, parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult =
                this.blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        return connectResult.getRight();
    }

    // Deploys the Ticker.sol contract.
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
                        this.energyPrice,
                        TransactionTypes.DEFAULT);
        transaction.sign(this.deployerKey);
        return transaction;
    }

    private AionTransaction makeFvmContractCallTransaction(
            ECKey sender, BigInteger nonce, AionAddress contract) {
        // This hash will call the 'ticking' function of the deployed contract (this increments a
        // counter).
        byte[] callBytes = Hex.decode("dae29f29");

        AionTransaction transaction =
                newTransaction(
                        nonce,
                        new AionAddress(sender.getAddress()),
                        contract,
                        BigInteger.ZERO,
                        callBytes,
                        2_000_000,
                        this.energyPrice,
                        TransactionTypes.DEFAULT);
        transaction.sign(this.deployerKey);
        return transaction;
    }

    private int getDeployedTickerCountValue(ECKey sender, BigInteger nonce, AionAddress contract) {
        // This hash will call the 'getTicker' function of the deployed contract (giving us the
        // count).
        byte[] callBytes = Hex.decode("c0004213");

        AionTransaction transaction =
                newTransaction(
                        nonce,
                        new AionAddress(sender.getAddress()),
                        contract,
                        BigInteger.ZERO,
                        callBytes,
                        2_000_000,
                        this.energyPrice,
                        TransactionTypes.DEFAULT);
        transaction.sign(this.deployerKey);

        AionBlockSummary summary =
                sendTransactionsInBulkInSingleBlock(Collections.singletonList(transaction));
        return new DataWordImpl(summary.getReceipts().get(0).getTransactionOutput()).intValue();
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
}
