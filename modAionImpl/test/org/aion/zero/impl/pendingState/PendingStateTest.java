package org.aion.zero.impl.pendingState;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.avm.stub.IAvmResourceFactory;
import org.aion.avm.stub.IContractFactory.AvmContract;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.zero.impl.blockchain.AionHub;
import org.aion.zero.impl.blockchain.AionImpl.PendingTxCallback;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
import org.aion.zero.impl.blockchain.StandaloneBlockchain.Bundle;
import org.aion.zero.impl.types.TxResponse;
import org.aion.zero.impl.core.ImportResult;
import org.aion.base.TransactionTypeRule;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.time.TimeInstant;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.AvmPathManager;
import org.aion.zero.impl.vm.AvmTestConfig;
import org.aion.zero.impl.vm.TestResourceProvider;
import org.aion.base.AionTxReceipt;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

public class PendingStateTest {
    private Bundle bundle;
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;
    private AionPendingStateImpl pendingState;

    @BeforeClass
    public static void setup() {
        TransactionTypeRule.allowAVMContractTransaction();
        AvmTestConfig.supportOnlyAvmVersion1();
    }

    @AfterClass
    public static void tearDown() {
        AvmTestConfig.clearConfigurations();
    }

    @Before
    public void reinitialize() throws SecurityException, IllegalArgumentException {

        bundle =
            new StandaloneBlockchain.Builder()
                .withDefaultAccounts()
                .withValidatorConfiguration("simple")
                .withAvmEnabled()
                .build();
        blockchain = bundle.bc;
        deployerKey = bundle.privateKeys.get(0);

        CfgAion.inst().setGenesis(blockchain.getGenesis());

        pendingState = AionHub.createForTesting(CfgAion.inst(), blockchain, blockchain.getRepository(),
            new PendingTxCallback(new ArrayList<>())).getPendingState();
    }

    @Test
    public void testAddPendingTransactionSuccess() {

        // Successful transaction
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        BigInteger.ZERO.toByteArray(),
                        new AionAddress(new byte[32]),
                        BigInteger.ZERO.toByteArray(),
                        new byte[0],
                        1_000_000L,
                        10_000_000_000L,
                        TransactionTypes.DEFAULT, null);

        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.SUCCESS);
    }

    @Test
    public void testAddPendingTransactionInvalidNrgPrice() {

        // Invalid Nrg Price transaction
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        BigInteger.ZERO.toByteArray(),
                        new AionAddress(new byte[32]),
                        BigInteger.ZERO.toByteArray(),
                        new byte[0],
                        1_000_000L,
                        1L,
                        TransactionTypes.DEFAULT, null);

        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.INVALID_TX_NRG_PRICE);
    }

    @Test
    public void testAddPendingTransaction_AVMContractDeploy_Success() throws Exception {
        TestResourceProvider resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());
        IAvmResourceFactory resourceFactory = resourceProvider.factoryForVersion1;

        // Successful transaction
        byte[] jar = resourceFactory.newContractFactory().getDeploymentBytes(AvmContract.HELLO_WORLD);

        AionTransaction transaction =
                AionTransaction.create(
                        deployerKey,
                        BigInteger.ZERO.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        jar,
                        5_000_000,
                        10_000_000_000L,
                        TransactionTypes.AVM_CREATE_CODE, null);

        assertEquals(pendingState.addPendingTransaction(transaction), TxResponse.SUCCESS);
    }

    @Test
    public void testAddPendingTransaction_AVMContractCall_Success() throws Exception {
        TestResourceProvider resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());
        IAvmResourceFactory resourceFactory = resourceProvider.factoryForVersion1;

        // Successful transaction
        byte[] jar = resourceFactory.newContractFactory().getDeploymentBytes(AvmContract.HELLO_WORLD);

        AionTransaction createTransaction =
                AionTransaction.create(
                        deployerKey,
                        BigInteger.ZERO.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        jar,
                        5_000_000,
                        10_000_000_000L,
                        TransactionTypes.AVM_CREATE_CODE, null);

        assertEquals(pendingState.addPendingTransaction(createTransaction), TxResponse.SUCCESS);

        AionBlock block =
                blockchain.createNewMiningBlock(
                        blockchain.getBestBlock(), pendingState.getPendingTransactions(), false);
        Pair<ImportResult, AionBlockSummary> connectResult =
                blockchain.tryToConnectAndFetchSummary(block);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);

        // Check the block was imported, the contract has the Avm prefix, and deployment succeeded.
        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);

        // verify that the output is indeed the contract address
        AionAddress contractAddress = TxUtil.calculateContractAddress(createTransaction);
        assertThat(contractAddress.toByteArray()).isEqualTo(receipt.getTransactionOutput());

        AionAddress contract = new AionAddress(receipt.getTransactionOutput());

        byte[] call = resourceFactory.newStreamingEncoder().encodeOneString("sayHello").getEncoding();
        AionTransaction callTransaction =
                AionTransaction.create(
                        deployerKey,
                        BigInteger.ONE.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        call,
                        2_000_000,
                        10_000_000_000L,
                        TransactionTypes.DEFAULT, null);

        assertEquals(pendingState.addPendingTransaction(callTransaction), TxResponse.SUCCESS);
    }

    private AionTransaction genTransactionWithTimestamp(byte[] nonce, ECKey key, byte[] timeStamp) {
        return AionTransaction.createGivenTimestamp(
                key,
                nonce,
                new AionAddress(new byte[32]),
                ByteUtils.fromHexString("1"),
                ByteUtils.fromHexString("1"),
                1000_000L,
                10_000_000_000L,
                TransactionTypes.DEFAULT,
                timeStamp, null);
    }

    private AionTransaction genTransaction(byte[] nonce) {
        return AionTransaction.create(
                deployerKey,
                nonce,
                new AionAddress(new byte[32]),
                ByteUtils.fromHexString("1"),
                ByteUtils.fromHexString("1"),
                1000_000L,
                10_000_000_000L,
                TransactionTypes.DEFAULT, null);
    }

    @Test
    public void snapshotWithSameTransactionTimestamp() {
        ECKey deployerKey2 = bundle.privateKeys.get(1);

        byte[] timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());
        final int cnt = 16;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            AionTransaction tx = genTransactionWithTimestamp(nonce, deployerKey, timeStamp);

            pendingState.addPendingTransaction(tx);
        }

        timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            AionTransaction tx = genTransactionWithTimestamp(nonce, deployerKey2, timeStamp);

            pendingState.addPendingTransaction(tx);
        }

        timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());
        for (int i = cnt; i < 2 * cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            AionTransaction tx = genTransactionWithTimestamp(nonce, deployerKey, timeStamp);

            pendingState.addPendingTransaction(tx);
        }

        timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());
        for (int i = cnt; i < 2 * cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;
            AionTransaction tx = genTransactionWithTimestamp(nonce, deployerKey2, timeStamp);

            pendingState.addPendingTransaction(tx);
        }

        assertEquals(pendingState.getPendingTxSize(), cnt * 4);
    }

    @Test
    public void addRepeatedTxn() {
        AionTransaction tx =
                AionTransaction.create(
                    deployerKey,
                    BigInteger.ZERO.toByteArray(),
                    new AionAddress(new byte[32]),
                    ByteUtils.fromHexString("1"),
                    ByteUtils.fromHexString("1"),
                    1000_000L,
                    10_000_000_000L,
                    TransactionTypes.DEFAULT, null);

        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.SUCCESS);
        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.REPAYTX_LOWPRICE);

        assertEquals(1, pendingState.getPendingTxSize());
    }

    @Test
    public void addRepeatedTxn2() {
        final int cnt = 10;
        for (int i = 0; i < cnt; i++) {
            byte[] nonce = new byte[Long.BYTES];
            nonce[Long.BYTES - 1] = (byte) i;

            assertEquals(pendingState.addPendingTransaction(genTransaction(nonce)), TxResponse.SUCCESS);
        }

        assertEquals(pendingState.getPendingTxSize(), cnt);

        AionTransaction tx = genTransaction(BigInteger.TWO.toByteArray());
        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.REPAYTX_LOWPRICE);

        assertEquals(pendingState.getPendingTxSize(), cnt);
        assertNotEquals(pendingState.getPendingTransactions().get(2), tx);
    }

    @Test
    public void addTxWithSameNonce() {

        AionTransaction tx = genTransaction(BigInteger.ZERO.toByteArray());
        AionTransaction tx2 = genTransaction(BigInteger.ZERO.toByteArray());

        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.SUCCESS);
        assertEquals(pendingState.addPendingTransaction(tx2), TxResponse.REPAYTX_LOWPRICE);

        assertEquals(1, pendingState.getPendingTxSize());

        List<AionTransaction> pendingTransactions = pendingState.getPendingTransactions();
        assertEquals(1, pendingTransactions.size());
        assertEquals(pendingTransactions.get(0), tx);
    }

    @Test
    public void addLargeNonce() {
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        BigInteger.TEN.toByteArray(),
                        new AionAddress(new byte[32]),
                        ByteUtils.fromHexString("1"),
                        ByteUtils.fromHexString("1"),
                        1000_000L,
                        10_000_000_000L,
                        TransactionTypes.DEFAULT, null);

        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.CACHED_NONCE);
        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.ALREADY_CACHED);
    }

    @Test
    public void invalidEnergyLimit() {
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        BigInteger.ZERO.toByteArray(),
                        new AionAddress(new byte[32]),
                        ByteUtils.fromHexString("1"),
                        ByteUtils.fromHexString("1"),
                        10L,
                        10_000_000_000L,
                        TransactionTypes.DEFAULT, null);

        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.INVALID_TX);
    }

    @Test
    public void alreadySealed() {
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        BigInteger.ZERO.toByteArray(),
                        new AionAddress(new byte[32]),
                        ByteUtils.fromHexString("1"),
                        ByteUtils.fromHexString("1"),
                        1000_000L,
                        10_000_000_000L,
                        TransactionTypes.DEFAULT, null);

        AionTransaction tx2 =
                AionTransaction.create(
                        deployerKey,
                        BigInteger.ZERO.toByteArray(),
                        new AionAddress(new byte[32]),
                        ByteUtils.fromHexString("2"),
                        ByteUtils.fromHexString("2"),
                        1000_000L,
                        20_000_000_000L,
                        TransactionTypes.DEFAULT, null);

        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.SUCCESS);

        AionBlock block =
            blockchain.createNewMiningBlock(
                blockchain.getBestBlock(), pendingState.getPendingTransactions(), false);
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);

        assertEquals(connectResult.getLeft(), ImportResult.IMPORTED_BEST);
        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.ALREADY_SEALED);
        assertEquals(pendingState.addPendingTransaction(tx2), TxResponse.ALREADY_SEALED);
    }

    @Test
    public void replayTransactionWithLessThanDoubleEnergyPrice() {
        AionTransaction tx1 =
            AionTransaction.create(
                deployerKey,
                BigInteger.ZERO.toByteArray(),
                new AionAddress(new byte[32]),
                ByteUtils.fromHexString("1"),
                ByteUtils.fromHexString("1"),
                1000_000L,
                10_000_000_000L,
                TransactionTypes.DEFAULT, null);

        AionTransaction tx2 =
            AionTransaction.create(
                deployerKey,
                BigInteger.ZERO.toByteArray(),
                new AionAddress(new byte[32]),
                ByteUtils.fromHexString("1"),
                ByteUtils.fromHexString("1"),
                1000_000L,
                19_999_999_999L,
                TransactionTypes.DEFAULT, null);

        assertEquals(TxResponse.SUCCESS, pendingState.addPendingTransaction(tx1));
        assertEquals(TxResponse.REPAYTX_LOWPRICE, pendingState.addPendingTransaction(tx2));
        assertEquals(1, pendingState.getPendingTxSize());
    }

    @Test
    public void replayTransactionWithDoubleEnergyPrice() {
        AionTransaction tx1 =
            AionTransaction.create(
                deployerKey,
                BigInteger.ZERO.toByteArray(),
                new AionAddress(new byte[32]),
                ByteUtils.fromHexString("1"),
                ByteUtils.fromHexString("1"),
                1000_000L,
                10_000_000_000L,
                TransactionTypes.DEFAULT, null);

        AionTransaction tx2 =
            AionTransaction.create(
                deployerKey,
                BigInteger.ZERO.toByteArray(),
                new AionAddress(new byte[32]),
                ByteUtils.fromHexString("1"),
                ByteUtils.fromHexString("1"),
                1000_000L,
                20_000_000_000L,
                TransactionTypes.DEFAULT, null);

        assertEquals(TxResponse.SUCCESS, pendingState.addPendingTransaction(tx1));
        assertEquals(TxResponse.REPAID, pendingState.addPendingTransaction(tx2));
        assertEquals(1, pendingState.getPendingTxSize());
        // tx2 will get cached and will replace tx1 if tx1 is not included in the next block.
        assertEquals(pendingState.getPendingTransactions().get(0), tx1);

        AionBlock block =
            blockchain.createNewMiningBlock(
                blockchain.getBestBlock(), Collections.emptyList(), false);
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(connectResult.getLeft(), ImportResult.IMPORTED_BEST);

        (pendingState).applyBlockUpdate(block, connectResult.getRight().getReceipts());
        assertEquals(pendingState.getPendingTransactions().get(0), tx2);
    }

    @Test
    public void replayTransactionWithDoubleEnergyPriceAfterSealing() {
        AionTransaction tx1 =
            AionTransaction.create(
                deployerKey,
                BigInteger.ZERO.toByteArray(),
                new AionAddress(new byte[32]),
                ByteUtils.fromHexString("1"),
                ByteUtils.fromHexString("1"),
                1000_000L,
                10_000_000_000L,
                TransactionTypes.DEFAULT, null);

        AionTransaction tx2 =
            AionTransaction.create(
                deployerKey,
                BigInteger.ZERO.toByteArray(),
                new AionAddress(new byte[32]),
                ByteUtils.fromHexString("1"),
                ByteUtils.fromHexString("1"),
                1000_000L,
                20_000_000_000L,
                TransactionTypes.DEFAULT, null);

        assertEquals(TxResponse.SUCCESS, pendingState.addPendingTransaction(tx1));
        assertEquals(TxResponse.REPAID, pendingState.addPendingTransaction(tx2));
        assertEquals(1, pendingState.getPendingTxSize());
        // tx2 will get cached and will replace tx1 if tx1 is not included in the next block.
        assertEquals(pendingState.getPendingTransactions().get(0), tx1);

        AionBlock block =
            blockchain.createNewMiningBlock(
                blockchain.getBestBlock(), pendingState.getPendingTransactions(), false);
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(connectResult.getLeft(), ImportResult.IMPORTED_BEST);

        pendingState.applyBlockUpdate(block, connectResult.getRight().getReceipts());
        assertEquals(0, pendingState.getPendingTxSize());
    }

    @Test
    public void replayTransactionThatUsesEntireBalance() {
        BigInteger balance = blockchain.getRepository().getBalance(new AionAddress(deployerKey.getAddress()));
        BigInteger value = balance.subtract(BigInteger.valueOf(21000*3).multiply(BigInteger.valueOf(10_000_000_000L)));

        AionTransaction tx1 =
            AionTransaction.create(
                deployerKey,
                BigInteger.ZERO.toByteArray(),
                new AionAddress(new byte[32]),
                value.toByteArray(),
                new byte[0],
                21000,
                10_000_000_000L,
                TransactionTypes.DEFAULT, null);

        AionTransaction tx2 =
            AionTransaction.create(
                deployerKey,
                BigInteger.ONE.toByteArray(),
                new AionAddress(new byte[32]),
                BigInteger.ZERO.toByteArray(),
                new byte[0],
                21000,
                10_000_000_000L,
                TransactionTypes.DEFAULT, null);

        /* tx1 and tx3 should use the entire balance. If tx2 is removed properly, tx3 should be
         * able to replace it in the pending state */
        AionTransaction tx3 =
            AionTransaction.create(
                deployerKey,
                BigInteger.ONE.toByteArray(),
                new AionAddress(new byte[32]),
                BigInteger.ZERO.toByteArray(),
                new byte[0],
                21000,
                20_000_000_000L,
                TransactionTypes.DEFAULT, null);

        // This would execute fine on top of tx2, but should have insufficient balance on top of tx3
        AionTransaction tx4 =
            AionTransaction.create(
                deployerKey,
                BigInteger.TWO.toByteArray(),
                new AionAddress(new byte[32]),
                BigInteger.ZERO.toByteArray(),
                new byte[0],
                21000,
                10_000_000_000L,
                TransactionTypes.DEFAULT, null);

        assertEquals(TxResponse.SUCCESS, pendingState.addPendingTransaction(tx1));
        assertEquals(TxResponse.SUCCESS, pendingState.addPendingTransaction(tx2));
        assertEquals(TxResponse.REPAID, pendingState.addPendingTransaction(tx3));
        assertEquals(TxResponse.SUCCESS, pendingState.addPendingTransaction(tx4));
        assertEquals(3, pendingState.getPendingTxSize());

        AionBlock block =
            blockchain.createNewMiningBlock(
                blockchain.getBestBlock(), Collections.emptyList(), false);
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(connectResult.getLeft(), ImportResult.IMPORTED_BEST);

        pendingState.applyBlockUpdate(block, connectResult.getRight().getReceipts());
        // tx3 should replace tx2, and tx4 will now have insufficient funds so it will get dropped
        assertEquals(2, pendingState.getPendingTxSize());
        assertEquals(pendingState.getPendingTransactions().get(1), tx3);
    }

    @Test
    public void replayTransactionThatThatInvalidatesMiddleTx() {
        BigInteger balance = blockchain.getRepository().getBalance(new AionAddress(deployerKey.getAddress()));
        BigInteger value = balance.subtract(BigInteger.valueOf(21000*7).multiply(BigInteger.valueOf(10_000_000_000L)));

        AionTransaction tx1 =
            AionTransaction.create(
                deployerKey,
                BigInteger.ZERO.toByteArray(),
                new AionAddress(new byte[32]),
                value.toByteArray(),
                new byte[0],
                21000,
                10_000_000_000L,
                TransactionTypes.DEFAULT, null);

        AionTransaction tx2 =
            AionTransaction.create(
                deployerKey,
                BigInteger.ONE.toByteArray(),
                new AionAddress(new byte[32]),
                BigInteger.ZERO.toByteArray(),
                new byte[0],
                21000,
                10_000_000_000L,
                TransactionTypes.DEFAULT, null);

        /* tx1 and tx3 should use the entire balance. If tx2 is removed properly, tx3 should be
         * able to replace it in the pending state */
        AionTransaction tx3 =
            AionTransaction.create(
                deployerKey,
                BigInteger.ONE.toByteArray(),
                new AionAddress(new byte[32]),
                BigInteger.ZERO.toByteArray(),
                new byte[0],
                21000,
                40_000_000_000L,
                TransactionTypes.DEFAULT, null);

        // This would execute fine on top of tx2, but should have insufficient balance on top of tx3
        AionTransaction tx4 =
            AionTransaction.create(
                deployerKey,
                BigInteger.TWO.toByteArray(),
                new AionAddress(new byte[32]),
                BigInteger.ZERO.toByteArray(),
                new byte[0],
                21000,
                40_000_000_000L,
                TransactionTypes.DEFAULT, null);

        AionTransaction tx5 =
            AionTransaction.create(
                deployerKey,
                BigInteger.valueOf(3).toByteArray(),
                new AionAddress(new byte[32]),
                BigInteger.ZERO.toByteArray(),
                new byte[0],
                21000,
                10_000_000_000L,
                TransactionTypes.DEFAULT, null);

        assertEquals(TxResponse.SUCCESS, pendingState.addPendingTransaction(tx1));
        assertEquals(TxResponse.SUCCESS, pendingState.addPendingTransaction(tx2));
        assertEquals(TxResponse.REPAID, pendingState.addPendingTransaction(tx3));
        assertEquals(TxResponse.SUCCESS, pendingState.addPendingTransaction(tx4));
        assertEquals(TxResponse.SUCCESS, pendingState.addPendingTransaction(tx5));
        assertEquals(4, pendingState.getPendingTxSize());

        AionBlock block =
            blockchain.createNewMiningBlock(
                blockchain.getBestBlock(), Collections.emptyList(), false);
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(connectResult.getLeft(), ImportResult.IMPORTED_BEST);

        pendingState.applyBlockUpdate(block, connectResult.getRight().getReceipts());
        // tx3 should replace tx2, and tx4 will now have insufficient funds so it will get dropped
        assertEquals(2, pendingState.getPendingTxSize());
        assertEquals(pendingState.getPendingTransactions().get(1), tx3);
    }

    @Test
    public void replayInvalidTransactionInMiddle() {
        BigInteger balance = blockchain.getRepository().getBalance(new AionAddress(deployerKey.getAddress()));
        BigInteger value = balance.subtract(BigInteger.valueOf(21000*3).multiply(BigInteger.valueOf(10_000_000_000L)));

        AionTransaction tx1 =
            AionTransaction.create(
                deployerKey,
                BigInteger.ZERO.toByteArray(),
                new AionAddress(new byte[32]),
                value.toByteArray(),
                new byte[0],
                21000,
                10_000_000_000L,
                TransactionTypes.DEFAULT, null);

        AionTransaction tx2 =
            AionTransaction.create(
                deployerKey,
                BigInteger.ONE.toByteArray(),
                new AionAddress(new byte[32]),
                BigInteger.ZERO.toByteArray(),
                new byte[0],
                21000,
                10_000_000_000L,
                TransactionTypes.DEFAULT, null);

        AionTransaction tx3 =
            AionTransaction.create(
                deployerKey,
                BigInteger.ONE.toByteArray(),
                new AionAddress(new byte[32]),
                BigInteger.ZERO.toByteArray(),
                new byte[0],
                21000,
                40_000_000_000L,
                TransactionTypes.DEFAULT, null);

        // This tx will get dropped after tx3 is rejected
        AionTransaction tx4 =
            AionTransaction.create(
                deployerKey,
                BigInteger.TWO.toByteArray(),
                new AionAddress(new byte[32]),
                BigInteger.ZERO.toByteArray(),
                new byte[0],
                21000,
                10_000_000_000L,
                TransactionTypes.DEFAULT, null);

        assertEquals(TxResponse.SUCCESS, pendingState.addPendingTransaction(tx1));
        assertEquals(TxResponse.SUCCESS, pendingState.addPendingTransaction(tx2));
        assertEquals(TxResponse.REPAID, pendingState.addPendingTransaction(tx3));
        assertEquals(TxResponse.SUCCESS, pendingState.addPendingTransaction(tx4));
        assertEquals(3, pendingState.getPendingTxSize());

        AionBlock block =
            blockchain.createNewMiningBlock(
                blockchain.getBestBlock(), Collections.emptyList(), false);
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(connectResult.getLeft(), ImportResult.IMPORTED_BEST);

        pendingState.applyBlockUpdate(block, connectResult.getRight().getReceipts());
        assertEquals(1, pendingState.getPendingTxSize());
    }

    @Test
    public void energyLimitMinimum() {
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        BigInteger.ZERO.toByteArray(),
                        new AionAddress(new byte[32]),
                        BigInteger.ZERO.toByteArray(),
                        ByteUtils.fromHexString("1"),
                        21_000L,
                        10_000_000_000L,
                        TransactionTypes.DEFAULT, null);

        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.DROPPED);

        AionBlock block =
            blockchain.createNewMiningBlock(
                blockchain.getBestBlock(), pendingState.getPendingTransactions(), false);
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(connectResult.getLeft(), ImportResult.IMPORTED_BEST);
    }

    @Test
    public void testAionPendingStateInit() {
        CfgAion.inst().getConsensus().setSeed(true);

        // NullPointerException should not happens
        AionHub.createForTesting(CfgAion.inst(), blockchain, blockchain.getRepository(),
            new PendingTxCallback(new ArrayList<>()));

        CfgAion.inst().getConsensus().setSeed(false);
    }
}
