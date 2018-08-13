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
package org.aion.rlp;

import org.aion.base.util.ByteUtil;

/**
 * @author Roman Mandeleil 2014
 * @author modified by aion 2017
 */
public class RLPItem implements RLPElement {

    private static final long serialVersionUID = 4456602029225251666L;

    private final byte[] rlpData;

    /**
     * @Jay inside the RLP encode/decode logic, there is no difference between null obj and
     * zero-byte array Therefore, put empty array when we see the input data is null
     *
     * @param rlpData
     */
    public RLPItem(byte[] rlpData) {
        this.rlpData = (rlpData == null) ? ByteUtil.EMPTY_BYTE_ARRAY : rlpData;
    }

    public byte[] getRLPData() {
        // @Jay
        // TODO: the ethereumJ implement the comment code piece, it will make
        // ambiguous with the null RLPItem and the
        // Empty byte array
        // if (rlpData.length == 0) {
        // return null;
        // }
        return rlpData;
    }
}
