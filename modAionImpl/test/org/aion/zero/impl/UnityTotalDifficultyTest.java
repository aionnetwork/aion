package org.aion.zero.impl;

import org.aion.crypto.ECKey;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.exceptions.HeaderStructureException;
import org.aion.stake.GenesisStakingBlock;
import org.aion.types.AionAddress;
import org.aion.util.string.StringUtils;
import org.aion.vm.LongLivedAvm;
import org.aion.zero.impl.blockchain.StakingContractHelper;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.StakingBlock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;
import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class UnityTotalDifficultyTest {

    private ECKey key;

    @Mock
    StakingContractHelper stakingContractHelper;
    StandaloneBlockchain bc;
    private final double delta = 5.396596843662725E9;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        LongLivedAvm.createAndStartLongLivedAvm();
        doReturn(1L).when(stakingContractHelper).callGetVote(any(AionAddress.class));
        key = new ECKeyEd25519().fromPrivate(
                StringUtils.StringHexToByteArray("0x042aea49b522407c0fadf19c184dc2d78b233c81b3951c0839967993d755dfde431efa65e0967765eaa2dc31f45e5df2f1a34751cf6e4ae0b6f10b7ee899094c"));

        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts().build();

        bc = spy(bundle.bc);
        doReturn(stakingContractHelper).when(bc).getStakingContractHelper();
    }

    @After
    public void shutdown() {
        LongLivedAvm.destroy();
    }

    /**
     * Generates POW and multiple POS blocks and validates the difficulties
     */
    @Test
    public void testImportPOWMultiPOS() {
        long time = System.currentTimeMillis();

        Block genesis = bc.getBestBlock();

        Block blockOnePOW = bc.createNewBlock(genesis, Collections.emptyList(), true);

        ImportResult result = bc.tryToConnect(blockOnePOW);
        assertThat(bc.getBestBlock() == blockOnePOW).isTrue();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        Block blockInfoPOW = bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockOnePOW.getHash());

        // parent.getNumber() == 0L || parent.isGenesis() -> get from genesis
        Assert.assertEquals(genesis.getMiningDifficulty(), blockInfoPOW.getDifficultyBI());
        Assert.assertEquals(genesis.getMiningDifficulty().add(blockInfoPOW.getDifficultyBI()), blockInfoPOW.getMiningDifficulty());
        Assert.assertEquals(genesis.getStakingDifficulty(), blockInfoPOW.getStakingDifficulty());
        Assert.assertEquals(blockInfoPOW.getMiningDifficulty().multiply(genesis.getStakingDifficulty()), blockInfoPOW.getCumulativeDifficulty());
        
        bc.setUnityForkNumber(2);

        StakingBlock blockTwoPOS = createNewStakingBlock(blockOnePOW, new byte[64]);
        result = bc.tryToConnect(blockTwoPOS);

        assertThat(bc.getBestBlock() == blockTwoPOS).isTrue();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        Block blockInfoPOS = bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockTwoPOS.getHash());
        Assert.assertTrue(blockInfoPOS.getCumulativeDifficulty().compareTo(genesis.getCumulativeDifficulty()) > 0);

        // parent.getNumber() == 0L || parent.isGenesis() -> get from genesis
        Assert.assertEquals(GenesisStakingBlock.getGenesisDifficulty(), blockInfoPOS.getDifficultyBI());
        // mining difficulty from parent
        Assert.assertEquals(blockInfoPOW.getMiningDifficulty(), blockInfoPOS.getMiningDifficulty());
        // staking is genesis + block's difficulty
        Assert.assertEquals(GenesisStakingBlock.getGenesisDifficulty().add(blockInfoPOS.getDifficultyBI()), blockInfoPOS.getStakingDifficulty());
        Assert.assertEquals(blockInfoPOS.getMiningDifficulty().multiply(blockInfoPOS.getStakingDifficulty()), blockInfoPOS.getCumulativeDifficulty());

        StakingBlock blockThreePOS = createNewStakingBlock(blockTwoPOS, blockTwoPOS.getSeed());
        blockThreePOS.getHeader().setTimestamp(time += delta);

        result = bc.tryToConnectInternal(blockThreePOS, time);

        assertThat(bc.getBestBlock() == blockThreePOS).isTrue();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        BigInteger blockOnePOSStakingDifficulty = blockInfoPOS.getStakingDifficulty();

        // calculated through StakeBlockDiffCalculator
        BigInteger expectedBlockDifficulty = BigInteger.valueOf(2000000000);

        blockInfoPOS = bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockThreePOS.getHash());

        Assert.assertEquals(expectedBlockDifficulty, blockInfoPOS.getDifficultyBI());
        // mining difficulty from parent
        Assert.assertEquals(blockInfoPOW.getMiningDifficulty(), blockInfoPOS.getMiningDifficulty());
        // staking difficulty is parent difficulty + current block's difficulty since it's a POS block
        Assert.assertEquals(blockOnePOSStakingDifficulty.add(blockInfoPOS.getDifficultyBI()), blockInfoPOS.getStakingDifficulty());
    }

    /**
     * Generates POS and multiple POW blocks and validates the difficulties
     */
    @Test
    public void testPOSMultiPOW() {
        bc.setUnityForkNumber(1);

        Block parent = bc.getBestBlock();
        StakingBlock blockOnePOS = createNewStakingBlock(parent, new byte[64]);
        ImportResult result = bc.tryToConnect(blockOnePOS);
        assertThat(bc.getBestBlock() == blockOnePOS).isTrue();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        Block blockOnePOSInfo = bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockOnePOS.getHash());

        // parent.getNumber() == 0L || parent.isGenesis() -> get from genesis
        Assert.assertEquals(GenesisStakingBlock.getGenesisDifficulty(), blockOnePOSInfo.getDifficultyBI());
        // mining difficulty from parent
        Assert.assertEquals(parent.getMiningDifficulty(), blockOnePOSInfo.getMiningDifficulty());
        // staking difficulty is genesis + current block's difficulty
        Assert.assertEquals(GenesisStakingBlock.getGenesisDifficulty().add(blockOnePOSInfo.getDifficultyBI()), blockOnePOSInfo.getStakingDifficulty());
        Assert.assertEquals(blockOnePOSInfo.getMiningDifficulty().multiply(blockOnePOSInfo.getStakingDifficulty()), blockOnePOSInfo.getCumulativeDifficulty());

        Block blockTwoPOW = bc.createNewBlock(blockOnePOS, Collections.emptyList(), true);
        result = bc.tryToConnect(blockTwoPOW);
        assertThat(bc.getBestBlock() == blockTwoPOW).isTrue();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        Block blockTwoPOWInfo = bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockTwoPOW.getHash());

        // from antiparent block (genesis)
        Assert.assertEquals(new BigInteger(AionGenesis.GENESIS_DIFFICULTY), blockTwoPOWInfo.getDifficultyBI());
        // mining difficulty is genesis + current block's difficulty
        Assert.assertEquals(blockTwoPOWInfo.getDifficultyBI().add(new BigInteger(AionGenesis.GENESIS_DIFFICULTY)), blockTwoPOWInfo.getMiningDifficulty());
        // staking difficulty is from parent
        Assert.assertEquals(blockOnePOSInfo.getStakingDifficulty(), blockTwoPOWInfo.getStakingDifficulty());
        Assert.assertEquals(blockTwoPOWInfo.getStakingDifficulty().multiply(blockTwoPOWInfo.getMiningDifficulty()), blockTwoPOWInfo.getCumulativeDifficulty());

        Block blockThreePOW = bc.createNewBlock(blockTwoPOW, Collections.emptyList(), true);
        result = bc.tryToConnect(blockThreePOW);
        assertThat(bc.getBestBlock() == blockThreePOW).isTrue();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        Block blockThreeInfo = bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockThreePOW.getHash());

        // Calculated through DiffCalc
        Assert.assertEquals(BigInteger.valueOf(975), blockThreeInfo.getDifficultyBI());
        // mining difficulty is parent + current block's difficulty
        Assert.assertEquals(blockThreeInfo.getDifficultyBI().add(blockTwoPOWInfo.getMiningDifficulty()), blockThreeInfo.getMiningDifficulty());
        // staking difficulty is from parent
        Assert.assertEquals(blockTwoPOWInfo.getStakingDifficulty(), blockThreeInfo.getStakingDifficulty());
        Assert.assertEquals(blockThreeInfo.getStakingDifficulty().multiply(blockThreeInfo.getMiningDifficulty()), blockThreeInfo.getCumulativeDifficulty());
    }

    /**
     * Tests the case where the mainchain switches because of a higher difficulty POS block
     */
    @Test
    public void testHigherDifficultyPOSFork() {

        long time = System.currentTimeMillis();

        Block blockOnePOW = bc.createNewBlockInternal(bc.getGenesis(), Collections.emptyList(), true, time / 1000L).block;
        assertThat(bc.tryToConnectInternal(blockOnePOW, (time += 100))).isEqualTo(ImportResult.IMPORTED_BEST);

        Block blockTwoPOW = bc.createNewBlockInternal(blockOnePOW, Collections.emptyList(), true, time / 1000L).block;
        assertThat(bc.tryToConnectInternal(blockTwoPOW, time += 100)).isEqualTo(ImportResult.IMPORTED_BEST);

        Block blockThreePOW = bc.createNewBlockInternal(blockTwoPOW, Collections.emptyList(), true, time / 1000L).block;
        assertThat(bc.tryToConnectInternal(blockThreePOW, time += 100)).isEqualTo(ImportResult.IMPORTED_BEST);

        // now we diverge with one block having higher TD than the other
        
        bc.setUnityForkNumber(2);
        StakingBlock blockTwoPOS = createNewStakingBlock(blockOnePOW, new byte[64]);

        assertThat(bc.tryToConnectInternal(blockTwoPOS, time += 10)).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(bc.getBestBlock() == blockTwoPOS).isTrue();

        Block blockInfoPOS = bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockTwoPOS.getHash());
        Assert.assertEquals(GenesisStakingBlock.getGenesisDifficulty(), blockInfoPOS.getDifficultyBI());
        Assert.assertEquals(bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockOnePOW.getHash()).getMiningDifficulty(), blockInfoPOS.getMiningDifficulty());
        Assert.assertEquals(GenesisStakingBlock.getGenesisDifficulty().add(blockInfoPOS.getDifficultyBI()), blockInfoPOS.getStakingDifficulty());
        Assert.assertEquals(blockInfoPOS.getMiningDifficulty().multiply(blockInfoPOS.getStakingDifficulty()), blockInfoPOS.getCumulativeDifficulty());

        StakingBlock blockThreePOS = createNewStakingBlock(blockTwoPOS, blockTwoPOS.getSeed());
        blockThreePOS.getHeader().setTimestamp(time += delta);

        assertThat(bc.tryToConnectInternal(blockThreePOS, time)).isEqualTo(ImportResult.IMPORTED_BEST);
    }

    /**
     * Tests the case where the mainchain reorganizes multiple times
     */
    @Test
    @Ignore
    // This test relies on an explosion in difficulty happening when the first PoS block is added
    // Due to a change in Unity implementation, this explosion no longer happens
    // This test is hence @Ignored until we can simulate its desired behaviour more accurately
    public void testHigherDifficultySwitchMultipleTimes() {

        long time = System.currentTimeMillis();

        Block blockOnePOW = bc.createNewBlockInternal(bc.getGenesis(), Collections.emptyList(), true, time / 1000L).block;
        assertThat(bc.tryToConnectInternal(blockOnePOW, (time += 100))).isEqualTo(ImportResult.IMPORTED_BEST);

        Block blockTwoPOW_Chain1 = bc.createNewBlockInternal(blockOnePOW, Collections.emptyList(), true, time / 1000L).block;
        assertThat(bc.tryToConnectInternal(blockTwoPOW_Chain1, time += 100)).isEqualTo(ImportResult.IMPORTED_BEST);

        Block blockThreePOW_Chain1 = bc.createNewBlockInternal(blockTwoPOW_Chain1, Collections.emptyList(), true, time / 1000L).block;
        assertThat(bc.tryToConnectInternal(blockThreePOW_Chain1, time += 100)).isEqualTo(ImportResult.IMPORTED_BEST);

        // now we diverge with one block having higher TD than the other
        StakingBlock blockTwoPOS_Chain2 = createNewStakingBlock(blockOnePOW, new byte[64]);

        assertThat(bc.tryToConnectInternal(blockTwoPOS_Chain2, time += 10)).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(bc.getBestBlock() == blockTwoPOS_Chain2).isTrue();

        // current block's mining difficulty equals parent's mining difficulty, which is the same as as cumulative difficulty before any POS has been generated
        Assert.assertEquals(
                blockOnePOW.getCumulativeDifficulty().multiply(GenesisStakingBlock.getGenesisDifficulty().add(blockTwoPOS_Chain2.getDifficultyBI())),
                blockTwoPOS_Chain2.getCumulativeDifficulty());

        // we diverge again with one block having higher TD than the other
        StakingBlock blockFourPOS_Chain1 = createNewStakingBlock(blockThreePOW_Chain1, new byte[64]);
        blockFourPOS_Chain1.getHeader().setTimestamp(time += delta);

        assertThat(bc.tryToConnectInternal(blockFourPOS_Chain1, time)).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(bc.getBestBlock() == blockFourPOS_Chain1).isTrue();

        // current block's mining difficulty equals parent's total mining difficulty, which is the same as as cumulative difficulty before any POS block has been generated
        Assert.assertEquals(
                blockThreePOW_Chain1.getCumulativeDifficulty().multiply(GenesisStakingBlock.getGenesisDifficulty().add(blockFourPOS_Chain1.getDifficultyBI())),
                blockFourPOS_Chain1.getCumulativeDifficulty());

        StakingBlock blockThreePOS_Chain2 = createNewStakingBlock(blockTwoPOS_Chain2, blockTwoPOS_Chain2.getSeed());
        blockThreePOS_Chain2.getHeader().setTimestamp(time += delta);
        assertThat(bc.tryToConnectInternal(blockThreePOS_Chain2, time)).isEqualTo(ImportResult.IMPORTED_NOT_BEST);

        // Calculated through StakeBlockDiffCalculator
        BigInteger expectedStakingDifficultyBlockThreePOS = BigInteger.valueOf(2000000000);

        Block blockTwoPOS_Chain2_Info = bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockTwoPOS_Chain2.getHash());
        Assert.assertEquals(
                blockTwoPOS_Chain2_Info.getMiningDifficulty().multiply(
                        blockTwoPOS_Chain2_Info.getStakingDifficulty().add(expectedStakingDifficultyBlockThreePOS)),
                blockThreePOS_Chain2.getCumulativeDifficulty());

        double newDelta = 1.4664738411858124E10;
        StakingBlock blockFourPOS_Chain2 = createNewStakingBlock(blockThreePOS_Chain2, blockThreePOS_Chain2.getSeed());
        blockFourPOS_Chain2.getHeader().setTimestamp(time += newDelta);
        assertThat(bc.tryToConnectInternal(blockFourPOS_Chain2, time)).isEqualTo(ImportResult.IMPORTED_BEST);
    }

    /**
     * This tests mocks the difficulty and hash for genesis staking block so that the calculated cumulative difficulty
     * for next generated POS and POW blocks are equal. The first block import will be successful.
     *
     * @throws HeaderStructureException
     */
    @Test
    public void testSamePOSPOWDifficulty() throws HeaderStructureException {
        long time = System.currentTimeMillis();

        Block genesis = bc.getGenesis();
        bc.setTotalStakingDifficulty(genesis.getDifficultyBI());

        GenesisStakingBlock genesisStakingBlock = spy(new GenesisStakingBlock(null));
        BlockHeader genesisBlockHeader = genesisStakingBlock.getHeader();
        genesisBlockHeader.setDifficulty(BigInteger.valueOf(2047).toByteArray());
        genesis.setAntiparentHash(genesisStakingBlock.getHash());
        doReturn(genesis.getDifficultyBI()).when(genesisStakingBlock).getStakingDifficulty();
        doReturn(genesisBlockHeader).when(genesisStakingBlock).getHeader();

        CfgAion cfgAion = spy(CfgAion.class);
        doReturn(genesisStakingBlock).when(cfgAion).getGenesisStakingBlock();

        CfgAion.setInst(cfgAion);
        
        bc.setUnityForkNumber(1);

        Block blockOnePOW = bc.createNewBlockInternal(genesis, Collections.emptyList(), true, time / 1000L).block;
        StakingBlock blockOnePOS = createNewStakingBlock(genesis, new byte[64]);

        assertThat(bc.tryToConnectInternal(blockOnePOW, time += 10)).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(bc.tryToConnectInternal(blockOnePOS, time)).isEqualTo(ImportResult.IMPORTED_NOT_BEST);
        assertThat(bc.getBestBlock() == blockOnePOW).isTrue();

        Mockito.reset(cfgAion);
    }

    private StakingBlock createNewStakingBlock(Block parent, byte[] parentSeed) {
        byte[] seedBlockOne = key.sign(parentSeed).getSignature();
        StakingBlock blockOnePOS = (StakingBlock) bc.createNewBlock(parent, Collections.emptyList(), true, seedBlockOne);
        blockOnePOS.getHeader().setPubKey(key.getPubKey());
        byte[] mineHashSig = key.sign(blockOnePOS.getHeader().getMineHash()).getSignature();
        blockOnePOS.getHeader().setSignature(mineHashSig);
        return blockOnePOS;
    }
}
