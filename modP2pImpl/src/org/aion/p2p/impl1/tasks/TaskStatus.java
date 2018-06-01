/*
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
 */
package org.aion.p2p.impl1.tasks;

import java.util.concurrent.BlockingQueue;
import org.aion.p2p.INodeMgr;

public class TaskStatus implements Runnable {
    private final INodeMgr nodeMgr;
    private final String selfShortId;
    private BlockingQueue<MsgOut> sendMsgQue;
    private BlockingQueue<MsgIn> receiveMsgQue;

    public TaskStatus(
            INodeMgr _nodeMgr,
            String _selfShortId,
            BlockingQueue<MsgOut> _sendMsgQue,
            BlockingQueue<MsgIn> _receiveMsgQue) {
        this.nodeMgr = _nodeMgr;
        this.selfShortId = _selfShortId;
        this.sendMsgQue = _sendMsgQue;
        this.receiveMsgQue = _receiveMsgQue;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("p2p-ts");
        String status = this.nodeMgr.dumpNodeInfo(this.selfShortId);
        System.out.println(status);
        System.out.println("--------------------------------------------------------------------" +
                "-------------------------------------------------------------------------------" +
                "-----------------");
        System.out.println(
                "recv queue ["
                        + this.receiveMsgQue.size()
                        + "] send queue ["
                        + this.sendMsgQue.size()
                        + "]\n");
    }
}
