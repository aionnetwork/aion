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
import java.util.ArrayList;
import java.util.List;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;

/** @author chris */
public final class ReqBlocksBodies extends Msg {

    private final List<byte[]> blocksHashes;

    public ReqBlocksBodies(final List<byte[]> _blocksHashes) {
        super(Ver.V0, Ctrl.SYNC, Act.REQ_BLOCKS_BODIES);
        blocksHashes = _blocksHashes;
    }

    public static ReqBlocksBodies decode(final byte[] _msgBytes) {
        if (_msgBytes == null) return null;
        else {
            /*
             * _msgBytes % 32 needs to be equal to 0 TODO: need test & catch
             */
            List<byte[]> blocksHashes = new ArrayList<>();
            ByteBuffer bb = ByteBuffer.wrap(_msgBytes);
            int count = _msgBytes.length / 32;
            while (count > 0) {
                byte[] blockHash = new byte[32];
                bb.get(blockHash);
                blocksHashes.add(blockHash);
                count--;
            }
            return new ReqBlocksBodies(blocksHashes);
        }
    }

    public List<byte[]> getBlocksHashes() {
        return this.blocksHashes;
    }

    @Override
    public byte[] encode() {
        ByteBuffer bb = ByteBuffer.allocate(this.blocksHashes.size() * 32);
        for (byte[] blockHash : this.blocksHashes) {
            bb.put(blockHash);
        }
        return bb.array();
    }
}
