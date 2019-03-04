package org.aion.mcf.blockchain;

import org.aion.interfaces.block.Block;
import org.aion.interfaces.tx.Transaction;
import org.aion.mcf.core.IDifficultyCalculator;
import org.aion.mcf.core.IRewardsCalculator;
import org.aion.mcf.valid.BlockHeaderValidator;
import org.aion.mcf.valid.ParentBlockHeaderValidator;

/** Chain configuration interface. */
public interface IChainCfg<Blk extends Block<?, ?>, Tx extends Transaction> {

    boolean acceptTransactionSignature(Tx tx);

    IBlockConstants getConstants();

    IBlockConstants getCommonConstants();

    IDifficultyCalculator getDifficultyCalculator();

    IRewardsCalculator getRewardsCalculator();

    BlockHeaderValidator createBlockHeaderValidator();

    ParentBlockHeaderValidator createParentHeaderValidator();
}
