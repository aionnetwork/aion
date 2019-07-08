package org.aion.mcf.tx;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.aion.base.AionTransaction;
import org.aion.base.Transaction;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Msg;

/**
 * @author jin
 * @modified jay@Sep.2017
 */
public abstract class AbstractTxTask<P2P extends IP2pMgr>
        implements Callable<List<AionTransaction>> {

    protected final List<AionTransaction> tx;
    protected final P2P p2pMgr;
    protected final Msg msg;

    public AbstractTxTask(AionTransaction _tx, P2P _p2pMgr, Msg _msg) {
        this.tx = Collections.singletonList(_tx);
        this.p2pMgr = _p2pMgr;
        this.msg = _msg;
    }

    public AbstractTxTask(List<AionTransaction> _tx, P2P _p2pMgr, Msg _msg) {
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
