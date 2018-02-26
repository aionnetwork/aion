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

package org.aion.zero.impl.sync.msg;

import java.nio.ByteBuffer;

import org.aion.p2p.CTRL;
import org.aion.p2p.IMsg;
import org.aion.p2p.P2pVer;
import org.aion.zero.impl.sync.ACT;

/*
 * Author: Chris
 * TODO: test cases
 */

public final class ResStatus implements IMsg {

    private final static byte ctrl = CTRL.SYNC0;

    private final static byte act = ACT.RES_STATUS;

    private final static int minLen = 8 + 1 + 1 + 32 + 32;

    private final long bestBlockNumber; // 8

    private final int totalDifficultyLen; // 1

    private final byte[] totalDifficulty; // >= 1

    private final byte[] bestHash; // 32

    private final byte[] genesisHash; // 32

    public ResStatus(final long bestBlockNumber, final byte[] _totalDifficulty, final byte[] _bestHash,
            byte[] _genesisHash) {
        this.bestBlockNumber = bestBlockNumber;
        this.totalDifficultyLen = _totalDifficulty.length;
        this.totalDifficulty = _totalDifficulty;
        this.bestHash = _bestHash;
        this.genesisHash = _genesisHash;
    }

    public long getBestBlockNumber() {
        return this.bestBlockNumber;
    }

    public byte[] getTotalDifficulty() {
        return this.totalDifficulty;
    }

    public byte[] getBestHash() {
        return this.bestHash;
    }

    public byte[] getGenesisHash() {
        return this.genesisHash;
    }

    public static ResStatus decode(final byte[] _bytes) {
        /**
         * TODO: missing test case
         */
        if (_bytes == null || _bytes.length < minLen)
            return null;
        ByteBuffer bb = ByteBuffer.wrap(_bytes);
        bb.clear();
        long _bestBlockNumber = bb.getLong();
        int _totalDifficultyLen = bb.get();
        byte[] _totalDifficulty = new byte[_totalDifficultyLen];
        byte[] _bestHash = new byte[32];
        byte[] _genesisHash = new byte[32];
        bb.get(_totalDifficulty);
        bb.get(_bestHash);
        bb.get(_genesisHash);
        return new ResStatus(_bestBlockNumber, _totalDifficulty, _bestHash, _genesisHash);
    }

    @Override
    public byte[] encode() {
        /**
         * TODO: missing test case
         */
        if (this.totalDifficultyLen > 127)
            return new byte[0];
        int _len = 8 + 1 + totalDifficultyLen + 32 + 32;
        ByteBuffer bb = ByteBuffer.allocate(_len);
        bb.putLong(this.bestBlockNumber);
        bb.put((byte) this.totalDifficultyLen);
        bb.put(this.totalDifficulty);
        bb.put(this.bestHash);
        bb.put(this.genesisHash);
        return bb.array();
    }

    public short getVer() {
        return P2pVer.VER0;
    }

    @Override
    public byte getCtrl() {
        return ctrl;
    }

    @Override
    public byte getAct() {
        return act;
    }

}
