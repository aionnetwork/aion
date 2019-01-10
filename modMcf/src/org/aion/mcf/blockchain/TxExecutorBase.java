package org.aion.mcf.blockchain;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.IBlock;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.types.AbstractTransaction;
import org.aion.mcf.types.AbstractTxReceipt;
import org.slf4j.Logger;

/** Transaction executor base class. */
public abstract class TxExecutorBase<
        BLK extends IBlock<?, ?>,
        TX extends AbstractTransaction,
        BS extends IBlockStoreBase<?, ?>,
        TR extends AbstractTxReceipt<?>> {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.VM.toString());

    protected TX tx;

    protected IRepositoryCache<?, ?, ?> track;

    protected IRepositoryCache<?, ?, ?> cacheTrack;

    protected BS blockStore;

    protected TR receipt;

    protected BLK currentBlock;
}
