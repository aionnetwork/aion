package org.aion.zero.impl.types;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.generateAccounts;
import static org.aion.zero.impl.types.MiningBlockHeader.NONCE_LENGTH;
import static org.aion.zero.impl.types.MiningBlockHeader.SOLUTIONSIZE;

import java.util.ArrayList;
import java.util.List;
import org.aion.base.TransactionTypeRule;
import org.aion.crypto.ECKey;
import org.aion.mcf.blockchain.BlockHeader.Seal;
import org.aion.zero.impl.blockchain.BlockchainTestUtils;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
import org.aion.zero.impl.vm.AvmPathManager;
import org.aion.zero.impl.vm.AvmTestConfig;
import org.aion.zero.impl.vm.TestResourceProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Ensures that the encoding for generate blocks matches the decoding.
 *
 * @author Alexandra Roatis
 */
public class BlockEncodeTest {

    private static TestResourceProvider resourceProvider;
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;
    private List<ECKey> accounts;

    @BeforeClass
    public static void setupAvm() throws Exception {
        resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());
        AvmTestConfig.supportBothAvmVersions(0, 2, 0);
    }

    @AfterClass
    public static void tearDownAvm() throws Exception {
        AvmTestConfig.clearConfigurations();
        resourceProvider.close();
    }

    @Before
    public void setup() {
        accounts = generateAccounts(10);
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts(accounts)
                        .withValidatorConfiguration("simple")
                        .withAvmEnabled()
                        .build();

        this.blockchain = bundle.bc;
        this.deployerKey = bundle.privateKeys.get(0);
        TransactionTypeRule.allowAVMContractTransaction();
    }

    @After
    public void tearDown() {
        this.blockchain = null;
        this.deployerKey = null;
    }

    @Test
    public void generateFirstStakeBlockAndCheckEncoding() {
        long unityForkNumber = 2L;
        blockchain.forkUtility.enableUnityFork(unityForkNumber);
        List<ECKey> txAccounts = new ArrayList<>(accounts);
        txAccounts.remove(deployerKey);

        // populating the chain to be above the Unity fork point
        BlockchainTestUtils.generateRandomUnityChain(blockchain, resourceProvider, unityForkNumber + 1, 1, accounts, deployerKey, 60);

        // get the top block from the generated chain
        StakingBlock stakingBlock = (StakingBlock) blockchain.getBestBlock();
        assertThat(stakingBlock.getHeader().getSealType()).isEqualTo(Seal.PROOF_OF_STAKE);

        // used to check the encode/decode correctness
        StakingBlock decodedBlock = (StakingBlock) BlockUtil.newBlockFromRlp(stakingBlock.getEncoded());
        assertThat(decodedBlock).isNotNull();

        // verify header equality
        StakingBlockHeader expected = stakingBlock.getHeader();
        StakingBlockHeader actual = decodedBlock.getHeader();
        assertThat(actual.getHash()).isEqualTo(expected.getHash());
        assertThat(actual.getParentHash()).isEqualTo(expected.getParentHash());
        assertThat(actual.getNumber()).isEqualTo(expected.getNumber());
        assertThat(actual.getSeedOrProof()).isEqualTo(expected.getSeedOrProof());
        assertThat(actual.getSignature()).isEqualTo(expected.getSignature());
        assertThat(actual.getSigningPublicKey()).isEqualTo(expected.getSigningPublicKey());
        assertThat(actual.getExtraData()).isEqualTo(expected.getExtraData());
        assertThat(actual.getDifficulty()).isEqualTo(expected.getDifficulty());
        assertThat(actual.getDifficultyBI()).isEqualTo(expected.getDifficultyBI());
        assertThat(actual.getTimestamp()).isEqualTo(expected.getTimestamp());
        assertThat(actual.getEnergyConsumed()).isEqualTo(expected.getEnergyConsumed());
        assertThat(actual.getEnergyLimit()).isEqualTo(expected.getEnergyLimit());
        assertThat(actual.getMineHash()).isEqualTo(expected.getMineHash());
        assertThat(actual.getCoinbase()).isEqualTo(expected.getCoinbase());
        assertThat(actual.getStateRoot()).isEqualTo(expected.getStateRoot());
        assertThat(actual.getTxTrieRoot()).isEqualTo(expected.getTxTrieRoot());
        assertThat(actual.getReceiptsRoot()).isEqualTo(expected.getReceiptsRoot());
        assertThat(actual.getSealType()).isEqualTo(expected.getSealType());
        assertThat(actual.getLogsBloom()).isEqualTo(expected.getLogsBloom());
        assertThat(actual.getEncoded()).isEqualTo(expected.getEncoded());

        // verify body
        assertThat(decodedBlock.getTransactionsList()).isEqualTo(stakingBlock.getTransactionsList());
    }

    @Test
    public void generateFirstMiningBlockAfterUnityAndCheckEncoding() {
        long unityForkNumber = 2L;
        blockchain.forkUtility.enableUnityFork(unityForkNumber);
        List<ECKey> txAccounts = new ArrayList<>(accounts);
        txAccounts.remove(deployerKey);

        // populating the chain to be above the Unity fork point
        BlockchainTestUtils.generateRandomUnityChain(blockchain, resourceProvider, unityForkNumber + 2, 1, accounts, deployerKey, 60);

        // get the top block from the generated chain
        MiningBlock miningBlock = (MiningBlock) blockchain.getBestBlock();
        assertThat(miningBlock.getHeader().getSealType()).isEqualTo(Seal.PROOF_OF_WORK);

        byte[] solution = new byte[SOLUTIONSIZE];
        solution[0] = (byte) 6;
        byte[] nonce = new byte[NONCE_LENGTH];
        nonce[0] = (byte) 6;
        // updating the block with a non-empty solution
        // since the testing setup does not add the nonce and solution to the block
        miningBlock.seal(nonce, solution);

        // used to check the encode/decode correctness
        MiningBlock decodedBlock = (MiningBlock) BlockUtil.newBlockFromRlp(miningBlock.getEncoded());
        assertThat(decodedBlock).isNotNull();

        // verify header equality
        MiningBlockHeader expected = miningBlock.getHeader();
        MiningBlockHeader actual = decodedBlock.getHeader();
        assertThat(actual.getHash()).isEqualTo(expected.getHash());
        assertThat(actual.getParentHash()).isEqualTo(expected.getParentHash());
        assertThat(actual.getNumber()).isEqualTo(expected.getNumber());
        assertThat(actual.getNonce()).isEqualTo(expected.getNonce());
        assertThat(actual.getSolution()).isEqualTo(expected.getSolution());
        assertThat(actual.getPowBoundaryBI()).isEqualTo(expected.getPowBoundaryBI());
        assertThat(actual.getExtraData()).isEqualTo(expected.getExtraData());
        assertThat(actual.getDifficulty()).isEqualTo(expected.getDifficulty());
        assertThat(actual.getDifficultyBI()).isEqualTo(expected.getDifficultyBI());
        assertThat(actual.getTimestamp()).isEqualTo(expected.getTimestamp());
        assertThat(actual.getEnergyConsumed()).isEqualTo(expected.getEnergyConsumed());
        assertThat(actual.getEnergyLimit()).isEqualTo(expected.getEnergyLimit());
        assertThat(actual.getMineHash()).isEqualTo(expected.getMineHash());
        assertThat(actual.getCoinbase()).isEqualTo(expected.getCoinbase());
        assertThat(actual.getStateRoot()).isEqualTo(expected.getStateRoot());
        assertThat(actual.getTxTrieRoot()).isEqualTo(expected.getTxTrieRoot());
        assertThat(actual.getReceiptsRoot()).isEqualTo(expected.getReceiptsRoot());
        assertThat(actual.getSealType()).isEqualTo(expected.getSealType());
        assertThat(actual.getLogsBloom()).isEqualTo(expected.getLogsBloom());
        assertThat(actual.getEncoded()).isEqualTo(expected.getEncoded());

        // verify body
        assertThat(decodedBlock.getTransactionsList()).isEqualTo(miningBlock.getTransactionsList());
    }
}
