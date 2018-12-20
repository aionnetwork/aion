package org.aion.zero.impl.avm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.Collections;
import org.aion.avm.api.ABIEncoder;
import org.aion.avm.core.NodeEnvironment;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.base.type.AionAddress;
import org.aion.base.vm.VirtualMachineSpecs;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.vm.VirtualMachineFactory;
import org.aion.vm.api.interfaces.Address;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.avm.contracts.AvmHelloWorld;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
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
        VirtualMachineFactory.getFactorySingleton().initializeAllVirtualMachines();
    }

    @AfterClass
    public static void tearDownAvm() {
        VirtualMachineFactory.getFactorySingleton().shutdownAllVirtualMachines();
    }

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
            .withDefaultAccounts()
            .withValidatorConfiguration("simple")
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
        byte[] jar = getJarBytes();
        AionTransaction transaction = newTransaction(
            BigInteger.ZERO,
            AionAddress.wrap(deployerKey.getAddress()),
            null,
            BigInteger.ZERO,
            jar,
            5_000_000,
            1,
            VirtualMachineSpecs.AVM_VM_CODE);
        transaction.sign(this.deployerKey);

        AionBlock block = this.blockchain.createNewBlock(this.blockchain.getBestBlock(), Collections.singletonList(transaction), false);
        Pair<ImportResult, AionBlockSummary> connectResult = this.blockchain.tryToConnectAndFetchSummary(block);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);

        // Check the block was imported, the contract has the Avm prefix, and deployment succeeded.
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        assertEquals(NodeEnvironment.CONTRACT_PREFIX, receipt.getTransactionOutput()[0]);
        assertTrue(receipt.isSuccessful());
    }

    @Test
    public void testDeployAndCallContract() {
        // Deploy the contract.
        byte[] jar = getJarBytes();
        AionTransaction transaction = newTransaction(
            BigInteger.ZERO,
            AionAddress.wrap(deployerKey.getAddress()),
            null,
            BigInteger.ZERO,
            jar,
            5_000_000,
            1,
            VirtualMachineSpecs.AVM_VM_CODE);
        transaction.sign(this.deployerKey);

        AionBlock block = this.blockchain.createNewBlock(this.blockchain.getBestBlock(), Collections.singletonList(transaction), false);
        Pair<ImportResult, AionBlockSummary> connectResult = this.blockchain.tryToConnectAndFetchSummary(block);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);

        // Check the block was imported, the contract has the Avm prefix, and deployment succeeded.
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        assertEquals(NodeEnvironment.CONTRACT_PREFIX, receipt.getTransactionOutput()[0]);
        assertTrue(receipt.isSuccessful());

        Address contract = AionAddress.wrap(receipt.getTransactionOutput());
        byte[] call = getCallArguments();
        transaction = newTransaction(
            BigInteger.ONE,
            AionAddress.wrap(deployerKey.getAddress()),
            contract,
            BigInteger.ZERO,
            call,
            2_000_000,
            1,
            VirtualMachineSpecs.AVM_VM_CODE);
        transaction.sign(this.deployerKey);

        block = this.blockchain.createNewBlock(this.blockchain.getBestBlock(), Collections.singletonList(transaction), false);
        connectResult = this.blockchain.tryToConnectAndFetchSummary(block);
        receipt = connectResult.getRight().getReceipts().get(0);

        // Check the block was imported and the transaction was successful.
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        assertTrue(receipt.isSuccessful());
    }

    private byte[] getCallArguments() {
        return ABIEncoder.encodeMethodArguments("sayHello");
    }

    private byte[] getJarBytes() {
        return new CodeAndArguments(JarBuilder.buildJarForMainAndClasses(AvmHelloWorld.class), new byte[0]).encodeToBytes();
    }

    private AionTransaction newTransaction(BigInteger nonce, Address sender, Address destination, BigInteger value, byte[] data, long energyLimit, long energyPrice, byte vm) {
        return new AionTransaction(nonce.toByteArray(), sender, destination, value.toByteArray(), data, energyLimit, energyPrice, vm);
    }

}
