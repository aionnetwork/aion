package org.aion.mcf.mine;

import com.google.common.util.concurrent.ListenableFuture;
import org.aion.base.type.IBlock;
import org.aion.mcf.types.AbstractBlockHeader;

/**
 * Miner interface.
 *
 * @param <Blk>
 * @param <BH>
 */
public interface IMiner<Blk extends IBlock<?, ?>, BH extends AbstractBlockHeader> {

    ListenableFuture<Long> mine(Blk block);

    boolean validate(BH blockHeader);
}
