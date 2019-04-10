package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.util.Collections;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIEncoder;
import org.aion.crypto.ECKey;
import org.aion.mcf.blockchain.TxResponse;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.tx.TransactionTypes;
import org.aion.types.Address;
import org.aion.vm.VirtualMachineProvider;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.contracts.AvmHelloWorld;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

public class PendingStateTest {

    @Test
    public void testAddPendingTransactionSuccess() {

        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withValidatorConfiguration("simple")
                        .withDefaultAccounts()
                        .build();
        StandaloneBlockchain bc = bundle.bc;

        CfgAion.inst().setGenesis(bc.getGenesis());

        AionHub hub = AionHub.createForTesting(CfgAion.inst(), bc, bc.getRepository());

        Address to = new Address(bundle.privateKeys.get(0).getAddress());
        ECKey signer = bundle.privateKeys.get(1);

        // Successful transaction

        AionTransaction tx =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        to,
                        new byte[0],
                        new byte[0],
                        1_000_000L,
                        10_000_000_000L);

        tx.sign(signer);

        assertEquals(hub.getPendingState().addPendingTransaction(tx), TxResponse.SUCCESS);
    }

    @Test
    public void testAddPendingTransactionInvalidNrgPrice() {

        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withValidatorConfiguration("simple")
                        .withDefaultAccounts()
                        .build();
        StandaloneBlockchain bc = bundle.bc;

        CfgAion.inst().setGenesis(bc.getGenesis());

        AionHub hub = AionHub.createForTesting(CfgAion.inst(), bc, bc.getRepository());

        Address to = new Address(bundle.privateKeys.get(0).getAddress());
        ECKey signer = bundle.privateKeys.get(1);

        // Invalid Nrg Price transaction

        AionTransaction tx =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        to,
                        new byte[0],
                        new byte[0],
                        1_000_000L,
                        1L);
        tx.sign(signer);

        assertEquals(
                hub.getPendingState().addPendingTransaction(tx), TxResponse.INVALID_TX_NRG_PRICE);
    }

    @Test
    public void testAddPendingTransaction_AVMContractDeploy_Success() {
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .withAvmEnabled()
                        .build();
        StandaloneBlockchain blockchain = bundle.bc;
        ECKey deployerKey = bundle.privateKeys.get(0);

        CfgAion.inst().setGenesis(blockchain.getGenesis());
        VirtualMachineProvider.initializeAllVirtualMachines();

        AionHub hub =
                AionHub.createForTesting(CfgAion.inst(), blockchain, blockchain.getRepository());

        // Successful transaction
        byte[] jar =
                new CodeAndArguments(
                                JarBuilder.buildJarForMainAndClassesAndUserlib(AvmHelloWorld.class),
                                new byte[0])
                        .encodeToBytes();

        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        Address.wrap(deployerKey.getAddress()),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        jar,
                        5_000_000,
                        10_000_000_000L,
                        TransactionTypes.AVM_CREATE_CODE);
        transaction.sign(deployerKey);

        assertEquals(hub.getPendingState().addPendingTransaction(transaction), TxResponse.SUCCESS);
        VirtualMachineProvider.shutdownAllVirtualMachines();
    }

    @Test
    public void testAddPendingTransaction_AVMContractCall_Success() {
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .withAvmEnabled()
                        .build();
        StandaloneBlockchain blockchain = bundle.bc;
        ECKey deployerKey = bundle.privateKeys.get(0);

        CfgAion.inst().setGenesis(blockchain.getGenesis());
        VirtualMachineProvider.initializeAllVirtualMachines();

        // Successful transaction
        byte[] jar =
                new CodeAndArguments(
                                JarBuilder.buildJarForMainAndClassesAndUserlib(AvmHelloWorld.class),
                                new byte[0])
                        .encodeToBytes();

        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        Address.wrap(deployerKey.getAddress()),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        jar,
                        5_000_000,
                        10_000_000_000L,
                        TransactionTypes.AVM_CREATE_CODE);
        transaction.sign(deployerKey);

        AionBlock block =
                blockchain.createNewBlock(
                        blockchain.getBestBlock(), Collections.singletonList(transaction), false);
        Pair<ImportResult, AionBlockSummary> connectResult =
                blockchain.tryToConnectAndFetchSummary(block);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);

        // Check the block was imported, the contract has the Avm prefix, and deployment succeeded.
        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        // verify that the output is indeed the contract address
        assertThat(transaction.getContractAddress().toBytes())
                .isEqualTo(receipt.getTransactionOutput());

        AionHub hub =
                AionHub.createForTesting(CfgAion.inst(), blockchain, blockchain.getRepository());

        Address contract = Address.wrap(receipt.getTransactionOutput());

        byte[] call = ABIEncoder.encodeOneString("sayHello");
        transaction =
                new AionTransaction(
                        BigInteger.ONE.toByteArray(),
                        Address.wrap(deployerKey.getAddress()),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        call,
                        2_000_000,
                        10_000_000_000L,
                        TransactionTypes.DEFAULT);

        transaction.sign(deployerKey);

        assertEquals(hub.getPendingState().addPendingTransaction(transaction), TxResponse.SUCCESS);
        VirtualMachineProvider.shutdownAllVirtualMachines();
    }
}
