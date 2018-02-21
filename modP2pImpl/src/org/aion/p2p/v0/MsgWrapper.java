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
 * Contributors to the aion source files in decreasing order of code volume:
 * 
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.p2p.a0;

import java.util.Arrays;

import org.aion.p2p.IMsg;

/**
 * 
 * @author chris
 *
 */
final class MsgWrapper{

    private byte[] nodeId;
    
    private int nodeIdHash;

    private IMsg msg;
    
    private int channelHashCode;
    
    MsgWrapper(final byte[] _nodeId, final int _channelHashCode, final IMsg _msg){
        this.nodeId = _nodeId;
        this.nodeIdHash = Arrays.hashCode(_nodeId);
        this.channelHashCode = _channelHashCode;
        this.msg = _msg;
    }
    
    int getNodeIdHash() {
        return this.nodeIdHash;
    }

    IMsg getMsg() {
        return this.msg;
    }
    
    int getChannelHashCode() {
        return this.channelHashCode;
    }
    
    byte[] getNodeId() {
        return this.nodeId;
    }
    
}
