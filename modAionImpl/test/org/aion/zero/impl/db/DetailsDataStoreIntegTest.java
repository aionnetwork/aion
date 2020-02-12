package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.addMiningBlock;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.addStakingBlock;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.deployLargeStorageContractTransaction;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.generateAccounts;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.generateRandomUnityChain;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.putToLargeStorageTransaction;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypeRule;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.log.AionLoggerFactory;
import org.aion.mcf.blockchain.Block;
import org.aion.types.AionAddress;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.vm.AvmPathManager;
import org.aion.zero.impl.vm.AvmTestConfig;
import org.aion.zero.impl.vm.TestResourceProvider;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
            assertThat(chain.getRepository().storageDatabase.isEmpty()).isFalse();

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
    @Ignore
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
        int insertTxPerBlock = 9, updateTxPerBlock = 5, deleteTxPerBlock = 4;
        int numberOfStorageBlocks = 2*6; // results in 3*6 blocks with storage in details, 3*6 after transition
        LinkedList<byte[]> keys = new LinkedList<>();

        // populate chain
        for (int i = 0; i < numberOfStorageBlocks; i++) {
            // call contract to increase storage
            for (int j = 0; j < insertTxPerBlock; j++) {
                byte[] key = RandomUtils.nextBytes(32 - i);
                txs.add(
                        putToLargeStorageTransaction(
                                resourceProvider.factoryForVersion2,
                                account,
                                key,
                                RandomUtils.nextBytes(32 - i),
                                nonce,
                                contract));
                nonce = nonce.add(BigInteger.ONE);
                keys.addLast(key);
            }
            addMiningBlock(chain, chain.getBestBlock(), txs);

            // call contract to update storage
            for (int j = 0; j < updateTxPerBlock; j++) {
                byte[] key = keys.removeFirst();
                txs.add(
                        putToLargeStorageTransaction(
                                resourceProvider.factoryForVersion2,
                                account,
                                key,
                                RandomUtils.nextBytes(32 - i - 1),
                                nonce,
                                contract));
                nonce = nonce.add(BigInteger.ONE);
                keys.addLast(key);
            }
            addMiningBlock(chain, chain.getBestBlock(), txs);

            // call contract to delete storage
            for (int j = 0; j < deleteTxPerBlock; j++) {
                txs.add(
                        putToLargeStorageTransaction(
                                resourceProvider.factoryForVersion2,
                                account,
                                keys.removeFirst(),
                                null,
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
        boolean isEmptyStorage = true, isSaved = false;

        // generate data by importing each block to the chain with/without transition
        for (int i = 2; i <= blocks; i++) {
            block = chain.getBlockByNumber(i);

            // importing without transition first
            // In case there is any bias due to caching, it will disfavour this option.
            // Even with any potential nevative bias, the option seems to have better performance.
//             AionContractDetailsImpl.detailsInMemoryStorageLimit = 0;

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
            // TODO: re-purpose test for db size impact on storage or data locality impact on eternal storage
//            AionContractDetailsImpl.detailsInMemoryStorageLimit = 65536;
//
//            start = System.nanoTime();
//            importResult = chainWithTransition.tryToConnect(block);
//            duration = System.nanoTime() - start;
//            assertThat(importResult).isEqualTo(ImportResult.IMPORTED_BEST);
//            details = chainWithTransition.getRepository().detailsDatabase.approximateSize();
//            storage = chainWithTransition.getRepository().storageDatabase.approximateSize();
//            size = details + storage;
//
//            results[i][0] = duration;
//            results[i][1] = details;
//            results[i][2] = storage;
//            results[i][3] = size;
//
//            totalTimeWithTransition += duration;
//            totalDetailsSizeWithTransition += details;
//            totalStorageSizeWithTransition += storage;
//            totalSizeWithTransition += size;

            if (storage != 0 && isEmptyStorage) {
                isEmptyStorage = false;
            }

            if (!isEmptyStorage && !isSaved) {
                results[1][0] = totalTimeWithTransition;
                results[1][1] = totalDetailsSizeWithTransition;
                results[1][2] = totalStorageSizeWithTransition;
                results[1][3] = totalSizeWithTransition;
                results[1][4] = totalTimeWithoutTransition;
                results[1][5] = totalDetailsSizeWithoutTransition;
                results[1][6] = totalStorageSizeWithoutTransition;
                results[1][7] = totalSizeWithoutTransition;
                isSaved = true;
            }
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
        System.out.printf("---------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");
        System.out.printf(
                "| %8s | %10s | %53s | %53s | %25s |\n",
                " ", " ", "With Storage Transition", "Without Storage Transition", "Difference");
        System.out.printf(
                "| %8s | %10s | %11s | %11s | %11s | %11s | %11s | %11s | %11s | %11s | %11s | %11s |\n",
                " ", "Type",
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
        System.out.printf("---------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");
        System.out.printf(
                "| %21s | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d |\n",
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
        System.out.printf("---------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");
        System.out.printf(
                "| %21s | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d |\n",
                "Read In-line Total",
                results[1][0],
                results[1][1],
                results[1][2],
                results[1][3],
                results[1][4],
                results[1][5],
                results[1][6],
                results[1][7],
                (results[1][0] - results[1][4]),
                (results[1][3] - results[1][7]));
        System.out.printf("---------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");
        // aggregate data for each type of update
        long[][] extra = new long[3][8];
        for (int i = 2; (i < results.length) && (results[i][2] == 0); i++) {
            int k = i % 3;
            for (int j = 0; j < 8; j++) {
                extra[k][j] += results[i][j];
            }
        }
        // inserts are at block nb % 3 = 2
        System.out.printf(
                "| %21s | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d |\n",
                "Insert In-line Total",
                extra[2][0],
                extra[2][1],
                extra[2][2],
                extra[2][3],
                extra[2][4],
                extra[2][5],
                extra[2][6],
                extra[2][7],
                (extra[2][0] - extra[2][4]),
                (extra[2][3] - extra[2][7]));
        // updates are at block nb % 3 = 0
        System.out.printf(
                "| %21s | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d |\n",
                "Update In-line Total",
                extra[0][0],
                extra[0][1],
                extra[0][2],
                extra[0][3],
                extra[0][4],
                extra[0][5],
                extra[0][6],
                extra[0][7],
                (extra[0][0] - extra[0][4]),
                (extra[0][3] - extra[0][7]));
        // deletes are at block nb % 3 = 1
        System.out.printf(
                "| %21s | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d |\n",
                "Delete In-line Total",
                extra[1][0],
                extra[1][1],
                extra[1][2],
                extra[1][3],
                extra[1][4],
                extra[1][5],
                extra[1][6],
                extra[1][7],
                (extra[1][0] - extra[1][4]),
                (extra[1][3] - extra[1][7]));
        System.out.printf("---------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");
        for (int i = 2; i < results.length; i++) {
            System.out.printf(
                    "| Block %2d | %10s | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d | %11d |\n",
                    i,
                    (i % 3 == 0 ? "5 tx UPD" : (i % 3 == 1 ? "4 tx DEL" : "9 tx INS")),
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
        System.out.printf("---------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");
    }
}
