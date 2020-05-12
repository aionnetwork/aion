package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.MIN_SELF_STAKE;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.generateAccounts;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.base.TransactionTypeRule;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.zero.impl.types.Block;
import org.aion.types.AionAddress;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.AvmPathManager;
import org.aion.zero.impl.vm.AvmTestConfig;
import org.aion.zero.impl.vm.TestResourceProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SignatureSchemeSwapForkTest {

    private List<ECKey> accounts;
    private ECKey stakingRegistryOwner;
    private List<ECKey> stakers;

    @Before
    public void setup() {
        // reduce default logging levels
        Map<LogEnum, LogLevel> cfg = new HashMap<>();
        cfg.put(LogEnum.TX, LogLevel.DEBUG);
        cfg.put(LogEnum.VM, LogLevel.DEBUG);
        // enable CONS->DEBUG to see messages from staking contract helper
        // cfg.put(LogEnum.CONS, LogLevel.DEBUG);
        AionLoggerFactory.initAll(cfg);

        AvmTestConfig.supportOnlyAvmVersion1();

        // setup accounts
        accounts = generateAccounts(10);
        stakingRegistryOwner = accounts.get(0);
        stakers = List.of(accounts.get(1), accounts.get(2), accounts.get(3));
    }

    @After
    public void tearDown() {
        AvmTestConfig.clearConfigurations();
    }

    @Test
    public void testSigatureSchemeSwapFork()
        throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {

        // setup Unity fork and AVM
        long unityForkBlock = 2;
        long signatureSwapForkBlockHeight = unityForkBlock + 3;

        setupAVM(unityForkBlock);

        TestResourceProvider resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(
            AvmPathManager.getPathOfProjectRootDirectory());

        // setup an identical blockchains
        StandaloneBlockchain blockchain = setupIdenticalBlockchain(unityForkBlock, signatureSwapForkBlockHeight);

        // create block with staker registry
        Block blockWithRegistry = BlockchainTestUtils.generateNextMiningBlockWithStakerRegistry(blockchain, blockchain.getGenesis(), resourceProvider, stakingRegistryOwner);
        // import block on firstChain
        Pair<ImportResult, AionBlockSummary> result = blockchain.tryToConnectAndFetchSummary(blockWithRegistry);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(result.getRight().getReceipts().get(0).isSuccessful()).isTrue();
        assertThat(result.getRight().getReceipts().get(0).getLogInfoList()).isNotEmpty();
        assertThat(result.getRight().getReceipts().get(0).getEnergyUsed()).isEqualTo(1_225_655L);

        // set the staking contract address in the staking genesis
        AionTransaction deploy = blockWithRegistry.getTransactionsList().get(0);
        AionAddress contract = TxUtil
            .calculateContractAddress(deploy.getSenderAddress().toByteArray(), deploy.getNonceBI());
        blockchain.getGenesis().setStakingContractAddress(contract);

        // create Unity block with stakers
        Block block2Unity = BlockchainTestUtils.generateNextMiningBlockWithStakers(blockchain, blockchain.getBestBlock(), resourceProvider, stakers, MIN_SELF_STAKE);
        // import block2Unity on blockchain
        result = blockchain.tryToConnectAndFetchSummary(block2Unity);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        verifyReceipts(result.getRight().getReceipts(), 3, true);

        // create staking block
        Block block3Staking = BlockchainTestUtils.generateNextStakingBlock(blockchain, blockchain.getBestBlock(),
            Collections.emptyList(), stakers.get(0));
        assertThat(block3Staking).isNotNull();
        // import block3Staking on blockchain
        result = blockchain.tryToConnectAndFetchSummary(block3Staking);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);

        // create next mining block
        Block block4Mining = BlockchainTestUtils.generateNextMiningBlock(blockchain, blockchain.getBestBlock(), Collections.emptyList());
        // import block4Mining on blockchain
        result = blockchain.tryToConnectAndFetchSummary(block4Mining);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);

        // create the first signatureSchemeSwap block
        Block block5SignatureSchemeSwapStaking = BlockchainTestUtils.generateNextStakingBlock(blockchain, blockchain.getBestBlock(),
            Collections.emptyList(), stakers.get(0));
        assertThat(block5SignatureSchemeSwapStaking).isNotNull();
        // import block5SignatureSchemeSwapStaking on blockchain
        result = blockchain.tryToConnectAndFetchSummary(block5SignatureSchemeSwapStaking);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);

        // create next mining block
        Block block6Mining = BlockchainTestUtils.generateNextMiningBlock(blockchain, blockchain.getBestBlock(), Collections.emptyList());
        // import block6Mining on blockchain
        result = blockchain.tryToConnectAndFetchSummary(block6Mining);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);

        // create next staking block
        Block block7Staking = BlockchainTestUtils.generateNextStakingBlock(blockchain, blockchain.getBestBlock(),
            Collections.emptyList(), stakers.get(0));
        // import block7Staking on blockchain
        result = blockchain.tryToConnectAndFetchSummary(block7Staking);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
    }

    private StandaloneBlockchain setupIdenticalBlockchain(long unityForkBlock,
        long signatureSwapForkBlockHeight) {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain blockchain = builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).withAvmEnabled().build().bc;
        blockchain.forkUtility.enableUnityFork(unityForkBlock);
        // the unity fork number set to the PoW block and fork at the next block (staking block).
        // the signatureSchemeSwap fork set fork to and exactly happens on the Pow block.
        blockchain.forkUtility.enableSignatureSwapFork(signatureSwapForkBlockHeight);

        return  blockchain;
    }

    private static void setupAVM(long unityForkBlock) {
        AvmTestConfig.clearConfigurations(); // clear setting from @Before
        TransactionTypeRule.allowAVMContractTransaction();
        AvmTestConfig.supportBothAvmVersions(0, unityForkBlock, 0); // enable both AVMs without overlap
    }

    private void verifyReceipts(List<AionTxReceipt> stakerRegistrationReceipts, int expectedSize, boolean checkEnergy) {
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

    @Test
    public void testRejectFvmContractDeploy()
        throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {

        // setup Unity fork and AVM
        long unityForkBlock = 2;
        long signatureSwapForkBlockHeight = unityForkBlock + 3;

        setupAVM(unityForkBlock);

        TestResourceProvider resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(
            AvmPathManager.getPathOfProjectRootDirectory());

        // setup an identical blockchains
        StandaloneBlockchain blockchain = setupIdenticalBlockchain(unityForkBlock, signatureSwapForkBlockHeight);

        // create block with staker registry
        Block blockWithRegistry = BlockchainTestUtils.generateNextMiningBlockWithStakerRegistry(blockchain, blockchain.getGenesis(), resourceProvider, stakingRegistryOwner);
        // import block on firstChain
        Pair<ImportResult, AionBlockSummary> result = blockchain.tryToConnectAndFetchSummary(blockWithRegistry);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(result.getRight().getReceipts().get(0).isSuccessful()).isTrue();
        assertThat(result.getRight().getReceipts().get(0).getLogInfoList()).isNotEmpty();
        assertThat(result.getRight().getReceipts().get(0).getEnergyUsed()).isEqualTo(1_225_655L);

        // set the staking contract address in the staking genesis
        AionTransaction deploy = blockWithRegistry.getTransactionsList().get(0);
        AionAddress contract = TxUtil
            .calculateContractAddress(deploy.getSenderAddress().toByteArray(), deploy.getNonceBI());
        blockchain.getGenesis().setStakingContractAddress(contract);

        // create Unity block with stakers
        Block block2Unity = BlockchainTestUtils.generateNextMiningBlockWithStakers(blockchain, blockchain.getBestBlock(), resourceProvider, stakers, MIN_SELF_STAKE);
        // import block2Unity on blockchain
        result = blockchain.tryToConnectAndFetchSummary(block2Unity);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        verifyReceipts(result.getRight().getReceipts(), 3, true);

        // create staking block
        Block block3Staking = BlockchainTestUtils.generateNextStakingBlock(blockchain, blockchain.getBestBlock(),
            Collections.emptyList(), stakers.get(0));
        assertThat(block3Staking).isNotNull();
        // import block3Staking on blockchain
        result = blockchain.tryToConnectAndFetchSummary(block3Staking);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);

        // create next mining block
        AionTransaction tx = BlockchainTestUtils.deployFvmTickerContractTransaction(accounts.get(4), BigInteger.ZERO);
        Block block4Mining = BlockchainTestUtils.generateNextMiningBlock(blockchain, blockchain.getBestBlock(), Collections.singletonList(tx));
        assertThat(block4Mining.getTransactionsList().size() == 1).isTrue();
        // import block4Mining on blockchain
        result = blockchain.tryToConnectAndFetchSummary(block4Mining);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);

        // create the first signatureSchemeSwap block
        tx = BlockchainTestUtils.deployFvmTickerContractTransaction(accounts.get(5), BigInteger.ZERO);
        Block block5SignatureSchemeSwapStaking = BlockchainTestUtils.generateNextStakingBlock(blockchain, blockchain.getBestBlock(),
            Collections.singletonList(tx), stakers.get(0));
        assertThat(block5SignatureSchemeSwapStaking).isNotNull();
        assertThat(block5SignatureSchemeSwapStaking.getTransactionsList().size() == 0).isTrue();
        // import block5SignatureSchemeSwapStaking on blockchain
        result = blockchain.tryToConnectAndFetchSummary(block5SignatureSchemeSwapStaking);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);

        // create next mining block with rejected transaction
        Block block6Mining = BlockchainTestUtils.generateNextMiningBlock(blockchain, blockchain.getBestBlock(), Collections.singletonList(tx));
        assertThat(block6Mining.getTransactionsList().size() == 0).isTrue();
    }
}
