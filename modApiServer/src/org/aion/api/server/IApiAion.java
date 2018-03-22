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

package org.aion.api.server;

import org.aion.api.server.pb.TxWaitingMappingUpdate;
import org.aion.api.server.types.Fltr;
import org.aion.api.server.types.TxPendingStatus;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.collections4.map.LRUMap;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public interface IApiAion {
    int MAP_SIZE = 50000;
    LinkedBlockingQueue<TxPendingStatus> txPendingStatus = new LinkedBlockingQueue<>();
    LinkedBlockingQueue<TxWaitingMappingUpdate> txWait = new LinkedBlockingQueue<>();
    Map<ByteArrayWrapper, Map.Entry<ByteArrayWrapper, ByteArrayWrapper>> msgIdMapping = Collections
            .synchronizedMap(new LRUMap<>(MAP_SIZE, 100));

    default byte[] parseMsgReq(byte[] request, byte[] msgHash) {
        int headerLen = msgHash == null ? this.getApiHeaderLen() : this.getApiHeaderLen() + msgHash.length;
        return ByteBuffer.allocate(request.length - headerLen).put(request, headerLen, request.length - headerLen)
                .array();
    }

    Map<Long, Fltr> getFilter();

    Map<ByteArrayWrapper, AionTxReceipt> getPendingReceipts();

    default LinkedBlockingQueue<TxPendingStatus> getQueue() {
        return txPendingStatus;
    }

    // General Level
    byte getApiVersion();

    byte getApiHeaderLen();

    int getTxHashLen();

    byte[] process(byte[] request, byte[] socketId);

    default Map<ByteArrayWrapper, Map.Entry<ByteArrayWrapper, ByteArrayWrapper>> getMsgIdMapping() {
        return msgIdMapping;
    }

    default TxWaitingMappingUpdate takeTxWait() throws Throwable {
        return txWait.take();
    }

    void shutDown();
}
