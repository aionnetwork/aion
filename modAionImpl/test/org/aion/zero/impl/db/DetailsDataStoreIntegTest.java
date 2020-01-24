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
import org.aion.types.AionAddress;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
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
}
