package org.aion.zero.impl;

import org.aion.base.db.IRepository;
import org.aion.evtmgr.IEventMgr;
import org.aion.generic.IGenericAionHub;
import org.aion.mcf.blockchain.IPendingStateInternal;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.db.IBlockStorePow;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.handler.BlockPropagationHandler;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;

public interface IAion0Hub extends IGenericAionHub<AionBlock, A0BlockHeader, AionTransaction, AionTxReceipt, AionTxInfo> {
    boolean isRunning();

    IRepository getRepository();

    IAionBlockchain getBlockchain();

    IBlockStorePow<AionBlock, A0BlockHeader> getBlockStore();

    IPendingStateInternal<AionBlock, AionTransaction> getPendingState();

    IEventMgr getEventMgr();

    BlockPropagationHandler getPropHandler();

    void close();

    SyncMgr getSyncMgr();

    IP2pMgr getP2pMgr();

    AionBlock getStartingBlock();
}
