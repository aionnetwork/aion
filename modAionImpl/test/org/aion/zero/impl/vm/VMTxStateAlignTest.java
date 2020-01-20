package org.aion.zero.impl.vm;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.core.ImportResult;
import org.aion.base.TransactionTypeRule;
import org.aion.precompiled.ContractInfo;
import org.aion.types.AionAddress;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.contracts.ContractUtils;
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
    private List<AionAddress> deployers;
    private long energyPrice = 10_000_000_000L;

    @BeforeClass
    public static void setupVM() {
        TransactionTypeRule.disallowAVMContractTransaction();

        AvmTestConfig.supportOnlyAvmVersion1();
    }

    @AfterClass
    public static void tearDownClass() {
        AvmTestConfig.clearConfigurations();
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
            AionAddress deployerAddr = new AionAddress(this.deployerKeys.get(i).getAddress());
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
        List<AionAddress> deployAddr = new ArrayList<>();

        for (int i = 0; i < this.deployerKeys.size() && i < deployerNum; i++) {
            ECKey senderKey = this.deployerKeys.get(i);
            txList.add(makeFvmContractCreateTransaction(senderKey, BigInteger.ZERO));
            deployAddr.add(TxUtil.calculateContractAddress(txList.get(i)));
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
        return AionTransaction.create(
                sender,
                nonce.toByteArray(),
                ContractInfo.BLAKE_2B.contractAddress,
                BigInteger.ONE.toByteArray(),
                new byte[10],
                2_000_000,
                this.energyPrice,
                TransactionTypes.DEFAULT, null);
    }

    // Deploys the Ticker.sol contract.
    private AionTransaction makeFvmContractCreateTransaction(ECKey sender, BigInteger nonce)
            throws IOException {
        byte[] contractBytes = ContractUtils.getContractDeployer("Ticker.sol", "Ticker");

        return AionTransaction.create(
                sender,
                nonce.toByteArray(),
                null,
                new byte[0],
                contractBytes,
                5_000_000,
                this.energyPrice,
                TransactionTypes.DEFAULT, null);
    }

    private AionTransaction makeFvmContractCallTransaction(
            ECKey sender, BigInteger nonce, AionAddress contract) {
        // This hash will call the 'ticking' function of the deployed contract (this increments a
        // counter).
        byte[] callBytes = Hex.decode("dae29f29");

        return AionTransaction.create(
                sender,
                nonce.toByteArray(),
                contract,
                new byte[0],
                callBytes,
                2_000_000,
                this.energyPrice,
                TransactionTypes.DEFAULT, null);
    }

    private AionBlock genNewBlock(List<AionTransaction> transactions, StandaloneBlockchain bc) {
        Block parentBlock = bc.getBestBlock();
        return bc.createBlock(parentBlock, transactions, false, parentBlock.getTimestamp());
    }

    private void tryImportNewBlock(StandaloneBlockchain bc, AionBlock block) {
        Pair<ImportResult, AionBlockSummary> connectResult = bc.tryToConnectAndFetchSummary(block);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        connectResult.getRight();
    }
}
