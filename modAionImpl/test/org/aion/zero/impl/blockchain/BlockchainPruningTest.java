package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.MIN_SELF_STAKE;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.base.TransactionTypeRule;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.log.AionLoggerFactory;
import org.aion.mcf.blockchain.Block;
import org.aion.types.AionAddress;
import org.aion.util.conversions.Hex;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.AvmPathManager;
import org.aion.zero.impl.vm.AvmTestConfig;
import org.aion.zero.impl.vm.TestResourceProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for pruning functionality on the blockchain.
 *
 * @author Alexandra Roatis
 */
public class BlockchainPruningTest {

    private static final List<String> privateKeys = List.of(
            "3c398025cbaf1ec8a27683e25125872cd2b9fbd00a6217c19c8301f317a8400b38265fba364dd9652de7b6b99e22f1055926d6a2724a8defce1eea8042264a0d",
            "3c6e19799a8705687257c101bab867315c096d05e5cd7e8594eb155a9f568f4570bfcdb73e5d2d0a1372bef87f5adb2ea911af0350fa661a0e360d5f248b05b8",
            "69a90fae460965c70cf6a9c47e68232ab523176beec0f8a875257b2c902677a251d8a926f103f040507a9f4673cd9c0ce81757df282235311543b6a8beb8f46c",
            "8262cadb62be41c9bc29fe8b2ffd149d67349bcb065b4802c4a6c8abed1a3e2eb9f29fff538a22d6c27e4b3b0c28daf4e41d6e80f4e110ba348a265d586b959b",
            "1b977969d2aebde24a5a43fd2408fc575e7a121a24ef7167a7f8d62e1e9c881ca4eb902f694db3602f9d12ccc05320f582f3b1f4780670d86b2a0d2997a6b592",
            "32cbcae7d056941d5db0badea4c648944a6a3fd9be5d8d6af3dcf3fcfc6816992e3d7735cd1823517778be04980b218346e61944278f140dbddf60fb207292a7",
            "fee8b192ffd6fc2d29fb8ed3f9dcc433fbeb7b57efdc808dfcc222f7ef92ae5cab0dbc94278dfb70b00bbb0244f1b10408f8249693333236dacb01c7a46307b0",
            "18fd5b086770f8038edfa0abf39361cc7db56f85b244096ed5f9547c5e2ba03fffaed80dbf735121c089b87128bded930eb7a6ce13dcc2344f480a61824b5212",
            "d6feb8a80aa965803427077a3fd490b92f6c6b77a6b3d5e16c25175c8a5a57665af2635a18853317b37268630063060f583a0d5181afd874a5871520be9b201a",
            "f8351f25bf3f19be92fa7f85b6d196823ce266ae715d0360e28e48e3712320f7eab670fcd842e31c90d7e48a78ed7a24de7e2ae30f6ded5788a2b3954a0fb4d5",
            "bff7e39454d4308ddac99f6e7edc96563b625af5bb3ea7194ca76e355846e5f042aefcd7cbdeb2391cff3999c30a421e1848e079d2c1c291773154444770dd66",
            "5488ab37d54713358293a2c7af7d82809c5b12cad2e2fa44d2ccbba561f0a3a4f17b4643adf6ab3a96e746f0d91849bd2d0e871a5c305fd466b8ec2aa64ffd1e");
    private static List<ECKey> accounts;

    private static final long unityForkBlock = 2;
    private static TestResourceProvider resourceProvider;

    @Before
    public void setup() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        // Set logging to warnings to avoid spam.
        AionLoggerFactory.initAll();

        // setup Unity fork and AVM
        AvmTestConfig.clearConfigurations();
        TransactionTypeRule.allowAVMContractTransaction();
        resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());
        AvmTestConfig.supportBothAvmVersions(0, unityForkBlock, 0); // enable both AVMs without overlap

        accounts = privateKeys.stream().map(k -> org.aion.crypto.ECKeyFac.inst().fromPrivate(Hex.decode(k))).collect(Collectors.toList());
    }

    @After
    public void tearDown() {
        AvmTestConfig.clearConfigurations();
    }

    @Test
    public void testTopPruningWithoutSideChains() throws ClassNotFoundException, IOException, InstantiationException, IllegalAccessException {
        // Setup used accounts.
        assertThat(accounts.size()).isAtLeast(12);
        ECKey stakingRegistryOwner = accounts.get(0);
        // Lists of stakers.
        List<ECKey> allStakes = List.of(accounts.get(1), accounts.get(2), accounts.get(3), accounts.get(4), accounts.get(5), accounts.get(6));
        List<ECKey> mainStakers = List.of(accounts.get(1), accounts.get(2), accounts.get(3));
        List<ECKey> otherStakers = List.of(accounts.get(4), accounts.get(5), accounts.get(6));
        // Lists of users.
        List<ECKey> mainUsers = List.of(accounts.get(1), accounts.get(2), accounts.get(3), accounts.get(7), accounts.get(8), accounts.get(9));
        List<ECKey> otherUsers = List.of(accounts.get(4), accounts.get(5), accounts.get(6), accounts.get(10), accounts.get(11), accounts.get(0));

        // Setup the blockchain.
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain chain = builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).withAvmEnabled().build().bc;
        chain.forkUtility.enableUnityFork(unityForkBlock);

        // Setup TOP pruning for the repository.
        AionRepositoryImpl repository = chain.getRepository();
        repository.setupTopPruning(1);

        // Setup the first block in the chain with the staker registry deployment.
        Block nextBlock = BlockchainTestUtils.generateNextMiningBlockWithStakerRegistry(chain, chain.getGenesis(), resourceProvider, stakingRegistryOwner);
        Pair<ImportResult, AionBlockSummary> result = chain.tryToConnectAndFetchSummary(nextBlock);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(result.getRight().getReceipts().get(0).isSuccessful()).isTrue();
        assertThat(result.getRight().getReceipts().get(0).getLogInfoList()).isNotEmpty();
        assertThat(result.getRight().getReceipts().get(0).getEnergyUsed()).isEqualTo(1_225_655L);

        // Ensure the current state was not pruned after the import.
        verifyFullState(repository, nextBlock);

        // Set the staking contract address in the staking genesis.
        AionTransaction deploy = nextBlock.getTransactionsList().get(0);
        AionAddress contract = TxUtil.calculateContractAddress(deploy.getSenderAddress().toByteArray(), deploy.getNonceBI());
        chain.getGenesis().setStakingContractAddress(contract);

        // Create block to register all stakers.
        nextBlock = BlockchainTestUtils.generateNextMiningBlockWithStakers(chain, chain.getBestBlock(), resourceProvider, allStakes, MIN_SELF_STAKE);
        result = chain.tryToConnectAndFetchSummary(nextBlock);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Ensure the current state was not pruned after the import.
        verifyFullState(repository, nextBlock);

        // Verify that all stakers were registered.
        verifyReceipts(result.getRight().getReceipts(), allStakes.size(), true);
        verifyEffectiveSelfStake(otherStakers, chain, nextBlock, MIN_SELF_STAKE);

        // Generate random transactions for all accounts to add them to the state.
        List<AionTransaction> txs = BlockchainTestUtils.generateTransactions(1_000, accounts, chain.getRepository());
        nextBlock = BlockchainTestUtils.generateNextStakingBlock(chain, nextBlock, txs, otherStakers.get(0));
        result = chain.tryToConnectAndFetchSummary(nextBlock);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Ensure the current state was not pruned after the import.
        verifyFullState(repository, nextBlock);

        BigInteger expectedStake = MIN_SELF_STAKE;

        for (int i = 0; i < 6; i++) {
            // Add blocks with transactions for mainStakers and mainUsers.
            for (int j = 0; j < 6; j++) {
                // Add transactions for frequent users.
                txs = BlockchainTestUtils.generateTransactions(1_000, mainUsers, chain.getRepository());
                // Seal the block with a frequent staker.
                ECKey staker = mainStakers.get((i + j) % mainStakers.size());
                if (nextBlock instanceof AionBlock) {
                    nextBlock = BlockchainTestUtils.generateNextStakingBlock(chain, nextBlock, txs, staker);
                } else {
                    nextBlock = BlockchainTestUtils.generateNextMiningBlock(chain, nextBlock, txs);
                }
                result = chain.tryToConnectAndFetchSummary(nextBlock);
                assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);

                // Ensure the current state was not pruned after the import.
                verifyFullState(repository, nextBlock);
            }

            // Increase stake of mainStakes.
            txs = BlockchainTestUtils.generateIncreaseStakeTransactions(chain, nextBlock, resourceProvider, mainStakers, MIN_SELF_STAKE);
            assertThat(txs.size()).isEqualTo(mainStakers.size());
            nextBlock = BlockchainTestUtils.generateNextMiningBlock(chain, nextBlock, txs);
            result = chain.tryToConnectAndFetchSummary(nextBlock);
            assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
            verifyReceipts(result.getRight().getReceipts(), mainStakers.size(), false);

            // Ensure the current state was not pruned after the import.
            verifyFullState(repository, nextBlock);

            // Verify stakers effective stake update.
            expectedStake = expectedStake.add(MIN_SELF_STAKE);
            verifyEffectiveSelfStake(mainStakers, chain, nextBlock, expectedStake);

            // Add transactions for infrequent users.
            txs = BlockchainTestUtils.generateTransactions(10, otherUsers, chain.getRepository());
            // Seal the block with an infrequent staker.
            ECKey staker = otherStakers.get(i % otherStakers.size());
            nextBlock = BlockchainTestUtils.generateNextStakingBlock(chain, nextBlock, txs, staker);
            result = chain.tryToConnectAndFetchSummary(nextBlock);
            assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);

            // Ensure the current state was not pruned after the import.
            verifyFullState(repository, nextBlock);
        }
    }

    public void verifyFullState(AionRepositoryImpl repository, Block block) {
        try {
            // Traverse the trie and count the number of keys. Throws an exception when a key is expected to exist and not found.
            byte[] stateRoot = block.getStateRoot();
            int size = repository.getWorldState().getTrieSize(stateRoot);
            assertThat(size).isNotNull();
            System.out.format(
                    "Block #%2d hash=%s txs=%3d root=%s trie-size=%4d %n",
                    block.getNumber(),
                    block.getShortHash(),
                    block.getTransactionsList().size(),
                    ByteArrayWrapper.wrap(stateRoot), size);
        } catch (Exception e) {
            System.out.println("The world state for the given root is incomplete.");
            e.printStackTrace();
            throw e;
        }
    }

    public void verifyReceipts(List<AionTxReceipt> stakerRegistrationReceipts, int expectedSize, boolean checkEnergy) {
        assertThat(stakerRegistrationReceipts.size()).isEqualTo(expectedSize);

        for (AionTxReceipt receipt : stakerRegistrationReceipts) {
            assertThat(receipt.isSuccessful()).isTrue();
            assertThat(receipt.getLogInfoList()).isNotEmpty();
            if (checkEnergy) {
                // the value below can differ slightly depending on the address of the caller
                assertThat(receipt.getEnergyUsed()).isAtLeast(180_000L);
            }
        }
    }

    public void verifyEffectiveSelfStake(List<ECKey> stakers, AionBlockchainImpl chain, Block block, BigInteger expectedStake) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        for (ECKey key : stakers) {
            AionAddress address = new AionAddress(key.getAddress());
            assertThat(chain.getStakingContractHelper().getEffectiveStake(address, address, block)).isEqualTo(expectedStake);
        }
    }
}
