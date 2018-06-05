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
import org.slf4j.Logger;

public class TaskStatus implements Runnable {

    private final INodeMgr nodeMgr;
    private final String selfShortId;
    private final BlockingQueue<MsgOut> sendMsgQue;
    private final BlockingQueue<MsgIn> receiveMsgQue;
    private final Logger logger;

    public TaskStatus(
        Logger _logger, final INodeMgr _nodeMgr,
        final String _selfShortId,
        final BlockingQueue<MsgOut> _sendMsgQue,
        final BlockingQueue<MsgIn> _receiveMsgQue) {
        this.nodeMgr = _nodeMgr;
        this.selfShortId = _selfShortId;
        this.sendMsgQue = _sendMsgQue;
        this.receiveMsgQue = _receiveMsgQue;
        this.logger = _logger;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("p2p-ts");
        String status = this.nodeMgr.dumpNodeInfo(this.selfShortId);

        if (logger.isDebugEnabled()) {
            logger.debug(status);
            logger.debug("recv queue[{}] send queue[{}]", this.receiveMsgQue.size(),
                this.sendMsgQue.size());
        }
    }
}
