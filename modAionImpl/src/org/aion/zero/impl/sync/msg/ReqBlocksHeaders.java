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
 * The aion network project leverages useful source code from other
 * open source projects. We greatly appreciate the effort that was
 * invested in these projects and we thank the individual contributors
 * for their work. For provenance information and contributors
 * please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 * Aion foundation.
 * <ether.camp> team through the ethereumJ library.
 * Ether.Camp Inc. (US) team through Ethereum Harmony.
 * John Tromp through the Equihash solver.
 * Samuel Neves through the BLAKE2 implementation.
 * Zcash project team.
 * Bitcoinj team.
 */

package org.aion.zero.impl.sync.msg;

import java.nio.ByteBuffer;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;

/** @author chris */
public final class ReqBlocksHeaders extends Msg {

    /** fromBlock(long), take(int) */
    private static final int len = 8 + 4;

    private final long fromBlock;

    private final int take;

    public ReqBlocksHeaders(final long _fromBlock, final int _take) {
        super(Ver.V0, Ctrl.SYNC, Act.REQ_BLOCKS_HEADERS);
        this.fromBlock = _fromBlock;
        this.take = _take;
    }

    public long getFromBlock() {
        return this.fromBlock;
    }

    public int getTake() {
        return this.take;
    }

    public static ReqBlocksHeaders decode(final byte[] _msgBytes) {
        if (_msgBytes == null || _msgBytes.length != len) return null;
        else {
            ByteBuffer bb = ByteBuffer.wrap(_msgBytes);
            long _fromBlock = bb.getLong();
            int _take = bb.getInt();
            return new ReqBlocksHeaders(_fromBlock, _take);
        }
    }

    @Override
    public byte[] encode() {
        ByteBuffer bb = ByteBuffer.allocate(len);
        bb.putLong(this.fromBlock);
        bb.putInt(this.take);
        return bb.array();
    }
}
