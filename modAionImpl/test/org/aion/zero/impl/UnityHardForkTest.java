package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertNotNull;
import static org.aion.util.types.AddressUtils.ZERO_ADDRESS;
import org.junit.Ignore;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.math.BigInteger;
import java.util.Collections;
import org.aion.crypto.ECKey;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.mcf.blockchain.Block;
import org.aion.types.AionAddress;
import org.aion.util.string.StringUtils;
import org.aion.vm.avm.LongLivedAvm;
import org.aion.zero.impl.blockchain.StakingContractHelper;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.types.GenesisStakingBlock;
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
        doReturn(new BigInteger("10000000000")).when(stakingContractHelper).getEffectiveStake(any(AionAddress.class), any(AionAddress.class));
        doReturn(ZERO_ADDRESS).when(stakingContractHelper).getCoinbaseForSigningAddress(any(AionAddress.class));
        key =
                new ECKeyEd25519()
                        .fromPrivate(
                                StringUtils.StringHexToByteArray(
                                        "0x042aea49b522407c0fadf19c184dc2d78b233c81b3951c0839967993d755dfde431efa65e0967765eaa2dc31f45e5df2f1a34751cf6e4ae0b6f10b7ee899094c"));

        CfgAion.inst().setGenesisForTest();
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts().build();

        bc = spy(bundle.bc);
        doReturn(stakingContractHelper).when(bc).getStakingContractHelper();

    }

    @After
    public void shutdown() {
        LongLivedAvm.destroy();
        bc.setUnityForkNumber(Long.MAX_VALUE);
    }

    @Test
    public void testBlockUnityHardFork() {
        bc.setUnityForkNumber(4);

        Block genesis = bc.getBestBlock();
        Block blockOnePOW = bc.createNewMiningBlock(genesis, Collections.emptyList(), true);

        ImportResult result = bc.tryToConnect(blockOnePOW);
        assertThat(bc.getBestBlock() == blockOnePOW).isTrue();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        Block blockOneInfoPOW =
                bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockOnePOW.getHash());

        Assert.assertEquals(genesis.getDifficultyBI(), blockOneInfoPOW.getDifficultyBI());
        Assert.assertEquals(
                genesis.getDifficultyBI().add(blockOneInfoPOW.getDifficultyBI()),
                blockOneInfoPOW.getTotalDifficulty());

        Block blockTwoPOW = bc.createNewMiningBlock(blockOneInfoPOW, Collections.emptyList(), true);
        result = bc.tryToConnect(blockTwoPOW);
        assertThat(bc.getBestBlock() == blockTwoPOW).isTrue();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        Block blockTwoInfoPOW =
                bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockTwoPOW.getHash());

        Assert.assertEquals(925, blockTwoInfoPOW.getDifficultyBI().intValue());
        Assert.assertEquals(
                blockOnePOW.getTotalDifficulty().add(blockTwoInfoPOW.getDifficultyBI()),
                blockTwoInfoPOW.getTotalDifficulty());

        Block blockThreePOW = bc.createNewMiningBlock(blockTwoInfoPOW, Collections.emptyList(), true);
        result = bc.tryToConnect(blockThreePOW);
        assertThat(bc.getBestBlock() == blockThreePOW).isTrue();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        Block blockThreeInfoPOW =
                bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockThreePOW.getHash());

        Assert.assertEquals(926, blockThreeInfoPOW.getDifficultyBI().intValue());
        Assert.assertEquals(
                blockTwoPOW.getTotalDifficulty().add(blockThreeInfoPOW.getDifficultyBI()),
                blockThreeInfoPOW.getTotalDifficulty());

        StakingBlock blockFourPOS = createNewStakingBlock(blockThreeInfoPOW, new byte[64]);
        assertNotNull(blockFourPOS);

        result = bc.tryToConnect(blockFourPOS);

        assertThat(bc.getBestBlock() == blockFourPOS).isTrue();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        Block blockFourInfoPOS =
                bc.getRepository().getBlockStore().getBlockByHashWithInfo(blockFourPOS.getHash());
        Assert.assertEquals(
                GenesisStakingBlock.getGenesisDifficulty(),
                blockFourInfoPOS.getHeader().getDifficultyBI());
        Assert.assertEquals(
                GenesisStakingBlock.getGenesisDifficulty().add(blockThreeInfoPOW.getTotalDifficulty()),
                blockFourInfoPOS.getTotalDifficulty());

        Assert.assertTrue(
                blockFourInfoPOS
                                .getTotalDifficulty()
                                .compareTo(blockThreeInfoPOW.getTotalDifficulty())
                        > 0);
    }

    private StakingBlock createNewStakingBlock(Block parent, byte[] parentSeed) {
        byte[] seedBlockOne = key.sign(parentSeed).getSignature();
        StakingBlock blockOnePOS =
                bc.createStakingBlockTemplate(Collections.emptyList(), key.getPubKey(), seedBlockOne);

        if (blockOnePOS == null) {
            return null;
        }

        byte[] mineHashSig = key.sign(blockOnePOS.getHeader().getMineHash()).getSignature();
        blockOnePOS.seal(mineHashSig, key.getPubKey());
        return blockOnePOS;
    }
}
