/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.zero.impl.tx;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.aion.base.type.ITransaction;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.sync.msg.BroadcastTx;

/**
 * @author jin
 * @modified jay@Sep.2017
 */
// public abstract class AbstractTxTask<TX extends ITransaction, CHANMGR extends
// AbstractChanMgr, CHAN extends AbstractChannel> implements Callable<List<TX>>
// {
public abstract class AbstractTxTask<TX extends ITransaction, P2P extends IP2pMgr> implements Callable<List<TX>> {

    protected final List<TX> tx;
    protected final P2P p2pMgr;

    public AbstractTxTask(TX _tx, P2P _p2pMgr) {
        this.tx = Collections.singletonList(_tx);
        this.p2pMgr = _p2pMgr;
    }

    public AbstractTxTask(List<TX> _tx, P2P _p2pMgr) {
        this.tx = _tx;
        this.p2pMgr = _p2pMgr;
    }

    /**
     * Class fails silently
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<TX> call() throws Exception {

        try {
            List<INode> activeNodes = this.p2pMgr.getActiveNodes();
            if (activeNodes != null && !activeNodes.isEmpty()) {
                for (INode n : activeNodes) {
                    this.p2pMgr.send(n.getIdHash(), new BroadcastTx((List<ITransaction>) this.tx));
                }
            }

            return tx;
        } catch (Throwable th) {
            // Todo : Log
            System.out.println(th.getMessage());
        }

        return null;
    }
}
