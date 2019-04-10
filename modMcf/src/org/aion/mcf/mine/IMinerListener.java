package org.aion.mcf.mine;

import org.aion.interfaces.block.Block;

/**
 * Miner Listener interface.
 *
 * @param <BLK>
 */
public interface IMinerListener<BLK extends Block<?, ?>> {

    void miningStarted();

    void miningStopped();

    void blockMiningStarted(BLK block);

    void blockMined(BLK block);

    void blockMiningCanceled(BLK block);
}
