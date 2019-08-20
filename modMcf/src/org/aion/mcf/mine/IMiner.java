package org.aion.mcf.mine;

import com.google.common.util.concurrent.ListenableFuture;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader;

/**
 * Miner interface.
 *
 * @param <Blk>
 * @param <BH>
 */
public interface IMiner<Blk extends Block, BH extends BlockHeader> {

    ListenableFuture<Long> mine(Blk block);

    boolean validate(BH blockHeader);
}
