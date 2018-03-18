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
 *
 */

package org.aion.zero.impl.sync;

import java.math.BigInteger;

/**
 * @author chris
 * used by sync mgr display logging
 */
final class NetworkStatus {

    private String targetDisplayId;

    private BigInteger targetTotalDiff;

    private long targetBestBlockNumber;

    private String targetBestBlockHash;

    NetworkStatus(){
        this.targetDisplayId = "";
        this.targetTotalDiff = BigInteger.ZERO;
        this.targetBestBlockNumber = 0;
        this.targetBestBlockHash = "";
    }

    synchronized void update(
        String _targetDisplayId,
        BigInteger _targetTotalDiff,
        long _targetBestBlockNumber,
        String _targetBestBlockHash
    ){
        this.targetDisplayId = _targetDisplayId;
        this.targetTotalDiff = _targetTotalDiff;
        this.targetBestBlockNumber = _targetBestBlockNumber;
        this.targetBestBlockHash = _targetBestBlockHash;
    }

    String getTargetDisplayId(){
        return this.targetDisplayId;
    }

    BigInteger getTargetTotalDiff(){
        return this.targetTotalDiff;
    }

    long getTargetBestBlockNumber(){
        return this.targetBestBlockNumber;
    }

    String getTargetBestBlockHash(){
        return this.targetBestBlockHash;
    }

}
