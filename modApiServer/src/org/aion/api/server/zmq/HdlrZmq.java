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

package org.aion.api.server.zmq;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import org.aion.api.server.ApiUtil;
import org.aion.api.server.IApiAion;
import org.aion.api.server.pb.IHdlr;
import org.aion.api.server.pb.Message;
import org.aion.api.server.pb.TxWaitingMappingUpdate;
import org.aion.api.server.types.Fltr;
import org.aion.api.server.types.TxPendingStatus;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.NativeLoader;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

public class HdlrZmq implements IHdlr {

    static {
        NativeLoader.loadLibrary("zmq");
    }

    private static final Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.API.name());

    private final IApiAion api;

    public HdlrZmq(final IApiAion api) {
        this.api = api;

        LOGGER.info("AionAPI Implementation Initiated");
    }

    public byte[] process(byte[] request, byte[] socketId) {
        try {
            return this.api.process(request, socketId);
        } catch (Exception e) {
            LOGGER.error("zmq incoming msg process failed! " + e.getMessage());
            return ApiUtil.toReturnHeader(this.api.getApiVersion(), Message.Retcode.r_fail_zmqHandler_exception_VALUE,
                    ApiUtil.getApiMsgHash(request));
        }
    }

    public void getTxWait() {
        TxWaitingMappingUpdate txWait = null;
        try {
            txWait = this.api.takeTxWait();

            if (txWait.isDummy()) {
                // shutdown process
                return;
            }

        } catch (Throwable e) {
            // TODO Auto-generated catch block
            LOGGER.error("zmq takeTxWait failed! " + e.getMessage());
        }
        Map.Entry<ByteArrayWrapper, ByteArrayWrapper> entry = this.api.getMsgIdMapping().get(txWait.getTxHash());
        while (entry == null) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                LOGGER.error("HdlrZmq.getTxWait exception " + e.getMessage());
            }
            entry = this.api.getMsgIdMapping().get(txWait.getTxHash());
        }

        this.api.getPendingStatus().add(new TxPendingStatus(txWait.getTxHash(), entry.getValue(), entry.getKey(),
                txWait.getState(), txWait.getTxResult()));

        // INCLUDED(3);
        if (txWait.getState() == 1 || txWait.getState() == 2) {
            this.api.getPendingReceipts().put(txWait.getTxHash(), txWait.getTxReceipt());
        } else {
            this.api.getPendingReceipts().remove(txWait.getTxHash());
            this.api.getMsgIdMapping().remove(txWait.getTxHash());
        }
    }

    public Map<Long, Fltr> getFilter() {
        return this.api.getFilter();
    }

    public LinkedBlockingQueue<TxPendingStatus> getTxStatusQueue() {
        return this.api.getPendingStatus();
    }

    public byte[] toRspMsg(byte[] msgHash, int txCode) {
        return ApiUtil.toReturnHeader(this.api.getApiVersion(), txCode, msgHash);
    }

    public byte[] toRspMsg(byte[] msgHash, int txCode, byte[] result) {
        return ApiUtil.toReturnHeader(this.api.getApiVersion(), txCode, msgHash, result);
    }

    @Override
    public byte[] process(byte[] request) {
        return null;
    }

    public byte[] toRspEvtMsg(byte[] ecb) {
        return ApiUtil.toReturnEvtHeader(this.api.getApiVersion(), ecb);
    }

    public void shutdown() {
        this.getTxStatusQueue().add(new TxPendingStatus(null, null, null, 0, null));
        this.api.getTxWait().add(new TxWaitingMappingUpdate(null, 0, null));
    }
}
