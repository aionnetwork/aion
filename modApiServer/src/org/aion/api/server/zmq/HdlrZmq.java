package org.aion.api.server.zmq;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
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
    private static final Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.API.name());

    private final IApiAion api;

    public HdlrZmq(final IApiAion api) {
        this.api = api;

        LOGGER.info("AionAPI Implementation Initiated");
    }

    public void shutDown() {
        api.shutDown();
    }

    public byte[] process(byte[] request, byte[] socketId) {
        try {
            return this.api.process(request, socketId);
        } catch (Exception e) {
            LOGGER.error("zmq incoming msg process failed! ", e);
            return ApiUtil.toReturnHeader(
                    this.api.getApiVersion(),
                    Message.Retcode.r_fail_zmqHandler_exception_VALUE,
                    ApiUtil.getApiMsgHash(request));
        }
    }

    void getTxWait() {
        TxWaitingMappingUpdate txWait = null;
        try {
            txWait = this.api.takeTxWait();

            if (txWait.isDummy()) {
                // shutdown process
                return;
            }

        } catch (Exception e) {
            // TODO Auto-generated catch block
            LOGGER.error("zmq takeTxWait failed! ", e);
        }
        Map.Entry<ByteArrayWrapper, ByteArrayWrapper> entry = null;
        if (txWait != null) {
            entry = this.api.getMsgIdMapping().get(txWait.getTxHash());
        }

        if (entry != null) {
            this.api
                    .getPendingStatus()
                    .add(
                            new TxPendingStatus(
                                    txWait.getTxHash(),
                                    entry.getValue(),
                                    entry.getKey(),
                                    txWait.getState(),
                                    txWait.getTxResult(),
                                    txWait.getTxReceipt().getError()));

            // INCLUDED(3);
            if (txWait.getState() == 1 || txWait.getState() == 2) {
                this.api.getPendingReceipts().put(txWait.getTxHash(), txWait.getTxReceipt());
            } else {
                this.api.getPendingReceipts().remove(txWait.getTxHash());
                this.api.getMsgIdMapping().remove(txWait.getTxHash());
            }
        }
    }

    public Map<Long, Fltr> getFilter() {
        return this.api.getFilter();
    }

    BlockingQueue<TxPendingStatus> getTxStatusQueue() {
        return this.api.getPendingStatus();
    }

    byte[] toRspMsg(byte[] msgHash, int txCode, String error) {
        return ApiUtil.toReturnHeader(this.api.getApiVersion(), txCode, msgHash, error.getBytes());
    }

    byte[] toRspMsg(byte[] msgHash, int txCode, String error, byte[] result) {
        return ApiUtil.toReturnHeader(
                this.api.getApiVersion(), txCode, msgHash, error.getBytes(), result);
    }

    @Override
    public byte[] process(byte[] request) {
        return null;
    }

    byte[] toRspEvtMsg(byte[] ecb) {
        return ApiUtil.toReturnEvtHeader(this.api.getApiVersion(), ecb);
    }

    public void shutdown() {
        this.getTxStatusQueue().add(new TxPendingStatus(null, null, null, 0, null, ""));
        this.api.getTxWait().add(new TxWaitingMappingUpdate(null, 0, null));
    }
}
