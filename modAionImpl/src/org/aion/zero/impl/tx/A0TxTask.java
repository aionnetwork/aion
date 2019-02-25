package org.aion.zero.impl.tx;

import java.util.List;
import org.aion.mcf.tx.AbstractTxTask;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Msg;
import org.aion.zero.types.AionTransaction;

public class A0TxTask extends AbstractTxTask<AionTransaction, IP2pMgr> {

    public A0TxTask(AionTransaction _tx, IP2pMgr _p2pMgr, Msg _msg) {
        super(_tx, _p2pMgr, _msg);
    }

    public A0TxTask(List<AionTransaction> _tx, IP2pMgr _p2pMgr, Msg _msg) {
        super(_tx, _p2pMgr, _msg);
    }
}
