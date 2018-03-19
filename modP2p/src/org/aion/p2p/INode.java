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
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p;

import java.math.BigInteger;

/**
 *
 * @author chris
 *
 */
public interface INode {

    /**
     * @return byte[]
     */
    byte[] getId();

    /**
     * @return int
     */
    int getIdHash();

    /**
     * @return String
     */
    String getIdShort();

    /**
     * @return byte[]
     */
    byte[] getIp();

    /**
     * @return String
     */
    String getIpStr();

    /**
     * @return int
     */
    int getPort();

    /**
     * @return long
     */
    long getBestBlockNumber();

    /**
     * @return BigInteger
     */
    BigInteger getTotalDifficulty();

    /**
     * @param _bestBlockNumber long
     * @param _bestBlockHash byte[]
     * @param _totalDifficulty long
     */
    void updateStatus(long _bestBlockNumber, final byte[] _bestBlockHash, BigInteger _totalDifficulty);

}
