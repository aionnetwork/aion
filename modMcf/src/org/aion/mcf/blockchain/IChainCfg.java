package org.aion.mcf.blockchain;

import org.aion.base.AionTransaction;
import org.aion.mcf.core.IDifficultyCalculator;
import org.aion.mcf.core.IRewardsCalculator;
import org.aion.mcf.valid.BlockHeaderValidator;
import org.aion.mcf.valid.ParentBlockHeaderValidator;

/** Chain configuration interface. */
public interface IChainCfg {

    boolean acceptTransactionSignature(AionTransaction tx);

    IBlockConstants getConstants();

    IBlockConstants getCommonConstants();

    IDifficultyCalculator getDifficultyCalculator();

    IRewardsCalculator getRewardsCalculator();

    BlockHeaderValidator createBlockHeaderValidator();

    ParentBlockHeaderValidator createMiningParentHeaderValidator();

    // TODO: [unity] separate these methods from the ChainCfg impl class.

    IDifficultyCalculator getUnityDifficultyCalculator();

    BlockHeaderValidator createStakingBlockHeaderValidator();

    ParentBlockHeaderValidator createStakingParentHeaderValidator();

    ParentBlockHeaderValidator createChainHeaderValidator();

    ParentBlockHeaderValidator createBlockParentHeaderValidator();
}
