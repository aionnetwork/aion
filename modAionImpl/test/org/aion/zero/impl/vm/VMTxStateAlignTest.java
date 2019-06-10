package org.aion.zero.impl.vm;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.valid.TransactionTypeRule;
import org.aion.precompiled.ContractFactory;
import org.aion.vm.api.types.Address;
import org.aion.util.conversions.Hex;
import org.aion.vm.LongLivedAvm;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.contracts.ContractUtils;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class VMTxStateAlignTest {
    private StandaloneBlockchain blockchainWoAVM;

    private List<ECKey> deployerKeys;
    private int deployerNum = 5;
    private List<Address> deployers;
    private long energyPrice = 1;

    @BeforeClass
    public static void setupVM() {
        LongLivedAvm.createAndStartLongLivedAvm();
        TransactionTypeRule.disallowAVMContractTransaction();
    }

    @AfterClass
    public static void tearDownVM() {
        LongLivedAvm.destroy();
    }

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .build();
        this.blockchainWoAVM = bundle.bc;
        deployerKeys = bundle.privateKeys;

        deployers = new ArrayList<>();
        for (int i = 0; i < deployerNum && i < deployerKeys.size(); i++) {
            Address deployerAddr = Address.wrap(this.deployerKeys.get(i).getAddress());
            deployers.add(deployerAddr);
        }
    }

    @After
    public void tearDown() {
        this.blockchainWoAVM = null;
        this.deployerKeys = null;
        this.deployers = null;
    }

    @Test
    public void testDeployAndCallContractStatesAlign() throws IOException {
        List<AionTransaction> txList = new ArrayList<>();
        List<Address> deployAddr = new ArrayList<>();

        for (int i = 0; i < this.deployerKeys.size() && i < deployerNum; i++) {
            ECKey senderKey = this.deployerKeys.get(i);
            txList.add(makeFvmContractCreateTransaction(senderKey, BigInteger.ZERO));
            deployAddr.add(txList.get(i).getContractAddress());
        }

        AionBlock deplayBlock = genNewBlock(txList, blockchainWoAVM);
        tryImportNewBlock(blockchainWoAVM, deplayBlock);

        txList.clear();

        ECKey sender = this.deployerKeys.get(deployerKeys.size() - 1);
        txList.add(makePrecompiledContractTransaction(sender, BigInteger.ZERO));

        for (int i = 0; i < this.deployerKeys.size() && i < deployerNum; i++) {
            ECKey senderKey = this.deployerKeys.get(i);
            txList.add(
                    makeFvmContractCallTransaction(senderKey, BigInteger.ONE, deployAddr.get(i)));
        }

        AionBlock callBlock = genNewBlock(txList, blockchainWoAVM);
        tryImportNewBlock(blockchainWoAVM, callBlock);

        // Does not initial in the setup call due to the avmenable in the StandaloneBlockchain is a
        // static variable.
        StandaloneBlockchain.Bundle bundleAVM =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts(deployerKeys)
                        .withAvmEnabled()
                        .withValidatorConfiguration("simple")
                        .build();
        StandaloneBlockchain blockchainWithAVM = bundleAVM.bc;

        tryImportNewBlock(blockchainWithAVM, deplayBlock);
        tryImportNewBlock(blockchainWithAVM, callBlock);
    }

    private AionTransaction makePrecompiledContractTransaction(ECKey sender, BigInteger nonce) {
        AionTransaction transaction =
                newTransaction(
                        nonce,
                        Address.wrap(sender.getAddress()),
                        ContractFactory.getBlake2bHashContractAddress(),
                        BigInteger.ONE,
                        new byte[10],
                        2_000_000,
                        this.energyPrice,
                        (byte) 0x01);
        transaction.sign(sender);
        return transaction;
    }

    // Deploys the Ticker.sol contract.
    private AionTransaction makeFvmContractCreateTransaction(ECKey sender, BigInteger nonce)
            throws IOException {
        byte[] contractBytes = ContractUtils.getContractDeployer("Ticker.sol", "Ticker");

        AionTransaction transaction =
                newTransaction(
                        nonce,
                        Address.wrap(sender.getAddress()),
                        null,
                        BigInteger.ZERO,
                        contractBytes,
                        5_000_000,
                        this.energyPrice,
                        (byte) 0x01);
        transaction.sign(sender);
        return transaction;
    }

    private AionTransaction makeFvmContractCallTransaction(
            ECKey sender, BigInteger nonce, Address contract) {
        // This hash will call the 'ticking' function of the deployed contract (this increments a
        // counter).
        byte[] callBytes = Hex.decode("dae29f29");

        AionTransaction transaction =
                newTransaction(
                        nonce,
                        Address.wrap(sender.getAddress()),
                        contract,
                        BigInteger.ZERO,
                        callBytes,
                        2_000_000,
                        this.energyPrice,
                        (byte) 0x01);
        transaction.sign(sender);
        return transaction;
    }

    private AionTransaction newTransaction(
            BigInteger nonce,
            Address sender,
            Address destination,
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

    private AionBlock genNewBlock(List<AionTransaction> transactions, StandaloneBlockchain bc) {
        AionBlock parentBlock = bc.getBestBlock();
        return bc.createBlock(parentBlock, transactions, false, parentBlock.getTimestamp());
    }

    private void tryImportNewBlock(StandaloneBlockchain bc, AionBlock block) {
        Pair<ImportResult, AionBlockSummary> connectResult = bc.tryToConnectAndFetchSummary(block);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        connectResult.getRight();
    }
}
