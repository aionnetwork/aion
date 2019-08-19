package org.aion.zero.impl.tx;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.aion.base.AionTransaction;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Msg;

public class A0TxTask implements Callable<List<AionTransaction>> {

    protected final List<AionTransaction> tx;
    protected final IP2pMgr p2pMgr;
    protected final Msg msg;

    public A0TxTask(List<AionTransaction> _tx, IP2pMgr _p2pMgr, Msg _msg) {
        this.tx = _tx;
        this.p2pMgr = _p2pMgr;
        this.msg = _msg;
    }

    /** Class fails silently */
    @SuppressWarnings("unchecked")
    @Override
    public List<AionTransaction> call() {

        try {
            Map<Integer, INode> activeNodes = this.p2pMgr.getActiveNodes();
            if (activeNodes != null) {
                for (Map.Entry<Integer, INode> e : activeNodes.entrySet()) {
                    this.p2pMgr.send(e.getKey(), e.getValue().getIdShort(), this.msg);
                }
            }

            return tx;
        } catch (Exception e) {
            // Todo : Log
            e.printStackTrace();
        }

        return null;
    }
}
