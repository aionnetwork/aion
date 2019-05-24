package org.aion.zero.impl.vm;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIEncoder;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.valid.TransactionTypeRule;
import org.aion.types.Address;
import org.aion.vm.LongLivedAvm;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.mcf.tx.TransactionTypes;
import org.aion.zero.impl.vm.contracts.AvmHelloWorld;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class AvmHelloWorldTest {
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
    public void testDeployContract() {
        TransactionTypeRule.allowAVMContractTransaction();
        byte[] jar = getJarBytes();
        AionTransaction transaction =
                newTransaction(
                        BigInteger.ZERO,
                        Address.wrap(deployerKey.getAddress()),
                        null,
                        jar,
                        5_000_000,
                        TransactionTypes.AVM_CREATE_CODE);

        transaction.sign(this.deployerKey);

        AionBlock block =
                this.blockchain.createNewBlock(
                        this.blockchain.getBestBlock(),
                        Collections.singletonList(transaction),
                        false);
        Pair<ImportResult, AionBlockSummary> connectResult =
                this.blockchain.tryToConnectAndFetchSummary(block);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);

        // Check the block was imported, the contract has the Avm prefix, and deployment succeeded.
        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(receipt.getTransactionOutput()[0]).isEqualTo(AddressSpecs.A0_IDENTIFIER);
        assertThat(receipt.isSuccessful()).isTrue();

        // verify that the output is indeed the contract address
        assertThat(transaction.getContractAddress().toBytes())
                .isEqualTo(receipt.getTransactionOutput());
    }

    @Test
    public void testDeployAndCallContract() {
        TransactionTypeRule.allowAVMContractTransaction();
        // Deploy the contract.
        byte[] jar = getJarBytes();
        AionTransaction transaction =
                newTransaction(
                        BigInteger.ZERO,
                        Address.wrap(deployerKey.getAddress()),
                        null,
                        jar,
                        5_000_000,
                        TransactionTypes.AVM_CREATE_CODE);
        transaction.sign(this.deployerKey);

        AionBlock block =
                this.blockchain.createNewBlock(
                        this.blockchain.getBestBlock(),
                        Collections.singletonList(transaction),
                        false);
        Pair<ImportResult, AionBlockSummary> connectResult =
                this.blockchain.tryToConnectAndFetchSummary(block);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);

        // Check the block was imported, the contract has the Avm prefix, and deployment succeeded.
        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(receipt.getTransactionOutput()[0]).isEqualTo(AddressSpecs.A0_IDENTIFIER);
        assertThat(receipt.isSuccessful()).isTrue();

        Address contract = Address.wrap(receipt.getTransactionOutput());
        // verify that the output is indeed the contract address
        assertThat(transaction.getContractAddress()).isEqualTo(contract);
        byte[] call = getCallArguments();
        transaction =
                newTransaction(
                        BigInteger.ONE,
                        Address.wrap(deployerKey.getAddress()),
                        contract,
                        call,
                        2_000_000,
                        TransactionTypes.DEFAULT);
        transaction.sign(this.deployerKey);

        block =
                this.blockchain.createNewBlock(
                        this.blockchain.getBestBlock(),
                        Collections.singletonList(transaction),
                        false);
        connectResult = this.blockchain.tryToConnectAndFetchSummary(block);
        receipt = connectResult.getRight().getReceipts().get(0);

        // Check the block was imported and the transaction was successful.
        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(receipt.isSuccessful()).isTrue();
    }

    @Test
    public void testDeployAndCallContractInTheSameBlock() {
        TransactionTypeRule.allowAVMContractTransaction();
        // Deploy the contract.
        byte[] jar = getJarBytes();
        AionTransaction transaction =
            newTransaction(
                BigInteger.ZERO,
                Address.wrap(deployerKey.getAddress()),
                null,
                jar,
                5_000_000,
                TransactionTypes.AVM_CREATE_CODE);
        transaction.sign(this.deployerKey);

        List<AionTransaction> ls = new ArrayList<>();
        ls.add(transaction);

        byte[] call = getCallArguments();
        AionTransaction transaction2 =
            newTransaction(
                BigInteger.ONE,
                Address.wrap(deployerKey.getAddress()),
                transaction.getContractAddress(),
                call,
                2_000_000,
                TransactionTypes.DEFAULT);
        transaction2.sign(this.deployerKey);

        ls.add(transaction2);


        AionBlock block =
            this.blockchain.createNewBlock(
                this.blockchain.getBestBlock(),
                ls,
                false);
        Pair<ImportResult, AionBlockSummary> connectResult =
            this.blockchain.tryToConnectAndFetchSummary(block);

        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(connectResult.getRight().getReceipts().size() == 2);

        // Check the block was imported, the contract has the Avm prefix, and deployment succeeded.
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);
        assertThat(receipt.getTransactionOutput()[0]).isEqualTo(AddressSpecs.A0_IDENTIFIER);
        assertThat(receipt.isSuccessful()).isTrue();

        // Check the call success
        receipt = connectResult.getRight().getReceipts().get(1);
        assertThat(receipt.isSuccessful()).isTrue();
    }

    private byte[] getCallArguments() {
        return ABIEncoder.encodeOneString("sayHello");
    }

    private byte[] getJarBytes() {
        return new CodeAndArguments(
                        JarBuilder.buildJarForMainAndClassesAndUserlib(AvmHelloWorld.class),
                        new byte[0])
                .encodeToBytes();
    }

    private AionTransaction newTransaction(
            BigInteger nonce,
            Address sender,
            Address destination,
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
