package org.aion.zero.impl.blockchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.Collections;
import org.aion.avm.stub.IContractFactory;
import org.aion.avm.stub.IContractFactory.AvmContract;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.base.TransactionTypeRule;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.types.AionAddress;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.types.MiningBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.AvmPathManager;
import org.aion.zero.impl.vm.AvmTestConfig;
import org.aion.zero.impl.vm.TestResourceProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class Avm2ForkingTest {

    private static IContractFactory contractFactory;
    private static TestResourceProvider resourceProvider;
    private StandaloneBlockchain blockchain;
    private ECKey deployer;


    @BeforeClass
    public static void setupAvm() throws Exception {
        TransactionTypeRule.allowAVMContractTransaction();
        resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());
        AvmTestConfig.supportBothAvmVersions(0, 2, 0);
        contractFactory = resourceProvider.factoryForVersion2.newContractFactory();
    }

    @AfterClass
    public static void tearDownAvm() throws Exception {
        TransactionTypeRule.disallowAVMContractTransaction();
        AvmTestConfig.clearConfigurations();
        resourceProvider.close();
    }

    @Before
    public void setup() {
        // generate bc bundle with pruning enabled
        StandaloneBlockchain.Bundle bundle =
            new StandaloneBlockchain.Builder()
                .withDefaultAccounts()
                .withValidatorConfiguration("simple")
                .withAvmEnabled()
                .build();

        blockchain = bundle.bc;
        deployer = bundle.privateKeys.get(0);
    }

    @Test
    public void testAvm2CacheResetForkpointReorg() {
        AionTransaction tx1a =
            AionTransaction.create(
                deployer,
                BigInteger.ZERO.toByteArray(),
                null,
                BigInteger.ZERO.toByteArray(),
                contractFactory.getDeploymentBytes(AvmContract.HELLO_WORLD),
                5_000_000L,
                10_000_000_000L,
                TransactionTypes.AVM_CREATE_CODE,
                null);

        AionTransaction tx1b =
            AionTransaction.create(
                deployer,
                BigInteger.ZERO.toByteArray(),
                null,
                BigInteger.ZERO.toByteArray(),
                contractFactory.getDeploymentBytes(AvmContract.STATEFULNESS),
                5_000_000L,
                10_000_000_000L,
                TransactionTypes.AVM_CREATE_CODE,
                null);

        AionAddress addr0 = TxUtil.calculateContractAddress(tx1a);
        byte[] callSayHello = resourceProvider.factoryForVersion2.newStreamingEncoder().encodeOneString("sayHello").getEncoding();
        byte[] callGetCount = resourceProvider.factoryForVersion2.newStreamingEncoder().encodeOneString("getCount").getEncoding();

        AionTransaction tx2a =
            AionTransaction.create(
                deployer,
                BigInteger.ONE.toByteArray(),
                addr0,
                BigInteger.ZERO.toByteArray(),
                callSayHello,
                22000L,
                10_000_000_000L,
                TransactionTypes.DEFAULT,
                null);

        AionTransaction tx2b =
            AionTransaction.create(
                deployer,
                BigInteger.ONE.toByteArray(),
                addr0,
                BigInteger.ZERO.toByteArray(),
                callGetCount,
                2_000_000L,
                10_000_000_000L,
                TransactionTypes.DEFAULT,
                null);

        MiningBlock block1a = blockchain.createBlock(blockchain.genesis, Collections.singletonList(tx1a), false, 0);
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block1a);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        assertTrue(receipt.isSuccessful());
        
        MiningBlock block2a = blockchain.createBlock(blockchain.getBestBlock(), Collections.singletonList(tx2a), false, 0);
        connectResult = blockchain.tryToConnectAndFetchSummary(block2a);
        receipt = connectResult.getRight().getReceipts().get(0);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        assertFalse(receipt.isSuccessful());

        MiningBlock block1b = blockchain.createBlock(blockchain.genesis, Collections.singletonList(tx1b), false, 0);
        connectResult = blockchain.tryToConnectAndFetchSummary(block1b);
        receipt = connectResult.getRight().getReceipts().get(0);
        assertEquals(ImportResult.IMPORTED_NOT_BEST, connectResult.getLeft());
        assertTrue(receipt.isSuccessful());

        MiningBlock block2b = blockchain.createBlock(block1b, Collections.singletonList(tx2b), false, 0);
        connectResult = blockchain.tryToConnectAndFetchSummary(block2b);
        receipt = connectResult.getRight().getReceipts().get(0);
        assertEquals(ImportResult.IMPORTED_NOT_BEST, connectResult.getLeft());
        assertTrue(receipt.isSuccessful());
        assertEquals(0, resourceProvider.factoryForVersion2.newDecoder(receipt.getTransactionOutput()).decodeOneInteger());
    }
}
