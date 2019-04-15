package org.aion.zero.impl.consensus;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.valid.TransactionTypeRule;
import org.aion.types.Address;
import org.aion.util.conversions.Hex;
import org.aion.vm.VirtualMachineProvider;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.StandaloneBlockchain.Builder;
import org.aion.zero.impl.StandaloneBlockchain.Bundle;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.valid.TransactionTypeValidator;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Consensus tests on balance transfers to regular accounts (not contracts). */
public class BalanceTransferConsensusTest {
    private static final byte[] SENDER_KEY =
            org.aion.util.conversions.Hex.decode(
                    "81e071e5bf2c155f641641d88b5956af52c768fbb90968979b20858d65d71f32aa935b67ac46480caaefcdd56dd31862e578694a99083e9fad88cb6df89fc7cb");
    private static final byte[] SENDER_ADDR =
            org.aion.util.conversions.Hex.decode(
                    "a00a4175a89a6ffbfdc45782771fba3f5e9da36baa69444f8f95e325430463e7");
    private static final BigInteger SENDER_BALANCE = new BigInteger("1000000000000000000000000");
    private static final byte[] MINER =
            Hex.decode("0000000000000000000000000000000000000000000000000000000000000000");
    private static final long ENERGY_PRICE = 10_123_456_789L;

    private StandaloneBlockchain blockchain;

    @Before
    public void setup() {
        Bundle bundle =
                new Builder()
                        .withDefaultAccounts(
                                Collections.singletonList(
                                        org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY)))
                        .withValidatorConfiguration("simple")
                        .withAvmEnabled()
                        .build();
        this.blockchain = bundle.bc;

        if (VirtualMachineProvider.isMachinesAreLive()) {
            return;
        }
        VirtualMachineProvider.initializeAllVirtualMachines();
    }

    @After
    public void tearDown() {
        this.blockchain = null;
        if (VirtualMachineProvider.isMachinesAreLive()) {
            VirtualMachineProvider.shutdownAllVirtualMachines();
        }
    }

    @Test
    public void testTransactionTypeBeforeTheFork() {
        // ensure that the fork was not triggered
        TransactionTypeRule.disallowAVMContractTransaction();

        BigInteger amount = BigInteger.TEN.pow(12).add(BigInteger.valueOf(293_865));
        BigInteger initialBalance = getBalance(Address.wrap(SENDER_ADDR));
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toBytes()).isEqualTo(MINER);

        // get contract address from precompiled factory
        Address to =
                Address.wrap("a0123456a89a6ffbfdc45782771fba3f5e9da36baa69444f8f95e325430463e7");

        // Make balance transfer transaction to precompiled contract.
        ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        to,
                        amount.toByteArray(),
                        new byte[0],
                        2_000_000,
                        ENERGY_PRICE,
                        (byte) 11); // legal type before the fork
        transaction.sign(key);

        // check that the transaction is valid
        assertThat(TransactionTypeValidator.isValid(transaction)).isTrue();

        // Process the transaction.
        Pair<ImportResult, AionBlockSummary> results = processTransactions(transaction, 1);

        // ensure transaction and block were valid
        AionBlockSummary blockSummary = results.getRight();
        AionTxReceipt receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertThat(receipt.isSuccessful()).isTrue();
        assertThat(receipt.getEnergyUsed()).isEqualTo(21000);
    }

    @Test
    public void testTransactionTypeAfterTheFork() {
        // triggering fork changes
        TransactionTypeRule.allowAVMContractTransaction();

        BigInteger amount = BigInteger.TEN.pow(12).add(BigInteger.valueOf(293_865));
        BigInteger initialBalance = getBalance(Address.wrap(SENDER_ADDR));
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toBytes()).isEqualTo(MINER);

        // get contract address from precompiled factory
        Address to =
                Address.wrap("a0123456a89a6ffbfdc45782771fba3f5e9da36baa69444f8f95e325430463e7");

        // Make balance transfer transaction to precompiled contract.
        ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        to,
                        amount.toByteArray(),
                        new byte[0],
                        2_000_000,
                        ENERGY_PRICE,
                        (byte) 11); // illegal type after the fork
        transaction.sign(key);

        // check that the transaction is not valid
        assertThat(TransactionTypeValidator.isValid(transaction)).isFalse();

        // Process the transaction.
        AionBlock parentBlock = this.blockchain.getRepository().blockStore.getBestBlock();
        AionBlock block =
                this.blockchain.createNewBlock(
                        parentBlock, Collections.singletonList(transaction), false);
        Pair<ImportResult, AionBlockSummary> results =
                this.blockchain.tryToConnectAndFetchSummary(block);

        assertThat(results.getLeft()).isEqualTo(ImportResult.INVALID_BLOCK);

        // cleaning up for future tests
        TransactionTypeRule.disallowAVMContractTransaction();
    }

    private static final String RECIPIENT1 =
            "a04272bb5f935fb170baf2998cb25dd15cc5794e7c5bac7241bec00c4971c7f8";
    private static final String STATE_ROOT1 =
            "d60ebda1ffb8ebdf1f8e92e66c6260320918efc2f5beba06e2a535487f2d8826";
    private static final String BLOCK_RECEIPTS_ROOT1 =
            "b6308552ce9fc04bc27d9cab5e4e8a39795b3f711dbef8c53776700110268a50";
    private static final String RECEIPT_TRIE1 =
            "f90125a0c0dce68e921f0559c03d8b072fd9b723961c7d17bbf7745ef45b4d5d721b264bb9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";

    @Test
    public void testBalanceTransfer() {
        BigInteger amount = BigInteger.TEN.pow(23).add(BigInteger.valueOf(13_897_651));
        BigInteger initialBalance = getBalance(Address.wrap(SENDER_ADDR));
        assertEquals(SENDER_BALANCE, initialBalance);
        assertArrayEquals(MINER, this.blockchain.getMinerCoinbase().toBytes());

        Address address = Address.wrap(Hex.decode(RECIPIENT1));
        assertEquals(BigInteger.ZERO, getBalance(address));

        AionTransaction transaction = makeBalanceTransferTransaction(address, amount);

        // Process the transaction.
        Pair<ImportResult, AionBlockSummary> results = processTransactions(transaction, 1);

        // Collect the consensus information from the block & receipt.
        AionBlockSummary blockSummary = results.getRight();
        AionTxReceipt receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertTrue(receipt.isSuccessful());

        byte[] stateRoot = blockSummary.getBlock().getStateRoot();
        byte[] blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        byte[] receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        assertArrayEquals(Hex.decode(STATE_ROOT1), stateRoot);
        assertArrayEquals(Hex.decode(BLOCK_RECEIPTS_ROOT1), blockReceiptsRoot);
        assertArrayEquals(Hex.decode(RECEIPT_TRIE1), receiptTrieEncoded);

        // Verify the sender's balance is as expected.
        BigInteger expectedBalance = new BigInteger("899999999785463689829861");
        assertEquals(expectedBalance, getBalance(Address.wrap(SENDER_ADDR)));

        // Verify that the recipient's balance is as expected.
        assertEquals(amount, getBalance(address));

        // Verify that the miner's balance is as expected.
        BigInteger expectedMinerBalance = new BigInteger("749212067557748651");
        assertEquals(expectedMinerBalance, getBalance(Address.wrap(MINER)));
    }

    private static final String RECIPIENT2 =
            "a03272bb5f935fb170baf2998cb25dd15cc5794e7c5bac7241bec00c4971c7f7";
    private static final String RECIPIENT3 =
            "a05272bb5f935fb170baf2998cb25dd15cc5794e7c5bac7241bec00c4971c7f9";
    private static final String RECIPIENT4 =
            "a06272bb5f935fb170baf2998cb25dd15cc5794e7c5bac7241bec00c4971c7fa";
    private static final String RECIPIENT5 =
            "a07272bb5f935fb170baf2998cb25dd15cc5794e7c5bac7241bec00c4971c7fb";
    private static final String STATE_ROOT2 =
            "6cd80c2d8398ccde9c8ada11c14e93a8d81e3e8825d205b7f0d3357514cd0fb5";
    private static final String BLOCK_RECEIPTS_ROOT2 =
            "aa970e6870850fa0f5affab7615da762d7b2204113395d05bb0e9679fd7028d3";
    private static final String RECEIPT_TRIE2 =
            "f90125a0bd01553d145789ba229ac9b2dd26c0570854c69ecfe1a1aa4921d82169067e20b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";

    @Test
    public void testBalanceTransfers() {
        BigInteger amount = BigInteger.TEN.pow(22).add(BigInteger.valueOf(123_987_156));
        BigInteger initialBalance = getBalance(Address.wrap(SENDER_ADDR));
        assertEquals(SENDER_BALANCE, initialBalance);
        assertArrayEquals(MINER, this.blockchain.getMinerCoinbase().toBytes());

        // Get the recipients.
        List<Address> recipients = produceRecipients();
        for (Address recipient : recipients) {
            assertEquals(BigInteger.ZERO, getBalance(recipient));
        }

        List<AionTransaction> transactions = makeBalanceTransferTransactions(recipients, amount);

        // Process the transactions.
        Pair<ImportResult, AionBlockSummary> results =
                processTransactions(transactions, transactions.size());

        // Collect the consensus information from the block & receipt.
        AionBlockSummary blockSummary = results.getRight();
        AionTxReceipt receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertTrue(receipt.isSuccessful());

        byte[] stateRoot = blockSummary.getBlock().getStateRoot();
        byte[] blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        byte[] receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        assertArrayEquals(Hex.decode(STATE_ROOT2), stateRoot);
        assertArrayEquals(Hex.decode(BLOCK_RECEIPTS_ROOT2), blockReceiptsRoot);
        assertArrayEquals(Hex.decode(RECEIPT_TRIE2), receiptTrieEncoded);

        // Verify the sender's balance is as expected.
        BigInteger expectedBalance = new BigInteger("949999998927317898701780");
        assertEquals(expectedBalance, getBalance(Address.wrap(SENDER_ADDR)));

        // Verify that the recipients' balances are as expected.
        for (Address recipient : recipients) {
            assertEquals(amount, getBalance(recipient));
        }

        // Verify that the miner's balance is as expected.
        BigInteger expectedMinerBalance = new BigInteger("750070212742838603");
        assertEquals(expectedMinerBalance, getBalance(Address.wrap(MINER)));
    }

    private List<Address> produceRecipients() {
        List<Address> addresses = new ArrayList<>();
        addresses.add(Address.wrap(Hex.decode(RECIPIENT1)));
        addresses.add(Address.wrap(Hex.decode(RECIPIENT2)));
        addresses.add(Address.wrap(Hex.decode(RECIPIENT3)));
        addresses.add(Address.wrap(Hex.decode(RECIPIENT4)));
        addresses.add(Address.wrap(Hex.decode(RECIPIENT5)));
        return addresses;
    }

    private Pair<ImportResult, AionBlockSummary> processTransactions(
            List<AionTransaction> transactions, int numNonRejectedTransactions) {
        AionBlock parentBlock = this.blockchain.getRepository().blockStore.getBestBlock();
        AionBlock block = this.blockchain.createNewBlock(parentBlock, transactions, false);
        Pair<ImportResult, AionBlockSummary> results =
                this.blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(ImportResult.IMPORTED_BEST, results.getLeft());
        assertEquals(numNonRejectedTransactions, results.getRight().getSummaries().size());
        return results;
    }

    private Pair<ImportResult, AionBlockSummary> processTransactions(
            AionTransaction transaction, int numNonRejectedTransactions) {
        return processTransactions(
                Collections.singletonList(transaction), numNonRejectedTransactions);
    }

    private static List<AionTransaction> makeBalanceTransferTransactions(
            List<Address> recipients, BigInteger amount) {
        List<AionTransaction> transactions = new ArrayList<>();

        BigInteger nonce = BigInteger.ZERO;
        for (Address recipient : recipients) {
            transactions.add(makeBalanceTransferTransaction(recipient, amount, nonce));
            nonce = nonce.add(BigInteger.ONE);
        }

        return transactions;
    }

    private static AionTransaction makeBalanceTransferTransaction(
            Address recipient, BigInteger amount) {
        return makeBalanceTransferTransaction(recipient, amount, BigInteger.ZERO);
    }

    private static AionTransaction makeBalanceTransferTransaction(
            Address recipient, BigInteger amount, BigInteger nonce) {
        org.aion.crypto.ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        nonce.toByteArray(),
                        recipient,
                        amount.toByteArray(),
                        new byte[] {0x1, 0x2, 0x3},
                        2_000_000,
                        ENERGY_PRICE);
        transaction.sign(key);
        return transaction;
    }

    private BigInteger getBalance(Address address) {
        return this.blockchain.getRepository().getBalance(address);
    }
}
