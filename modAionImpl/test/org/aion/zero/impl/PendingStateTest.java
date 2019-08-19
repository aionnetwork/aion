package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.List;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIEncoder;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.mcf.blockchain.IPendingStateInternal;
import org.aion.mcf.blockchain.TxResponse;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.valid.TransactionTypeRule;
import org.aion.txpool.TxPoolModule;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.time.TimeInstant;
import org.aion.vm.LongLivedAvm;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.contracts.AvmHelloWorld;
import org.aion.mcf.types.AionTxReceipt;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

public class PendingStateTest {
    private StandaloneBlockchain.Bundle bundle;
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;
    private IPendingStateInternal pendingState;

    @BeforeClass
    public static void setup() {
        LongLivedAvm.createAndStartLongLivedAvm();
        TransactionTypeRule.allowAVMContractTransaction();
    }

    @AfterClass
    public static void shutdown() {
        LongLivedAvm.destroy();
    }

    @Before
    public void reinitialize() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        // This clears the TxPool between tests, otherwise the singleton instance persists.
        Field instance = TxPoolModule.class.getDeclaredField("singleton");
        instance.setAccessible(true);
        instance.set(null, null);

        bundle =
            new StandaloneBlockchain.Builder()
                .withDefaultAccounts()
                .withValidatorConfiguration("simple")
                .withAvmEnabled()
                .build();
        blockchain = bundle.bc;
        deployerKey = bundle.privateKeys.get(0);

        CfgAion.inst().setGenesis(blockchain.getGenesis());

        pendingState = AionHub.createForTesting(CfgAion.inst(), blockchain, blockchain.getRepository()).getPendingState();
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
                        TransactionTypes.DEFAULT);

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
                        TransactionTypes.DEFAULT);

        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.INVALID_TX_NRG_PRICE);
    }

    @Test
    public void testAddPendingTransaction_AVMContractDeploy_Success() {

        // Successful transaction
        byte[] jar =
                new CodeAndArguments(
                                JarBuilder.buildJarForMainAndClassesAndUserlib(AvmHelloWorld.class),
                                new byte[0])
                        .encodeToBytes();

        AionTransaction transaction =
                AionTransaction.create(
                        deployerKey,
                        BigInteger.ZERO.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        jar,
                        5_000_000,
                        10_000_000_000L,
                        TransactionTypes.AVM_CREATE_CODE);

        assertEquals(pendingState.addPendingTransaction(transaction), TxResponse.SUCCESS);
    }

    @Test
    public void testAddPendingTransaction_AVMContractCall_Success() {

        // Successful transaction
        byte[] jar =
                new CodeAndArguments(
                                JarBuilder.buildJarForMainAndClassesAndUserlib(AvmHelloWorld.class),
                                new byte[0])
                        .encodeToBytes();

        AionTransaction createTransaction =
                AionTransaction.create(
                        deployerKey,
                        BigInteger.ZERO.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        jar,
                        5_000_000,
                        10_000_000_000L,
                        TransactionTypes.AVM_CREATE_CODE);

        assertEquals(pendingState.addPendingTransaction(createTransaction), TxResponse.SUCCESS);

        AionBlock block =
                blockchain.createNewBlock(
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

        byte[] call = ABIEncoder.encodeOneString("sayHello");
        AionTransaction callTransaction =
                AionTransaction.create(
                        deployerKey,
                        BigInteger.ONE.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        call,
                        2_000_000,
                        10_000_000_000L,
                        TransactionTypes.DEFAULT);

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
                timeStamp);
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
                TransactionTypes.DEFAULT);
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
                    TransactionTypes.DEFAULT);

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
                        TransactionTypes.DEFAULT);

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
                        TransactionTypes.DEFAULT);

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
                        TransactionTypes.DEFAULT);

        AionTransaction tx2 =
                AionTransaction.create(
                        deployerKey,
                        BigInteger.ZERO.toByteArray(),
                        new AionAddress(new byte[32]),
                        ByteUtils.fromHexString("2"),
                        ByteUtils.fromHexString("2"),
                        1000_000L,
                        20_000_000_000L,
                        TransactionTypes.DEFAULT);

        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.SUCCESS);

        AionBlock block =
            blockchain.createNewBlock(
                blockchain.getBestBlock(), pendingState.getPendingTransactions(), false);
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);

        assertEquals(connectResult.getLeft(), ImportResult.IMPORTED_BEST);
        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.ALREADY_SEALED);
        assertEquals(pendingState.addPendingTransaction(tx2), TxResponse.ALREADY_SEALED);
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
                        TransactionTypes.DEFAULT);

        assertEquals(pendingState.addPendingTransaction(tx), TxResponse.SUCCESS);

        AionBlock block =
            blockchain.createNewBlock(
                blockchain.getBestBlock(), pendingState.getPendingTransactions(), false);
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(connectResult.getLeft(), ImportResult.IMPORTED_BEST);
    }

    @Test
    public void testAionPendingStateInit() {
        CfgAion.inst().getConsensus().setSeed(true);

        // NullPointerException should not happens
        AionHub.createForTesting(CfgAion.inst(), blockchain, blockchain.getRepository());

        CfgAion.inst().getConsensus().setSeed(false);
    }
}
