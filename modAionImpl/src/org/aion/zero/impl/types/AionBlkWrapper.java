/*
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
 */
package org.aion.zero.impl.types;

import java.math.BigInteger;
import java.util.List;
import org.aion.mcf.types.AbstractBlockWrapper;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;

public class AionBlkWrapper extends AbstractBlockWrapper<AionBlock> implements
    Comparable<AionBlkWrapper> {

    public AionBlkWrapper(AionBlock block, byte[] nodeId) {
        super(block, false, nodeId);
    }

    public AionBlkWrapper(AionBlock block, boolean newBlock, byte[] nodeId) {
        super(block, newBlock, nodeId);
    }

    public AionBlkWrapper(byte[] bytes) {
        super(bytes);
    }

    @Override
    protected void parse(byte[] bytes) {
        List<RLPElement> params = RLP.decode2(bytes);
        List<RLPElement> wrapper = (RLPList) params.get(0);

        byte[] blockBytes = wrapper.get(0).getRLPData();
        byte[] importFailedBytes = wrapper.get(1).getRLPData();
        byte[] receivedAtBytes = wrapper.get(2).getRLPData();
        byte[] newBlockBytes = wrapper.get(3).getRLPData();

        this.block = new AionBlock(blockBytes);
        this.importFailedAt =
            importFailedBytes == null ? 0 : new BigInteger(1, importFailedBytes).longValue();
        this.receivedAt =
            receivedAtBytes == null ? 0 : new BigInteger(1, receivedAtBytes).longValue();
        byte newBlock = newBlockBytes == null ? 0 : new BigInteger(1, newBlockBytes).byteValue();
        this.newBlock = newBlock == 1;
        this.nodeId = wrapper.get(4).getRLPData();
    }

    @Override
    public int compareTo(AionBlkWrapper nbw) {
        return (int) nbw.getNumber();
    }

}
