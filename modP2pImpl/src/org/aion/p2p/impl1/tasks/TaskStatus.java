/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p.impl1.tasks;

import java.util.concurrent.LinkedBlockingQueue;
import org.aion.p2p.impl.comm.NodeMgr;
import org.aion.p2p.impl1.tasks.TaskReceive.MsgIn;
import org.aion.p2p.impl1.tasks.TaskSend.MsgOut;

public class TaskStatus implements Runnable {
    private final NodeMgr nodeMgr;
    private final String selfShortId;
    private LinkedBlockingQueue<MsgOut> sendMsgQue;
    private LinkedBlockingQueue<MsgIn> receiveMsgQue;

    public TaskStatus(
            NodeMgr _nodeMgr,
            String _selfShortId,
            LinkedBlockingQueue<MsgOut> _sendMsgQue,
            LinkedBlockingQueue<MsgIn> _receiveMsgQue) {
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
