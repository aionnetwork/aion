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

package org.aion.zero.impl.types;

import java.util.List;

import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.zero.types.A0BlockHeader;
import org.aion.mcf.types.AbstractBlockHeaderWrapper;

public class AionBlockHeaderWrapper extends AbstractBlockHeaderWrapper<A0BlockHeader> {

    public AionBlockHeaderWrapper(byte[] bytes) {
        parse(bytes);
    }

    public AionBlockHeaderWrapper(A0BlockHeader header, byte[] nodeId) {
        super(header, nodeId);
    }

    protected void parse(byte[] bytes) {
        List<RLPElement> params = RLP.decode2(bytes);
        List<RLPElement> wrapper = (RLPList) params.get(0);

        byte[] headerBytes = wrapper.get(0).getRLPData();
        this.header = new A0BlockHeader(headerBytes);
        this.nodeId = wrapper.get(1).getRLPData();
    }
}
