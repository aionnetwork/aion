package org.aion.zero.impl.consensus;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.precompiled.ContractFactory;
import org.aion.vm.api.types.Address;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.StandaloneBlockchain.Builder;
import org.aion.zero.impl.StandaloneBlockchain.Bundle;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Consensus tests meant to test out balance transfers to fvm contracts. */
public class FvmBalanceTransferConsensusTest {
    private static final byte[] SENDER_KEY =
            Hex.decode(
                    "81e071e5bf2c155f641641d88b5956af52c768fbb90968979b20858d65d71f32aa935b67ac46480caaefcdd56dd31862e578694a99083e9fad88cb6df89fc7cb");
    private static final byte[] SENDER_ADDR =
            Hex.decode("a00a4175a89a6ffbfdc45782771fba3f5e9da36baa69444f8f95e325430463e7");
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
    }

    @After
    public void tearDown() {
        this.blockchain = null;
    }

    @Test
    public void testTransferToPrecompiledContract() {
        BigInteger amount = BigInteger.TEN.pow(12).add(BigInteger.valueOf(293_865));
        BigInteger initialBalance = getBalance(Address.wrap(SENDER_ADDR));
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toBytes()).isEqualTo(MINER);

        // ensure bridge is viewed as precompiled contract
        Address bridge =
                Address.wrap("0000000000000000000000000000000000000000000000000000000000000200");
        assertThat(ContractFactory.isPrecompiledContract(bridge)).isTrue();

        // Make balance transfer transaction to precompiled contract.
        ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        bridge,
                        amount.toByteArray(),
                        new byte[] {},
                        2_000_000,
                        ENERGY_PRICE);
        transaction.sign(key);

        // Process the transaction.
        Pair<ImportResult, AionBlockSummary> results = processTransaction(transaction, 1);

        // Collect the consensus information from the block & receipt.
        AionBlockSummary blockSummary = results.getRight();
        AionTxReceipt receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertThat(receipt.isSuccessful()).isTrue();
        assertThat(receipt.getEnergyUsed()).isEqualTo(21000);

        byte[] stateRoot = blockSummary.getBlock().getStateRoot();
        byte[] blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        byte[] receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        String expectedRoot = "419F9A4C02612094A9B9A2EAC5CC6DF761214882C35267328297D09246206A9A";
        String expectedReceiptsRoot =
                "9A73184E9A5514164D980452E2283892D0487B6833D3F9F782C8686BFDA1B809";
        String expectedReceiptsTrie =
                "f90125a034b98e6a317bd93f39959d41cccafec864329cadfb0d8d7bfc5d30df5612e401b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";
        assertThat(stateRoot).isEqualTo(Hex.decode(expectedRoot));
        assertThat(blockReceiptsRoot).isEqualTo(Hex.decode(expectedReceiptsRoot));
        assertThat(receiptTrieEncoded).isEqualTo(Hex.decode(expectedReceiptsTrie));

        // Verify that the sender has the expected balance.
        BigInteger expectedBalance = new BigInteger("999999999786407407137135");
        assertThat(getBalance(Address.wrap(SENDER_ADDR))).isEqualTo(expectedBalance);

        // Verify that the contract has the expected balance.
        BigInteger contractBalance = getBalance(bridge);
        assertThat(contractBalance).isEqualTo(amount);

        // Verify that the miner has the expected balance.
        BigInteger expectedMinerBalance = new BigInteger("749210123854045163");
        assertThat(getBalance(Address.wrap(MINER))).isEqualTo(expectedMinerBalance);
    }

    @Test
    public void testCallToPrecompiledContract() {
        BigInteger initialBalance = getBalance(Address.wrap(SENDER_ADDR));
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toBytes()).isEqualTo(MINER);

        // ensure bridge is viewed as precompiled contract
        Address bridge =
                Address.wrap("0000000000000000000000000000000000000000000000000000000000000200");
        assertThat(ContractFactory.isPrecompiledContract(bridge)).isTrue();

        // Make call transaction to precompiled contract.
        ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        bridge,
                        BigInteger.ZERO.toByteArray(),
                        Hex.decode(
                                "a6f9dae1a048613dd3cb89685cb3f9cfa410ecf606c7ec7320e721edacd194050828c6b0"),
                        2_000_000,
                        ENERGY_PRICE);
        transaction.sign(key);

        // Process the transaction.
        Pair<ImportResult, AionBlockSummary> results = processTransaction(transaction, 1);

        // Collect the consensus information from the block & receipt.
        AionBlockSummary blockSummary = results.getRight();
        AionTxReceipt receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertThat(receipt.isSuccessful()).isTrue();
        assertThat(receipt.getEnergyUsed()).isEqualTo(23304);

        byte[] stateRoot = blockSummary.getBlock().getStateRoot();
        byte[] blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        byte[] receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        String expectedRoot = "7D91DD44FC7AABC0291BB6FD9CBCD70BEB3859FDD10E0F2356D2B5947EBC55D7";
        String expectedReceiptsRoot =
                "E897C7EB2531B5CC45293AE3EC0CC156B0FACEAEF3046D671A08D6C91BA88827";
        String expectedReceiptsTrie =
                "f90125a06ed1cbe0a46be351c90404aaeb7d12d9737d4c8a070e8c63dc1cfc0d7441fbdbb9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";
        assertThat(stateRoot).isEqualTo(Hex.decode(expectedRoot));
        assertThat(blockReceiptsRoot).isEqualTo(Hex.decode(expectedReceiptsRoot));
        assertThat(receiptTrieEncoded).isEqualTo(Hex.decode(expectedReceiptsTrie));

        // Verify that the sender has the expected balance.
        BigInteger expectedBalance = new BigInteger("999999999764082962989144");
        assertThat(getBalance(Address.wrap(SENDER_ADDR))).isEqualTo(expectedBalance);

        // Verify that the contract has the expected balance.
        BigInteger contractBalance = getBalance(bridge);
        assertThat(contractBalance).isEqualTo(BigInteger.ZERO);

        // Verify that the miner has the expected balance.
        BigInteger expectedMinerBalance = new BigInteger("749233448298487019");
        assertThat(getBalance(Address.wrap(MINER))).isEqualTo(expectedMinerBalance);
    }

    @Test
    public void testTransferToPrecompiledBlake2bContractWithoutData() {
        BigInteger amount = BigInteger.TEN.pow(12).add(BigInteger.valueOf(293_865));
        BigInteger initialBalance = getBalance(Address.wrap(SENDER_ADDR));
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toBytes()).isEqualTo(MINER);

        // get contract address from precompiled factory
        Address blake2b = ContractFactory.getBlake2bHashContractAddress();
        assertThat(ContractFactory.isPrecompiledContract(blake2b)).isTrue();

        // Make balance transfer transaction to precompiled contract.
        ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        blake2b,
                        amount.toByteArray(),
                        new byte[] {},
                        2_000_000,
                        ENERGY_PRICE);
        transaction.sign(key);

        // Process the transaction.
        Pair<ImportResult, AionBlockSummary> results = processTransaction(transaction, 1);

        // Collect the consensus information from the block & receipt.
        AionBlockSummary blockSummary = results.getRight();
        AionTxReceipt receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertThat(receipt.isSuccessful()).isFalse();
        assertThat(receipt.getEnergyUsed()).isEqualTo(21010);

        byte[] stateRoot = blockSummary.getBlock().getStateRoot();
        byte[] blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        byte[] receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        String expectedRoot = "37C62813178F694DA9A82398F63CBD7CFFD913B6CE6B2080A8B9374E688EF37E";
        String expectedReceiptsRoot =
                "BE3697A1A56274D8378EE9EF40C308BFBFC8BE48AD5250E1EDCF04A26E11F021";
        String expectedReceiptsTrie =
                "f90125a0619b938d5971660fd2a83cf7c2718653bb5f9b8e78b3b5e7a361e66635c32fd1b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";
        assertThat(stateRoot).isEqualTo(Hex.decode(expectedRoot));
        assertThat(blockReceiptsRoot).isEqualTo(Hex.decode(expectedReceiptsRoot));
        assertThat(receiptTrieEncoded).isEqualTo(Hex.decode(expectedReceiptsTrie));

        // Verify that the sender has the expected balance.
        BigInteger expectedBalance = new BigInteger("999999979753086422000000");
        assertThat(getBalance(Address.wrap(SENDER_ADDR))).isEqualTo(expectedBalance);

        // Verify that the contract has the expected balance.
        BigInteger contractBalance = getBalance(blake2b);
        assertThat(contractBalance).isEqualTo(BigInteger.ZERO);

        // Verify that the miner has the expected balance.
        BigInteger expectedMinerBalance = new BigInteger("749210225088613053");
        assertThat(getBalance(Address.wrap(MINER))).isEqualTo(expectedMinerBalance);
    }

    @Test
    public void testTransferToPrecompiledBlake2bContractWithData() {
        // ensure the contract is live
        Properties properties = new Properties();
        properties.put("fork0.3.2", "0");
        CfgAion cfg = CfgAion.inst();
        cfg.getFork().setProperties(properties);

        BigInteger amount = BigInteger.TEN.pow(12).add(BigInteger.valueOf(293_865));
        BigInteger initialBalance = getBalance(Address.wrap(SENDER_ADDR));
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toBytes()).isEqualTo(MINER);

        // get contract address from precompiled factory
        Address blake2b = ContractFactory.getBlake2bHashContractAddress();
        assertThat(ContractFactory.isPrecompiledContract(blake2b)).isTrue();

        // Make balance transfer transaction to precompiled contract.
        ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        blake2b,
                        amount.toByteArray(),
                        Hex.decode("abcdef0123456789"),
                        2_000_000,
                        ENERGY_PRICE);
        transaction.sign(key);

        // Process the transaction.
        Pair<ImportResult, AionBlockSummary> results = processTransaction(transaction, 1);

        // Collect the consensus information from the block & receipt.
        AionBlockSummary blockSummary = results.getRight();
        AionTxReceipt receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertThat(receipt.isSuccessful()).isTrue();
        assertThat(receipt.getEnergyUsed()).isEqualTo(21526);
        assertThat(receipt.getTransactionOutput())
                .isEqualTo(
                        Hex.decode(
                                "ac86b78afd9bdda3641a47a4aff2a7ee26acd40cc534d63655e9dfbf3f890a02"));

        byte[] stateRoot = blockSummary.getBlock().getStateRoot();
        byte[] blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        byte[] receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        String expectedRoot = "12CB8B149B8465CE3A3BD3AF3D19EB39C3DADD528880BB7CCB4FC4ED10758E68";
        String expectedReceiptsRoot =
                "55CB056398D1AA49328E11E97BA034C532D312CD8C4959F3E609FD25E2FC0C62";
        String expectedReceiptsTrie =
                "f90125a0188897e00f24f83eb6a27004dcb96cb4fa4370a1e7b072f2c21c9a437146fa69b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";
        assertThat(stateRoot).isEqualTo(Hex.decode(expectedRoot));
        assertThat(blockReceiptsRoot).isEqualTo(Hex.decode(expectedReceiptsRoot));
        assertThat(receiptTrieEncoded).isEqualTo(Hex.decode(expectedReceiptsTrie));

        // Verify that the sender has the expected balance.
        BigInteger expectedBalance = new BigInteger("999999999781082468866121");
        assertThat(getBalance(Address.wrap(SENDER_ADDR))).isEqualTo(expectedBalance);

        // Verify that the contract has the expected balance.
        BigInteger contractBalance = getBalance(blake2b);
        assertThat(contractBalance).isEqualTo(amount);

        // Verify that the miner has the expected balance.
        BigInteger expectedMinerBalance = new BigInteger("749215448792316177");
        assertThat(getBalance(Address.wrap(MINER))).isEqualTo(expectedMinerBalance);
    }

    @Test
    public void testCallToPrecompiledBlake2bContract() {
        // ensure the contract is live (after fork)
        Properties properties = new Properties();
        properties.put("fork0.3.2", "0");
        CfgAion cfg = CfgAion.inst();
        cfg.getFork().setProperties(properties);

        BigInteger initialBalance = getBalance(Address.wrap(SENDER_ADDR));
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toBytes()).isEqualTo(MINER);

        // get contract address from precompiled factory
        Address blake2b = ContractFactory.getBlake2bHashContractAddress();
        assertThat(ContractFactory.isPrecompiledContract(blake2b)).isTrue();

        // Make call transaction to precompiled contract.
        ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        blake2b,
                        BigInteger.ZERO.toByteArray(),
                        Hex.decode("abcdef0123456789"),
                        2_000_000,
                        ENERGY_PRICE);
        transaction.sign(key);

        // Process the transaction.
        Pair<ImportResult, AionBlockSummary> results = processTransaction(transaction, 1);

        // Collect the consensus information from the block & receipt.
        AionBlockSummary blockSummary = results.getRight();
        AionTxReceipt receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertThat(receipt.isSuccessful()).isTrue();
        assertThat(receipt.getEnergyUsed()).isEqualTo(21526);
        assertThat(receipt.getTransactionOutput())
                .isEqualTo(
                        Hex.decode(
                                "ac86b78afd9bdda3641a47a4aff2a7ee26acd40cc534d63655e9dfbf3f890a02"));

        byte[] stateRoot = blockSummary.getBlock().getStateRoot();
        byte[] blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        byte[] receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        String expectedRoot = "ABBF04753B23F36F04CC4C0AEF7969C29236A4FCD982F3A4D824CB1EDE60AF4B";
        String expectedReceiptsRoot =
                "72C976AF0CE4627E1CA3FA1EA5EB798A0CB7118E186626E04ECA7198887AD3A1";
        String expectedReceiptsTrie =
                "f90125a03441b15983ce33839dfd1a7b62c1fa86ca4ff9df76d263bcc7c8f1d14225dc5cb9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";
        assertThat(stateRoot).isEqualTo(Hex.decode(expectedRoot));
        assertThat(blockReceiptsRoot).isEqualTo(Hex.decode(expectedReceiptsRoot));
        assertThat(receiptTrieEncoded).isEqualTo(Hex.decode(expectedReceiptsTrie));

        // Verify that the sender has the expected balance.
        BigInteger expectedBalance = new BigInteger("999999999782082469159986");
        assertThat(getBalance(Address.wrap(SENDER_ADDR))).isEqualTo(expectedBalance);

        // Verify that the contract has the expected balance.
        BigInteger contractBalance = getBalance(blake2b);
        assertThat(contractBalance).isEqualTo(BigInteger.ZERO);

        // Verify that the miner has the expected balance.
        BigInteger expectedMinerBalance = new BigInteger("749215448792316177");
        assertThat(getBalance(Address.wrap(MINER))).isEqualTo(expectedMinerBalance);
    }

    private static final String CONTRACT =
            "a04272bb5f935fb170baf2998cb25dd15cc5794e7c5bac7241bec00c4971c7f8";
    private static final String STATE_ROOT1 =
            "043f209d36ab740fe9e126705bc7933a27b3a4627c47efd24ee1d039b6fde25f";
    private static final String BLOCK_RECEIPTS_ROOT1 =
            "cf3ff96895af00a66b8f2dd93d6401d8edd83b6fb6a6b0d8d1b0059802d2b673";
    private static final String RECEIPT_TRIE1 =
            "f90125a02d7af5f12c661cdbda95a70593fc63ea4896190fb49c926e99d83f8da7ab424ab9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";

    @Test
    public void testTransferUponCreationToNonPayableConstructor() {
        BigInteger amount = BigInteger.TEN.pow(12).add(BigInteger.valueOf(293_865));
        BigInteger initialBalance = getBalance(Address.wrap(SENDER_ADDR));
        assertEquals(SENDER_BALANCE, initialBalance);
        assertArrayEquals(MINER, this.blockchain.getMinerCoinbase().toBytes());

        // Make the create & transfer transaction.
        AionTransaction transaction =
                makeCreateAndTransferToFvmNonpayableConstructorContractTx(amount);
        assertArrayEquals(Hex.decode(CONTRACT), transaction.getContractAddress().toBytes());

        // Process the transaction.
        Pair<ImportResult, AionBlockSummary> results = processTransaction(transaction, 1);

        // Collect the consensus information from the block & receipt.
        AionBlockSummary blockSummary = results.getRight();
        AionTxReceipt receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertFalse(receipt.isSuccessful());

        byte[] stateRoot = blockSummary.getBlock().getStateRoot();
        byte[] blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        byte[] receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        assertArrayEquals(Hex.decode(STATE_ROOT1), stateRoot);
        assertArrayEquals(Hex.decode(BLOCK_RECEIPTS_ROOT1), blockReceiptsRoot);
        assertArrayEquals(Hex.decode(RECEIPT_TRIE1), receiptTrieEncoded);

        // Verify that the sender has the expected balance.
        BigInteger expectedBalance = new BigInteger("999999997708454321241960");
        assertEquals(expectedBalance, getBalance(Address.wrap(SENDER_ADDR)));

        // Verify that the contract has the expected balance.
        BigInteger contractBalance = getBalance(transaction.getContractAddress());
        assertEquals(BigInteger.ZERO, contractBalance);

        // Verify that the miner has the expected balance.
        BigInteger expectedMinerBalance = new BigInteger("751289076940234203");
        assertEquals(expectedMinerBalance, getBalance(Address.wrap(MINER)));
    }

    private BigInteger getEnergyCost(AionTxReceipt receipt) {
        return BigInteger.valueOf(receipt.getEnergyUsed())
                .multiply(BigInteger.valueOf(ENERGY_PRICE));
    }

    private static final String STATE_ROOT2 =
            "86e9e3551f2376ee521300972206d6d87731f406ef0a9e29f001e4766ba78e4b";
    private static final String BLOCK_RECEIPTS_ROOT2 =
            "292c8ce512aa7c0fc0d2bf67518aceea8699d2d0b5558a494a89a46dc67a7669";
    private static final String RECEIPT_TRIE2 =
            "f90125a054b2261c6ca2d93a93617360523731e3506ea97e01a91af95d27ae270f5ecf65b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";

    @Test
    public void testTransferUponCreationToPayableConstructor() {
        BigInteger amount = BigInteger.TEN.pow(11).add(BigInteger.valueOf(1_234_578));
        BigInteger initialBalance = getBalance(Address.wrap(SENDER_ADDR));
        assertEquals(SENDER_BALANCE, initialBalance);
        assertArrayEquals(MINER, this.blockchain.getMinerCoinbase().toBytes());

        // Make the create & transfer transaction.
        AionTransaction transaction =
                makeCreateAndTransferToFvmPayableConstructorContractTx(amount);
        assertArrayEquals(Hex.decode(CONTRACT), transaction.getContractAddress().toBytes());

        // Process the transaction.
        Pair<ImportResult, AionBlockSummary> results = processTransaction(transaction, 1);

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

        BigInteger expectedBalance = new BigInteger("999999997714155060747479");
        assertEquals(expectedBalance, getBalance(Address.wrap(SENDER_ADDR)));

        // Verify that the contract has the expected balance.
        BigInteger contractBalance = getBalance(transaction.getContractAddress());
        assertEquals(amount, contractBalance);

        // Verify that the miner has the expected balance.
        BigInteger expectedMinerBalance = new BigInteger("751283276199494106");
        assertEquals(expectedMinerBalance, getBalance(Address.wrap(MINER)));
    }

    private static final String STATE_ROOT3 =
            "4ffe139207bb504713c104bfc9adb53fee3e7d45abec87201e516770532d7370";
    private static final String BLOCK_RECEIPTS_ROOT3 =
            "dcfdef14452702e59ae10013411e04bff6aa34887fbe3a873ea827d8b1cda94d";
    private static final String RECEIPT_TRIE3 =
            "f90125a0c99519d9a99ddc099d59165d502b881b7cb60caab0717d8e0237cf4b0676b464b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";
    private static final String STATE_ROOT4 =
            "a5c0dceb6c92575dab5555f6c0acd5d207ea6143da2533c7ed10eba0eeb42c61";
    private static final String BLOCK_RECEIPTS_ROOT4 =
            "6b6b16ecb1272a2a7e23f250eb0d49a93d00d928721d39f21e741f791c044267";
    private static final String RECEIPT_TRIE4 =
            "f90125a091381a2543c775ee77441efa63260453c5f53243a63efbf4df21452fe2498dd6b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";

    @Test
    public void testTransferAfterCreatingContractToNonPayableFunction() {
        BigInteger amount = BigInteger.TEN.pow(9).add(BigInteger.valueOf(3_124_587));
        BigInteger initialBalance = getBalance(Address.wrap(SENDER_ADDR));
        assertEquals(SENDER_BALANCE, initialBalance);
        assertArrayEquals(MINER, this.blockchain.getMinerCoinbase().toBytes());

        // Make the create transaction.
        AionTransaction transaction = makeCreatePayableContractTx();
        assertArrayEquals(Hex.decode(CONTRACT), transaction.getContractAddress().toBytes());

        // Process the transaction.
        Pair<ImportResult, AionBlockSummary> results = processTransaction(transaction, 1);

        // Collect the consensus information from the block & receipt.
        AionBlockSummary blockSummary = results.getRight();
        AionTxReceipt receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertTrue(receipt.isSuccessful());

        byte[] stateRoot = blockSummary.getBlock().getStateRoot();
        byte[] blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        byte[] receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        assertArrayEquals(Hex.decode(STATE_ROOT3), stateRoot);
        assertArrayEquals(Hex.decode(BLOCK_RECEIPTS_ROOT3), blockReceiptsRoot);
        assertArrayEquals(Hex.decode(RECEIPT_TRIE3), receiptTrieEncoded);

        // Make the balance transfer transaction.
        Address contract = transaction.getContractAddress();
        transaction = makeCallNonpayableFunctionTx(contract, amount);

        // Process the transaction.
        results = processTransaction(transaction, 1);

        // Collect the consensus information from the block & receipt.
        blockSummary = results.getRight();
        receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertFalse(receipt.isSuccessful());

        stateRoot = blockSummary.getBlock().getStateRoot();
        blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        assertArrayEquals(Hex.decode(STATE_ROOT4), stateRoot);
        assertArrayEquals(Hex.decode(BLOCK_RECEIPTS_ROOT4), blockReceiptsRoot);
        assertArrayEquals(Hex.decode(RECEIPT_TRIE4), receiptTrieEncoded);

        // Verify the sender's balance is as expected.
        BigInteger expectedBalance = new BigInteger("999999997458455555837605");
        assertEquals(expectedBalance, getBalance(Address.wrap(SENDER_ADDR)));

        // Verify that the contract has the expected balance.
        assertEquals(BigInteger.ZERO, getBalance(contract));

        // Verify that the miner has the expected balance.
        BigInteger expectedMinerBalance = new BigInteger("1500539496606935792");
        assertEquals(expectedMinerBalance, getBalance(Address.wrap(MINER)));
    }

    private static final String STATE_ROOT5 =
            "2fcb5e9463cad96a5cc7154e0e3236aa8d32e3c68212fd263813d87f72b14cfd";
    private static final String BLOCK_RECEIPTS_ROOT5 =
            "d75eb82ed9377f0ebcc579f13bd3d8bd7f42c30208c8f0118d0bcfc90d641a79";
    private static final String RECEIPT_TRIE5 =
            "f90125a0763548459d92b2c6106837fd97e49be68da58767cadbab8fe490cb66e6043bf2b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";

    @Test
    public void testTransferAfterCreatingContractToPayableFunction() {
        BigInteger amount = BigInteger.TEN.pow(17).add(BigInteger.valueOf(38_193));
        BigInteger initialBalance = getBalance(Address.wrap(SENDER_ADDR));
        assertEquals(SENDER_BALANCE, initialBalance);
        assertArrayEquals(MINER, this.blockchain.getMinerCoinbase().toBytes());

        // Make the create transaction.
        AionTransaction transaction = makeCreatePayableContractTx();
        assertArrayEquals(Hex.decode(CONTRACT), transaction.getContractAddress().toBytes());

        // Process the transaction.
        Pair<ImportResult, AionBlockSummary> results = processTransaction(transaction, 1);

        // Collect the consensus information from the block & receipt.
        AionBlockSummary blockSummary = results.getRight();
        AionTxReceipt receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertTrue(receipt.isSuccessful());

        byte[] stateRoot = blockSummary.getBlock().getStateRoot();
        byte[] blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        byte[] receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        assertArrayEquals(Hex.decode(STATE_ROOT3), stateRoot);
        assertArrayEquals(Hex.decode(BLOCK_RECEIPTS_ROOT3), blockReceiptsRoot);
        assertArrayEquals(Hex.decode(RECEIPT_TRIE3), receiptTrieEncoded);

        // Make the balance transfer transaction.
        Address contract = transaction.getContractAddress();
        transaction = makeCallPayableFunctionTx(contract, amount);

        // Process the transaction.
        results = processTransaction(transaction, 1);

        // Collect the consensus information from the block & receipt.
        blockSummary = results.getRight();
        receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertTrue(receipt.isSuccessful());

        stateRoot = blockSummary.getBlock().getStateRoot();
        blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        assertArrayEquals(Hex.decode(STATE_ROOT5), stateRoot);
        assertArrayEquals(Hex.decode(BLOCK_RECEIPTS_ROOT5), blockReceiptsRoot);
        assertArrayEquals(Hex.decode(RECEIPT_TRIE5), receiptTrieEncoded);

        // Verify the sender's balance is as expected.
        BigInteger expectedBalance = new BigInteger("999999897458394815058678");
        assertEquals(expectedBalance, getBalance(Address.wrap(SENDER_ADDR)));

        // Verify that the contract has the expected balance.
        assertEquals(amount, getBalance(contract));

        // Verify that the miner has the expected balance.
        BigInteger expectedMinerBalance = new BigInteger("1500539557347676526");
        assertEquals(expectedMinerBalance, getBalance(Address.wrap(MINER)));
    }

    private static final String STATE_ROOT6 =
            "5ea37f5754ecc75f5d41bf300b6f379c9f72eb6e50c86d7f8a52b36325629ae4";
    private static final String BLOCK_RECEIPTS_ROOT6 =
            "484e54668297bf2004c932eb3237f24a465d9c9a778cf2a9434d61b532dd9908";
    private static final String RECEIPT_TRIE6 =
            "f90125a02912545d2a9f54bdd96dc2fc812470730c197583b9d75b56a854c05215451522b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";
    private static final String STATE_ROOT7 =
            "440a881c8e4fba775e855a9874f56f559e6529f69e8cd6ae17860e3ac2e79af6";
    private static final String BLOCK_RECEIPTS_ROOT7 =
            "c32c4f95e92957cbf6ef709b431654fbaa11dc9c290687f997b3d5e78deacd2c";
    private static final String RECEIPT_TRIE7 =
            "f90125a0734a96fd117112031af907c63609a00bc136d117f132be130b0f32b0be9eda0eb9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";

    @Test
    public void testTransferAfterCreatingToNonPayableFallbackFunction() {
        BigInteger amount = BigInteger.TEN.pow(17).add(BigInteger.valueOf(38_193));
        BigInteger initialBalance = getBalance(Address.wrap(SENDER_ADDR));
        assertEquals(SENDER_BALANCE, initialBalance);
        assertArrayEquals(MINER, this.blockchain.getMinerCoinbase().toBytes());

        // Make the create transaction.
        AionTransaction transaction = makeCreateNonpayableFallbackContractTx();
        assertArrayEquals(Hex.decode(CONTRACT), transaction.getContractAddress().toBytes());

        // Process the transaction.
        Pair<ImportResult, AionBlockSummary> results = processTransaction(transaction, 1);

        // Collect the consensus information from the block & receipt.
        AionBlockSummary blockSummary = results.getRight();
        AionTxReceipt receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertTrue(receipt.isSuccessful());

        byte[] stateRoot = blockSummary.getBlock().getStateRoot();
        byte[] blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        byte[] receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        assertArrayEquals(Hex.decode(STATE_ROOT6), stateRoot);
        assertArrayEquals(Hex.decode(BLOCK_RECEIPTS_ROOT6), blockReceiptsRoot);
        assertArrayEquals(Hex.decode(RECEIPT_TRIE6), receiptTrieEncoded);

        // Make the balance transfer transaction.
        Address contract = transaction.getContractAddress();
        transaction = makeCallFallbackFunctionTx(contract, amount);

        // Process the transaction.
        results = processTransaction(transaction, 1);

        // Collect the consensus information from the block & receipt.
        blockSummary = results.getRight();
        receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertFalse(receipt.isSuccessful());

        stateRoot = blockSummary.getBlock().getStateRoot();
        blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        assertArrayEquals(Hex.decode(STATE_ROOT7), stateRoot);
        assertArrayEquals(Hex.decode(BLOCK_RECEIPTS_ROOT7), blockReceiptsRoot);
        assertArrayEquals(Hex.decode(RECEIPT_TRIE7), receiptTrieEncoded);

        // Verify the sender's balance is as expected.
        BigInteger expectedBalance = new BigInteger("999999997488350123735522");
        assertEquals(expectedBalance, getBalance(Address.wrap(SENDER_ADDR)));

        // Verify that the contract has the expected balance.
        assertEquals(BigInteger.ZERO, getBalance(contract));

        // Verify that the miner has the expected balance.
        BigInteger expectedMinerBalance = new BigInteger("1500509602039037875");
        assertEquals(expectedMinerBalance, getBalance(Address.wrap(MINER)));
    }

    private static final String STATE_ROOT8 =
            "7ab509766ece9a4d307fd7cd8c2c4ceae9f5ed7b75399668d7b90985d9b3b123";
    private static final String BLOCK_RECEIPTS_ROOT8 =
            "7026679223b9afd81c159d4aa6f65d78d1ae1b47e9cfbb0ddda0301e9ad1fff3";
    private static final String RECEIPT_TRIE8 =
            "f90125a0b017205b58b2790dddf4450b3b9289f140bf7e0c59f3136b5eb77170c5242ff4b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";
    private static final String STATE_ROOT9 =
            "35124c16c8fddf510ecd691a891c2732cd2e19bf348722f408210ed98b1b6d84";
    private static final String BLOCK_RECEIPTS_ROOT9 =
            "d4b95308896b1cefabb82e710f06acfa9be421995fe5ec0ebcd2128c00f50d9d";
    private static final String RECEIPT_TRIE9 =
            "f90125a08eec03dcd128e093c0ee7e9d6187f3448722d7bb9c24c9686a9f0486ac810c00b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0";

    @Test
    public void testTransferAfterCreatingToPayableFallbackFunction() {
        BigInteger amount = BigInteger.TEN.pow(17).add(BigInteger.valueOf(38_193));
        BigInteger initialBalance = getBalance(Address.wrap(SENDER_ADDR));
        assertEquals(SENDER_BALANCE, initialBalance);
        assertArrayEquals(MINER, this.blockchain.getMinerCoinbase().toBytes());

        // Make the create transaction.
        AionTransaction transaction = makeCreatePayableFallbackContractTx();
        assertArrayEquals(Hex.decode(CONTRACT), transaction.getContractAddress().toBytes());

        // Process the transaction.
        Pair<ImportResult, AionBlockSummary> results = processTransaction(transaction, 1);

        // Collect the consensus information from the block & receipt.
        AionBlockSummary blockSummary = results.getRight();
        AionTxReceipt receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertTrue(receipt.isSuccessful());

        byte[] stateRoot = blockSummary.getBlock().getStateRoot();
        byte[] blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        byte[] receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        assertArrayEquals(Hex.decode(STATE_ROOT8), stateRoot);
        assertArrayEquals(Hex.decode(BLOCK_RECEIPTS_ROOT8), blockReceiptsRoot);
        assertArrayEquals(Hex.decode(RECEIPT_TRIE8), receiptTrieEncoded);

        // Make the balance transfer transaction.
        Address contract = transaction.getContractAddress();
        transaction = makeCallFallbackFunctionTx(contract, amount);

        // Process the transaction.
        results = processTransaction(transaction, 1);

        // Collect the consensus information from the block & receipt.
        blockSummary = results.getRight();
        receipt = blockSummary.getSummaries().get(0).getReceipt();
        assertTrue(receipt.isSuccessful());

        stateRoot = blockSummary.getBlock().getStateRoot();
        blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // Verify the consensus information.
        assertArrayEquals(Hex.decode(STATE_ROOT9), stateRoot);
        assertArrayEquals(Hex.decode(BLOCK_RECEIPTS_ROOT9), blockReceiptsRoot);
        assertArrayEquals(Hex.decode(RECEIPT_TRIE9), receiptTrieEncoded);

        // Verify the sender's balance is as expected.
        BigInteger expectedBalance = new BigInteger("999999897494333086659628");
        assertEquals(expectedBalance, getBalance(Address.wrap(SENDER_ADDR)));

        // Verify that the contract has the expected balance.
        assertEquals(amount, getBalance(contract));

        // Verify that the miner has the expected balance.
        BigInteger expectedMinerBalance = new BigInteger("1500503619076075576");
        assertEquals(expectedMinerBalance, getBalance(Address.wrap(MINER)));
    }

    private static AionTransaction makeCreateAndTransferToFvmNonpayableConstructorContractTx(
            BigInteger amount) {
        ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        null,
                        amount.toByteArray(),
                        getNonpayableConstructorContractBytes(),
                        5_000_000,
                        ENERGY_PRICE);
        transaction.sign(key);
        return transaction;
    }

    private static AionTransaction makeCreateAndTransferToFvmPayableConstructorContractTx(
            BigInteger amount) {
        ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        null,
                        amount.toByteArray(),
                        getPayableConstructorContractBytes(),
                        5_000_000,
                        ENERGY_PRICE);
        transaction.sign(key);
        return transaction;
    }

    private static AionTransaction makeCreatePayableFallbackContractTx() {
        ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        getPayableFallbackContractBytes(),
                        5_000_000,
                        ENERGY_PRICE);
        transaction.sign(key);
        return transaction;
    }

    private static AionTransaction makeCreateNonpayableFallbackContractTx() {
        ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        getNonpayableFallbackContractBytes(),
                        5_000_000,
                        ENERGY_PRICE);
        transaction.sign(key);
        return transaction;
    }

    private static AionTransaction makeCreatePayableContractTx() {
        ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        getPayableContractBytes(),
                        5_000_000,
                        ENERGY_PRICE);
        transaction.sign(key);
        return transaction;
    }

    private static AionTransaction makeCallNonpayableFunctionTx(
            Address contract, BigInteger amount) {
        ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ONE.toByteArray(),
                        contract,
                        amount.toByteArray(),
                        callNonpayableFunctionEncoding(),
                        2_000_000,
                        ENERGY_PRICE);
        transaction.sign(key);
        return transaction;
    }

    private static AionTransaction makeCallFallbackFunctionTx(Address contract, BigInteger amount) {
        ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ONE.toByteArray(),
                        contract,
                        amount.toByteArray(),
                        new byte[0],
                        2_000_000,
                        ENERGY_PRICE);
        transaction.sign(key);
        return transaction;
    }

    private static AionTransaction makeCallPayableFunctionTx(Address contract, BigInteger amount) {
        ECKey key = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_KEY);
        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ONE.toByteArray(),
                        contract,
                        amount.toByteArray(),
                        callPayableFunctionEncoding(),
                        2_000_000,
                        ENERGY_PRICE);
        transaction.sign(key);
        return transaction;
    }

    private Pair<ImportResult, AionBlockSummary> processTransaction(
            AionTransaction transaction, int numNonRejectedTransactions) {
        AionBlock parentBlock = this.blockchain.getRepository().blockStore.getBestBlock();
        List<AionTransaction> transactions = Collections.singletonList(transaction);
        AionBlock block = this.blockchain.createNewBlock(parentBlock, transactions, false);
        Pair<ImportResult, AionBlockSummary> results =
                this.blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(ImportResult.IMPORTED_BEST, results.getLeft());
        assertEquals(numNonRejectedTransactions, results.getRight().getSummaries().size());
        return results;
    }

    private BigInteger getBalance(Address address) {
        return this.blockchain.getRepository().getBalance(address);
    }

    /** Returns the bytes of NonpayableConstructor.sol */
    private static byte[] getNonpayableConstructorContractBytes() {
        return Hex.decode(
                "60506040523415600f5760006000fd5b5b5b6015565b603a8060226000396000"
                        + "f30060506040526008565b60006000fd00a165627a7a72305820d1e98a5ae7ff9b50d168b62389e56"
                        + "665feb01e6742150667324a5430b67237ba0029");
    }

    /** Returns the bytes of PayableConstructor.sol */
    private static byte[] getPayableConstructorContractBytes() {
        return Hex.decode(
                "60506040525b5b600a565b603a8060176000396000f30060506040526008565"
                        + "b60006000fd00a165627a7a72305820fcd15b67312358cf40ec28495d2606f24a526ba03d5b2feb1"
                        + "c124a61be8543f00029");
    }

    /** Returns the bytes of PayableContract.sol */
    private static byte[] getPayableContractBytes() {
        return Hex.decode(
                "60506040525b5b600a565b6088806100186000396000f300605060405260003"
                        + "56c01000000000000000000000000900463ffffffff1680630f385d6114603b5780634a6a74071460"
                        + "4e576035565b60006000fd5b341560465760006000fd5b604c6056565b005b60546059565b005b5b5"
                        + "65b5b5600a165627a7a72305820c35d0465bf2aede0825558c533938ea55bd711f85d5ee4bbfef990"
                        + "d8554fa6510029");
    }

    /** Returns the bytes of NonpayableFallbackContract.sol */
    private static byte[] getNonpayableFallbackContractBytes() {
        return Hex.decode(
                "60506040523415600f5760006000fd5b6013565b60488060206000396000f30"
                        + "060506040523615600d57600d565b341560185760006000fd5b5b5b0000a165627a7a723058201f2"
                        + "c39dedd348a16f4b9bd99ff935f8bea8a42050b334f0e958b15e2deb2d95e0029");
    }

    /** Returns the bytes of PayableFallbackContract.sol */
    private static byte[] getPayableFallbackContractBytes() {
        return Hex.decode(
                "60506040523415600f5760006000fd5b6013565b603d8060206000396000f30"
                        + "060506040523615600d57600d565b5b5b0000a165627a7a723058205a54983a3fd2c6098a380903d"
                        + "f78688d9f34e4e5e2b86df61a7cf42e1d22f2b60029");
    }

    /** Encoding to call 'notPayableFunction' in PayableContract.sol */
    private static byte[] callNonpayableFunctionEncoding() {
        return Hex.decode("0f385d61");
    }

    /** Encoding to call 'payableFunction' in PayableContract.sol */
    private static byte[] callPayableFunctionEncoding() {
        return Hex.decode("4a6a7407");
    }
}
