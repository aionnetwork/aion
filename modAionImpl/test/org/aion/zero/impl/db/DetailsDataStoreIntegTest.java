package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.addMiningBlock;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.addStakingBlock;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.deployLargeStorageContractTransaction;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.generateAccounts;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.generateNextMiningBlock;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.generateNextStakingBlock;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.generateRandomUnityChain;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.putToLargeStorageTransaction;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypeRule;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.mcf.blockchain.Block;
import org.aion.types.AionAddress;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.vm.AvmPathManager;
import org.aion.zero.impl.vm.AvmTestConfig;
import org.aion.zero.impl.vm.TestResourceProvider;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit and integration tests for {@link DetailsDataStore}.
 *
 * @author Alexandra Roatis
 */
public class DetailsDataStoreIntegTest {
    private static final long unityForkBlock = 2;
    private TestResourceProvider resourceProvider;

    @Before
    public void setup()
            throws ClassNotFoundException, InstantiationException, IllegalAccessException,
                    IOException {
        // reduce default logging levels
        AionLoggerFactory.initAll();

        resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());

        // enable both AVMs without overlap
        AvmTestConfig.supportBothAvmVersions(0, unityForkBlock, 0);

        TransactionTypeRule.allowAVMContractTransaction();
    }

    @After
    public void tearDown() {
        AvmTestConfig.clearConfigurations();
    }

    /**
     * Creates a contract with large storage. Ensures that the trie transitions from the details
     * database to the storage database after reaching the transition size.
     */
    @Test
    public void largeStorageTransition() {
        int txPerBlock = 60;
        int numberOfStorageBlocks = 2; // storage transitions after the second call block is added

        // setup accounts
        List<ECKey> accounts = generateAccounts(3);
        ECKey stakingRegistryOwner = accounts.get(0);
        ECKey staker = accounts.get(1);
        ECKey account = accounts.get(2); // used to deploy and call the large storage contract
        BigInteger nonce = BigInteger.ZERO;

        // setup two identical blockchains
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain chain =
                builder.withValidatorConfiguration("simple")
                        .withDefaultAccounts(accounts)
                        .withAvmEnabled()
                        .build()
                        .bc;
        chain.forkUtility.enableUnityFork(unityForkBlock);

        // populate the chain for unity
        generateRandomUnityChain(chain, resourceProvider, 3, 1, List.of(staker), stakingRegistryOwner, 0);

        // tracks all the deployed storage contracts
        List<AionTransaction> txs = new ArrayList<>();

        // deploy the storage contract
        AionTransaction tx =
                deployLargeStorageContractTransaction(
                        resourceProvider.factoryForVersion2, account, nonce);
        nonce = nonce.add(BigInteger.ONE);
        addMiningBlock(chain, chain.getBestBlock(), List.of(tx));

        // save the contract address
        AionAddress contract = TxUtil.calculateContractAddress(tx);
        System.out.println("Contract: " + contract);

        boolean stakingIsNext = true;
        for (int i = 0; i < numberOfStorageBlocks; i++) {
            // verify that the storage is empty
            assertThat(chain.getRepository().storageDatabase.isEmpty()).isTrue();

            // call contracts to increase storage
            for (int j = 0; j < txPerBlock; j++) {
                txs.add(
                        putToLargeStorageTransaction(
                                resourceProvider.factoryForVersion2,
                                account,
                                RandomUtils.nextBytes(32),
                                RandomUtils.nextBytes(32),
                                nonce,
                                contract));
                nonce = nonce.add(BigInteger.ONE);
            }

            if (stakingIsNext) {
                addStakingBlock(chain, chain.getBestBlock(), txs, staker);
            } else {
                addMiningBlock(chain, chain.getBestBlock(), txs);
            }
            stakingIsNext = !stakingIsNext;
        }

        // verify that the storage has transitioned
        assertThat(chain.getRepository().storageDatabase.isEmpty()).isFalse();
    }

    /**
     * Creates a contract with large storage. Imports 20 blocks expanding the storage and prints
     * times and database size with and without storage transition.
     */
    @Test
    public void largeStorageTransitionBenchmark() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        // setup account used to deploy and call the large storage contract
        List<ECKey> accounts = generateAccounts(1);
        ECKey account = accounts.get(0);

        // setup AVM
        AvmTestConfig.clearConfigurations(); // clear setting from @Before
        TransactionTypeRule.allowAVMContractTransaction();
        resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());
        AvmTestConfig.supportBothAvmVersions(0, 1, 0); // enable both AVMs without overlap

        // setup 3 identical blockchains
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain chain = builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).withAvmEnabled().build().bc;
        StandaloneBlockchain chainWithTransition = builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).withAvmEnabled().build().bc;
        StandaloneBlockchain chainWithoutTransition = builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).withAvmEnabled().build().bc;

        // disabling Unity for this test
        chain.forkUtility.disableUnityFork();
        chainWithTransition.forkUtility.disableUnityFork();
        chainWithoutTransition.forkUtility.disableUnityFork();

        assertThat(chain).isNotEqualTo(chainWithTransition);
        assertThat(chain.genesis).isEqualTo(chainWithTransition.genesis);
        assertThat(chain).isNotEqualTo(chainWithoutTransition);
        assertThat(chain.genesis).isEqualTo(chainWithoutTransition.genesis);
        assertThat(chainWithoutTransition).isNotEqualTo(chainWithTransition);
        assertThat(chainWithoutTransition.genesis).isEqualTo(chainWithTransition.genesis);

        // deploy the storage contract
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx = deployLargeStorageContractTransaction(resourceProvider.factoryForVersion2, account, nonce);
        nonce = nonce.add(BigInteger.ONE);
        addMiningBlock(chain, chain.getBestBlock(), List.of(tx));

        // save the contract address
        AionAddress contract = TxUtil.calculateContractAddress(tx);
        System.out.println("Contract: " + contract);

        List<AionTransaction> txs = new ArrayList<>();
        int txPerBlock = 8;
        int numberOfStorageBlocks = 20; // 10 blocks with storage in details, 10 after transition

        // populate chain
        for (int i = 0; i < numberOfStorageBlocks; i++) {
            // call contract to increase storage
            for (int j = 0; j < txPerBlock; j++) {
                txs.add(
                        putToLargeStorageTransaction(
                                resourceProvider.factoryForVersion2,
                                account,
                                RandomUtils.nextBytes(32),
                                RandomUtils.nextBytes(32),
                                nonce,
                                contract));
                nonce = nonce.add(BigInteger.ONE);
            }

            addMiningBlock(chain, chain.getBestBlock(), txs);
        }

        // import the contract block (not part of the benchmark)
        Block block = chain.getBlockByNumber(1);
        ImportResult importResult = chainWithoutTransition.tryToConnect(block);
        assertThat(importResult).isEqualTo(ImportResult.IMPORTED_BEST);
        importResult = chainWithTransition.tryToConnect(block);
        assertThat(importResult).isEqualTo(ImportResult.IMPORTED_BEST);

        long start, duration, storage, details, size;
        long totalTimeWithTransition = 0,
                totalDetailsSizeWithTransition = 0,
                totalStorageSizeWithTransition = 0,
                totalSizeWithTransition = 0,
                totalTimeWithoutTransition = 0,
                totalDetailsSizeWithoutTransition = 0,
                totalStorageSizeWithoutTransition = 0,
                totalSizeWithoutTransition = 0;

        int blocks = (int) chain.getBestBlock().getNumber();
        long results[][] = new long[blocks + 1][8];

        // generate data by importing each block to the chain with/without transition
        for (int i = 2; i <= blocks; i++) {
            block = chain.getBlockByNumber(i);

            // importing without transition first
            // In case there is any bias due to caching, it will disfavour this option.
            // Even with any potential nevative bias, the option seems to have better performance.
            AionContractDetailsImpl.detailsInMemoryStorageLimit = 0;

            start = System.nanoTime();
            importResult = chainWithoutTransition.tryToConnect(block);
            duration = System.nanoTime() - start;
            assertThat(importResult).isEqualTo(ImportResult.IMPORTED_BEST);
            details = chainWithoutTransition.getRepository().detailsDatabase.approximateSize();
            storage = chainWithoutTransition.getRepository().storageDatabase.approximateSize();
            size = details + storage;

            results[i][4] = duration;
            results[i][5] = details;
            results[i][6] = storage;
            results[i][7] = size;

            totalTimeWithoutTransition += duration;
            totalDetailsSizeWithoutTransition += details;
            totalStorageSizeWithoutTransition += storage;
            totalSizeWithoutTransition += size;

            // importing with transition second
            AionContractDetailsImpl.detailsInMemoryStorageLimit = 65536;

            start = System.nanoTime();
            importResult = chainWithTransition.tryToConnect(block);
            duration = System.nanoTime() - start;
            assertThat(importResult).isEqualTo(ImportResult.IMPORTED_BEST);
            details = chainWithTransition.getRepository().detailsDatabase.approximateSize();
            storage = chainWithTransition.getRepository().storageDatabase.approximateSize();
            size = details + storage;

            results[i][0] = duration;
            results[i][1] = details;
            results[i][2] = storage;
            results[i][3] = size;

            totalTimeWithTransition += duration;
            totalDetailsSizeWithTransition += details;
            totalStorageSizeWithTransition += storage;
            totalSizeWithTransition += size;
        }

        // Save totals
        results[0][0] = totalTimeWithTransition;
        results[0][1] = totalDetailsSizeWithTransition;
        results[0][2] = totalStorageSizeWithTransition;
        results[0][3] = totalSizeWithTransition;
        results[0][4] = totalTimeWithoutTransition;
        results[0][5] = totalDetailsSizeWithoutTransition;
        results[0][6] = totalStorageSizeWithoutTransition;
        results[0][7] = totalSizeWithoutTransition;

        print(results);
    }

    private static void print(long results[][]) {
        System.out.printf("--------------------------------------------------------------------------------------------------------------------------------------------------------\n");
        System.out.printf(
                "| %8s | %53s | %53s | %25s |\n",
                " ", "With Storage Transition", "Without Storage Transition", "Difference");
        System.out.printf(
                "| %8s | %11s | %11s | %11s | %11s | %11s | %11s | %11s | %11s | %11s | %11s |\n",
                " ",
                "Time (ns)",
                "Detais (B)",
                "Storage (B)",
                "Size (B)",
                "Time (ns)",
                "Detais (B)",
                "Storage (B)",
                "Size (B)",
                "Time (ns)",
                "Size (B)");
        System.out.printf("--------------------------------------------------------------------------------------------------------------------------------------------------------\n");
        System.out.printf(
                "| %8s | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d |\n",
                "Total",
                results[0][0],
                results[0][1],
                results[0][2],
                results[0][3],
                results[0][4],
                results[0][5],
                results[0][6],
                results[0][7],
                (results[0][0] - results[0][4]),
                (results[0][3] - results[0][7]));
        System.out.printf("--------------------------------------------------------------------------------------------------------------------------------------------------------\n");
        for (int i = 2; i < results.length; i++) {
            System.out.printf(
                    "| Block %2d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d |\n",
                    i,
                    results[i][0],
                    results[i][1],
                    results[i][2],
                    results[i][3],
                    results[i][4],
                    results[i][5],
                    results[i][6],
                    results[i][7],
                    (results[i][0] - results[i][4]),
                    (results[i][3] - results[i][7]));
        }
        System.out.printf("--------------------------------------------------------------------------------------------------------------------------------------------------------\n");
    }
}
