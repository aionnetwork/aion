package org.aion.mcf.mine;

import org.aion.base.type.IBlock;

/**
 * Miner Listener interface.
 *
 * @param <BLK>
 */
public interface IMinerListener<BLK extends IBlock<?, ?>> {

    void miningStarted();

    void miningStopped();

    void blockMiningStarted(BLK block);

    void blockMined(BLK block);

    void blockMiningCanceled(BLK block);
}
