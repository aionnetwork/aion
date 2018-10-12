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

import org.aion.base.util.ByteArrayWrapper;
import org.aion.mcf.evt.IListenerBase;

public class TxPendingStatus {

    private final static int txRetCodeOffset = 102;
    ByteArrayWrapper txhash;
    ByteArrayWrapper socketId;
    ByteArrayWrapper msgHash;
    ByteArrayWrapper txResult;
    String error;

    /*  */
    /**
     * @see IListenerBase DROPPED(0) NEW_PENDING(1) PENDING(2) INCLUDED(3)
     */

    int state;

    public TxPendingStatus(ByteArrayWrapper txHash, ByteArrayWrapper id, ByteArrayWrapper msgHash,
        int v,
        ByteArrayWrapper txRes, String error) {
        // TODO Auto-generated constructor stub
        this.txhash = txHash;
        this.socketId = id;
        this.msgHash = msgHash;
        this.state = v;
        this.txResult = txRes;
        this.error = error;
    }

    public byte[] getSocketId() {
        return this.socketId.getData();
    }

    public byte[] getMsgHash() {
        return this.msgHash.getData();
    }

    public int getPendStatus() {
        return this.state;
    }

    public byte[] getTxHash() {
        return this.txhash.getData();
    }

    public byte[] getTxResult() {
        return this.txResult.getData();
    }

    public String getError() {
        return this.error;
    }

    public int toTxReturnCode() {
        return this.state + txRetCodeOffset;
    }

    public boolean isEmpty() {
        return txhash == null && socketId == null;
    }
}
