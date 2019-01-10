package org.aion.api.server;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import org.aion.api.server.pb.TxWaitingMappingUpdate;
import org.aion.api.server.types.Fltr;
import org.aion.api.server.types.TxPendingStatus;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.zero.types.AionTxReceipt;

public interface IApiAion {

    byte[] parseMsgReq(byte[] request, byte[] msgHash);

    Map<Long, Fltr> getFilter();

    Map<ByteArrayWrapper, AionTxReceipt> getPendingReceipts();

    BlockingQueue<TxPendingStatus> getPendingStatus();

    BlockingQueue<TxWaitingMappingUpdate> getTxWait();

    // General Level
    byte getApiVersion();

    byte getApiHeaderLen();

    int getTxHashLen();

    byte[] process(byte[] request, byte[] socketId);

    Map<ByteArrayWrapper, Map.Entry<ByteArrayWrapper, ByteArrayWrapper>> getMsgIdMapping();

    void shutDown();

    TxWaitingMappingUpdate takeTxWait() throws Exception;
}
