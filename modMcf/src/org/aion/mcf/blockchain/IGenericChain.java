package org.aion.mcf.blockchain;

import org.aion.base.type.IBlock;
import org.aion.mcf.core.AbstractTxInfo;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.types.AbstractBlockHeader;

/** Generic Chain interface. */
public interface IGenericChain<BLK extends IBlock, BH extends AbstractBlockHeader> {

    BLK getBlockByNumber(long number);

    BLK getBlockByHash(byte[] hash);

    IBlockStoreBase<?, ?> getBlockStore();

    BLK getBestBlock();

    AbstractTxInfo getTransactionInfo(byte[] hash);

    void flush();
}
