package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.Collections;
import org.aion.crypto.ECKey;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.core.ImportResult;
import org.aion.stake.GenesisStakingBlock;
import org.aion.types.AionAddress;
import org.aion.util.string.StringUtils;
import org.aion.vm.LongLivedAvm;
import org.aion.zero.impl.blockchain.StakingContractHelper;
import org.aion.zero.impl.types.StakingBlock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class UnityHardForkTest {

    private ECKey key;

    @Mock StakingContractHelper stakingContractHelper;
    StandaloneBlockchain bc;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        LongLivedAvm.createAndStartLongLivedAvm();
        doReturn(1L).when(stakingContractHelper).callGetVote(any(AionAddress.class));
        key =
                new ECKeyEd25519()
                        .fromPrivate(
                                StringUtils.StringHexToByteArray(
                                        "0x042aea49b522407c0fadf19c184dc2d78b233c81b3951c0839967993d755dfde431efa65e0967765eaa2dc31f45e5df2f1a34751cf6e4ae0b6f10b7ee899094c"));

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

    @Test
    public void testBlockUnityHardFork() {

        Block genesis = bc.getBestBlock();
        Block blockOnePOW = bc.createNewMiningBlock(genesis, Collections.emptyList(), true);

        ImportResult result = bc.tryToConnect(blockOnePOW);
        assertThat(bc.getBestBlock() == blockOnePOW).isTrue();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        Block blockOneInfoPOW =
                bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockOnePOW.getHash());

        Assert.assertEquals(genesis.getMiningDifficulty(), blockOneInfoPOW.getDifficultyBI());
        Assert.assertEquals(
                genesis.getMiningDifficulty().add(blockOneInfoPOW.getDifficultyBI()),
                blockOneInfoPOW.getMiningDifficulty());
        Assert.assertEquals(genesis.getStakingDifficulty(), blockOneInfoPOW.getStakingDifficulty());
        Assert.assertEquals(
                blockOneInfoPOW.getMiningDifficulty().multiply(genesis.getStakingDifficulty()),
                blockOneInfoPOW.getCumulativeDifficulty());

        Block blockTwoPOW = bc.createNewMiningBlock(blockOneInfoPOW, Collections.emptyList(), true);
        result = bc.tryToConnect(blockTwoPOW);
        assertThat(bc.getBestBlock() == blockTwoPOW).isTrue();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        Block blockTwoInfoPOW =
                bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockTwoPOW.getHash());

        Assert.assertEquals(925, blockTwoInfoPOW.getDifficultyBI().intValue());
        Assert.assertEquals(
                blockOnePOW.getCumulativeDifficulty().add(blockTwoInfoPOW.getDifficultyBI()),
                blockTwoInfoPOW.getMiningDifficulty());
        Assert.assertEquals(genesis.getStakingDifficulty(), blockTwoInfoPOW.getStakingDifficulty());
        Assert.assertEquals(
                blockTwoInfoPOW
                        .getMiningDifficulty()
                        .multiply(blockOneInfoPOW.getStakingDifficulty()),
                blockTwoInfoPOW.getCumulativeDifficulty());

        StakingBlock blockThreePOS = createNewStakingBlock(blockTwoInfoPOW, new byte[64]);
        assertNull(blockThreePOS);

        bc.setUnityForkNumber(3);

        blockThreePOS = createNewStakingBlock(blockTwoInfoPOW, new byte[64]);
        assertNotNull(blockThreePOS);

        result = bc.tryToConnect(blockThreePOS);

        assertThat(bc.getBestBlock() == blockThreePOS).isTrue();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        Block blockThreeInfoPOS =
                bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockThreePOS.getHash());
        Assert.assertEquals(
                GenesisStakingBlock.getGenesisDifficulty(),
                blockThreeInfoPOS.getHeader().getDifficultyBI());
        Assert.assertEquals(
                GenesisStakingBlock.getGenesisDifficulty().add(blockThreeInfoPOS.getDifficultyBI()),
                blockThreeInfoPOS.getStakingDifficulty());

        Assert.assertTrue(
                blockThreeInfoPOS
                                .getCumulativeDifficulty()
                                .compareTo(blockTwoInfoPOW.getCumulativeDifficulty())
                        > 0);
    }

    private StakingBlock createNewStakingBlock(Block parent, byte[] parentSeed) {
        byte[] seedBlockOne = key.sign(parentSeed).getSignature();
        StakingBlock blockOnePOS =
                bc.createNewStakingBlock(parent, Collections.emptyList(), seedBlockOne);

        if (blockOnePOS == null) {
            return null;
        }

        blockOnePOS.getHeader().setPubKey(key.getPubKey());
        byte[] mineHashSig = key.sign(blockOnePOS.getHeader().getMineHash()).getSignature();
        blockOnePOS.getHeader().setSignature(mineHashSig);
        return blockOnePOS;
    }
}
