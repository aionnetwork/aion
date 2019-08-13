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

    IDifficultyCalculator getUnityDifficultyCalculator();

    IRewardsCalculator getRewardsCalculator();

    BlockHeaderValidator createBlockHeaderValidator();

    ParentBlockHeaderValidator createSealParentBlockHeaderValidator();

    ParentBlockHeaderValidator createChainParentBlockHeaderValidator();
}
