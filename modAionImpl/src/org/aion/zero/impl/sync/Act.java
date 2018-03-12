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

package org.aion.zero.impl.sync;

//import java.util.HashSet;
//import java.util.Set;

/**
 * @author chris
 */
public final class Act {

    public static final byte REQ_STATUS = 0;

    public static final byte RES_STATUS = 1;

    public static final byte REQ_BLOCKS_HEADERS = 2;

    public static final byte RES_BLOCKS_HEADERS = 3;

    public static final byte REQ_BLOCKS_BODIES = 4;

    public static final byte RES_BLOCKS_BODIES = 5;

    public static final byte BROADCAST_TX = 6;

    public static final byte BROADCAST_BLOCK = 7;

//    private static final byte UNKNOWN = Byte.MAX_VALUE;

//    private static Set<Byte> active = new HashSet<>() {{
//        add(REQ_STATUS);
//        add(RES_STATUS);
//        add(REQ_BLOCKS_HEADERS);
//        add(RES_BLOCKS_HEADERS);
//        add(REQ_BLOCKS_BODIES);
//        add(RES_BLOCKS_BODIES);
//        add(BROADCAST_TX);
//        add(BROADCAST_BLOCK);
//    }};

//    /**
//     * @param _act byte
//     * @return byte
//     * method provided to filter any decoded sync action (byte)
//     */
//    static byte filter(byte _act){
//        return active.contains(_act) ? _act : UNKNOWN;
//    }

}
