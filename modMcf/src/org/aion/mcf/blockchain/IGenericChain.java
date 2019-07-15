package org.aion.mcf.blockchain;

import org.aion.mcf.core.AbstractTxInfo;
import org.aion.mcf.db.IBlockStoreBase;

/** Generic Chain interface. */
public interface IGenericChain {

    Block getBlockByNumber(long number);

    Block getBlockByHash(byte[] hash);

    IBlockStoreBase getBlockStore();

    Block getBestBlock();

    AbstractTxInfo getTransactionInfo(byte[] hash);

    void flush();
}
