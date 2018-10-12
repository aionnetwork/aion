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

package org.aion.api.server.types;

import com.google.protobuf.ByteString;
import java.util.Optional;
import org.aion.api.server.pb.Message;
import org.aion.api.server.types.Fltr.Type;

public class EvtContract extends Evt {

    private final byte[] addr;
    private final byte[] data;
    private final byte[] blockHash;
    private final long blockNumber;
    private final int logIndex;
    private final String eventName;
    private final boolean removed;
    private final int txIndex;
    private final byte[] txHash;

    public EvtContract(byte[] addr, byte[] data, byte[] blockHash, long blockNumber, int logIndex,
        String eventName, boolean removed, int txIndex, byte[] txHash) {
        this.addr = addr;
        this.data = data;
        this.blockHash = blockHash;
        this.blockNumber = blockNumber;
        this.logIndex = logIndex;
        this.eventName = eventName;
        this.removed = removed;
        this.txIndex = txIndex;
        this.txHash = txHash;
    }

    public Message.t_EventCt getMsgEventCt() {
        return Message.t_EventCt.newBuilder()
            .setAddress(ByteString.copyFrom(Optional.ofNullable(this.addr).orElse(new byte[0])))
            .setBlockHash(
                ByteString.copyFrom(Optional.ofNullable(this.blockHash).orElse(new byte[0])))
            .setData(ByteString.copyFrom(Optional.ofNullable(this.data).orElse(new byte[0])))
            .setBlockNumber(this.blockNumber)
            .setLogIndex(this.logIndex)
            .setEventName(this.eventName)
            .setRemoved(this.removed)
            .setTxIndex(this.txIndex)
            .setTxHash(ByteString.copyFrom(Optional.ofNullable(this.txHash).orElse(new byte[0])))
            .build();
    }

    @Override
    public Type getType() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object toJSON() {
        // TODO Auto-generated method stub
        return null;
    }
}