package org.aion.mcf.blockchain;

import org.aion.mcf.mine.IMineRunner;

/** Chain instance pow interface. */
public interface IChainInstancePOW extends IChainInstanceBase {

    IPowChain<?, ?> getBlockchain();

    IMineRunner getBlockMiner();
}
