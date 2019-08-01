package org.aion.mcf.blockchain;

import org.aion.mcf.mine.IMineRunner;
import org.aion.mcf.stake.StakeRunnerInterface;

/** Chain instance pow interface. */
public interface IChainInstancePOW extends IChainInstanceBase {

    UnityChain getBlockchain();

    IMineRunner getBlockMiner();

    StakeRunnerInterface getStakeRunner();
}
