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

import java.util.ArrayList;
import java.util.List;

import org.aion.base.type.ITransaction;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.rlp.RLPElement;
import org.aion.zero.impl.sync.Act;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;

/**
 * @author chris
 *
 */
public final class BroadcastTx extends Msg {

    private final List<ITransaction> txl;

    public BroadcastTx(final List<ITransaction> _txl) {
        super(Ver.V0, Ctrl.SYNC, Act.BROADCAST_TX);
        this.txl = _txl;
    }

    /* (non-Javadoc)
     * @see org.aion.net.nio.IMsg#encode()
     */
    @Override
    public byte[] encode() {
        List<byte[]> encodedTx = new ArrayList<>();
        for(ITransaction tx : txl) {
            encodedTx.add(tx.getEncoded());
        }

        return RLP.encodeList(encodedTx.toArray(new byte[encodedTx.size()][]));
    }

    /* return the encodedData of the Transaction list, the caller function need to cast the return byte[] array
     */
    public static List<byte[]> decode(final byte[] _msgBytes) {
        RLPList paramsList = (RLPList) RLP.decode2(_msgBytes).get(0);
        List<byte[]> txl = new ArrayList<>();
        for (RLPElement aParamsList : paramsList) {
            RLPList rlpData = ((RLPList) aParamsList);
            txl.add(rlpData.getRLPData());
        }
        return txl;
    }
}
