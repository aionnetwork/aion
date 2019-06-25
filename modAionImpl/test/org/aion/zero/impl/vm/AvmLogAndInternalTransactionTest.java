package org.aion.zero.impl.vm;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import avm.Address;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.tx.TransactionTypes;
import org.aion.mcf.valid.TransactionTypeRule;
import org.aion.types.AionAddress;
import org.aion.vm.LongLivedAvm;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.InternalTransactionInterface;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.contracts.AvmLogTarget;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class AvmLogAndInternalTransactionTest {
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;

    @BeforeClass
    public static void setupAvm() {
        LongLivedAvm.createAndStartLongLivedAvm();
    }

    @AfterClass
    public static void tearDownAvm() {
        LongLivedAvm.destroy();
        TransactionTypeRule.disallowAVMContractTransaction();
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
    }

    @After
    public void tearDown() {
        this.blockchain = null;
        this.deployerKey = null;
    }

    @Test
    public void testLogAndInternalTransactionsOnSuccess() {
        AionAddress contract = deployContract(BigInteger.ZERO);
        AionAddress other = deployContract(BigInteger.ONE);

        Pair<ImportResult, AionBlockSummary> connectResult = callFireLogs(BigInteger.TWO, contract, other, "fireLogsOnSuccess");
        AionBlockSummary summary = connectResult.getRight();

        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        AionTxReceipt receipt = summary.getReceipts().get(0);
        assertThat(receipt.isSuccessful()).isTrue();

        List<IExecutionLog> logs = receipt.getLogInfoList();
        List<InternalTransactionInterface> internalTransactions = summary.getSummaries().get(0).getInternalTransactions();

        assertEquals(3, logs.size());
        assertEquals(1, internalTransactions.size());
    }

    @Test
    public void testLogAndInternalTransactionsOnFailure() {
        AionAddress contract = deployContract(BigInteger.ZERO);
        AionAddress other = deployContract(BigInteger.ONE);

        Pair<ImportResult, AionBlockSummary> connectResult = callFireLogs(BigInteger.TWO, contract, other, "fireLogsOnFailure");
        AionBlockSummary summary = connectResult.getRight();

        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        AionTxReceipt receipt = summary.getReceipts().get(0);
        assertThat(receipt.isSuccessful()).isTrue();

        List<IExecutionLog> logs = receipt.getLogInfoList();
        List<InternalTransactionInterface> internalTransactions = summary.getSummaries().get(0).getInternalTransactions();

        assertEquals(0, logs.size());
        assertEquals(0, internalTransactions.size());
    }

    public Pair<ImportResult, AionBlockSummary> callFireLogs(BigInteger nonce, AionAddress address, AionAddress addressToCall, String methodName) {
        byte[] data =
                new ABIStreamingEncoder()
                        .encodeOneString(methodName)
                        .encodeOneAddress(new Address(addressToCall.toByteArray()))
                        .toBytes();

        AionTransaction transaction =
                newTransaction(
                        nonce,
                        new AionAddress(deployerKey.getAddress()),
                        address,
                        data,
                        2_000_000,
                        TransactionTypes.DEFAULT);
        transaction.sign(this.deployerKey);

        AionBlock block =
                this.blockchain.createBlock(
                        this.blockchain.getBestBlock(),
                        Collections.singletonList(transaction),
                        false,
                        this.blockchain.getBestBlock().getTimestamp());
        return this.blockchain.tryToConnectAndFetchSummary(block);
    }

    public AionAddress deployContract(BigInteger nonce) {
        TransactionTypeRule.allowAVMContractTransaction();
        byte[] jar = getJarBytes();
        AionTransaction transaction =
                newTransaction(
                        nonce,
                        new AionAddress(deployerKey.getAddress()),
                        null,
                        jar,
                        5_000_000,
                        TransactionTypes.AVM_CREATE_CODE);

        transaction.sign(this.deployerKey);

        AionBlock block =
                this.blockchain.createBlock(
                        this.blockchain.getBestBlock(),
                        Collections.singletonList(transaction),
                        false,
                        this.blockchain.getBestBlock().getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult = this.blockchain.tryToConnectAndFetchSummary(block);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);

        // Check the block was imported, the contract has the Avm prefix, and deployment succeeded.
        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(receipt.isSuccessful()).isTrue();
        return new AionAddress(receipt.getTransactionOutput());
    }

    private byte[] getJarBytes() {
        return new CodeAndArguments(JarBuilder.buildJarForMainAndClassesAndUserlib(AvmLogTarget.class), new byte[0]).encodeToBytes();
    }

    private AionTransaction newTransaction(
            BigInteger nonce,
            AionAddress sender,
            AionAddress destination,
            byte[] data,
            long energyLimit,
            byte type) {
        return new AionTransaction(
                nonce.toByteArray(),
                sender,
                destination,
                BigInteger.ZERO.toByteArray(),
                data,
                energyLimit,
                1,
                type);
    }
}
