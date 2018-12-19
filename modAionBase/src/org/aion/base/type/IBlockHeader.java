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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 */

package org.aion.base.type;

import org.aion.vm.api.interfaces.Address;

/** @author jay */
public interface IBlockHeader {

    // Getter
    byte[] getParentHash();

    byte[] getStateRoot();

    byte[] getTxTrieRoot();

    byte[] getReceiptsRoot();

    byte[] getLogsBloom();

    byte[] getExtraData();

    byte[] getNonce();

    byte[] getHash();

    byte[] getEncoded();

    Address getCoinbase();

    long getTimestamp();

    long getNumber();

    // Setter
    void setCoinbase(Address _cb);

    void setStateRoot(byte[] _strt);

    void setReceiptsRoot(byte[] _rcrt);

    void setTransactionsRoot(byte[] _txrt);

    void setTimestamp(long _ts);

    void setNumber(long _nb);

    void setNonce(byte[] _nc);

    void setLogsBloom(byte[] _lb);

    void setExtraData(byte[] _ed);

    boolean isGenesis();
}
