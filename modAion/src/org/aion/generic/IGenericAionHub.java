package org.aion.generic;

import org.aion.base.db.IRepository;
import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.evtmgr.IEventMgr;
import org.aion.mcf.blockchain.IPendingStateInternal;
import org.aion.mcf.core.AbstractTxInfo;
import org.aion.mcf.core.IBlockchain;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.types.AbstractBlockHeader;
import org.aion.mcf.types.AbstractTxReceipt;
import org.aion.p2p.IP2pMgr;

public interface IGenericAionHub<
        BLK extends IBlock<?, ?>,
        BH extends AbstractBlockHeader,
        TX extends ITransaction,
        TR extends AbstractTxReceipt,
        INFO extends AbstractTxInfo> {
    boolean isRunning();

    IRepository getRepository();

    IBlockchain<BLK, BH, TX, TR, INFO> getBlockchain();

    IBlockStoreBase<BLK, BH> getBlockStore();

    IPendingStateInternal<BLK, TX> getPendingState();

    IEventMgr getEventMgr();

    IBlockPropagationHandler<BLK> getPropHandler();

    void close();

    //TODO: add a sync mgr

    IP2pMgr getP2pMgr();

    BLK getStartingBlock();
}
