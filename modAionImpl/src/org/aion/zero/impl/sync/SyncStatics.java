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

/**
 * @author chris
 */
final class SyncStatics {

    private long start;

    private long startBlock;

    private long avgBlocksPerSec;

    SyncStatics(long _startBlock){
        this.start = System.currentTimeMillis();
        this.startBlock = _startBlock;
        this.avgBlocksPerSec = 0;
    }

    synchronized void update(long _blockNumber){
        avgBlocksPerSec = (_blockNumber - startBlock) * 1000 / (System.currentTimeMillis() - start);
    }

    synchronized long getAvgBlocksPerSec(){
        return this.avgBlocksPerSec;
    }
}
