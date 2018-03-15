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
 */

package org.aion.zero.impl.sync;

import org.aion.base.util.Hex;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author chris
 *
 * The thread print out sync status
 *
 */
final class TaskShowStatus implements Runnable {

    private final AtomicBoolean start;

    private final int interval;

    private final AionBlockchainImpl chain;

    private final AtomicLong jump;

    private final AtomicReference<NetworkStatus> networkStatus;

    private final SyncStatis statis;

    private final Logger log;

    TaskShowStatus(final AtomicBoolean _start, int _interval, final AionBlockchainImpl _chain, final AtomicLong _jump, final AtomicReference<NetworkStatus> _networkStatus, final SyncStatis _statis, final Logger _log){
        this.start = _start;
        this.interval = _interval;
        this.chain = _chain;
        this.jump = _jump;
        this.networkStatus = _networkStatus;
        this.statis = _statis;
        this.log = _log;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        while(this.start.get()){
            AionBlock blk = this.chain.getBestBlock();
            System.out.println(
                "[sync-status avg-import=" + this.statis.getAvgBlocksPerSec()
                + " b/s jump=" + jump.get()
                + " td=" + this.chain.getTotalDifficulty().toString(10) + "/" + networkStatus.get().totalDiff.toString(10)
                + " b-num=" + blk.getNumber() + "/" + this.networkStatus.get().blockNumber
                + " b-hash=" + Hex.toHexString(this.chain.getBestBlockHash()) + "/" + Hex.toHexString(networkStatus.get().blockHash) + "]");
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                if(log.isDebugEnabled())
                    log.debug("<sync-ss shutdown>");
                return;
            }
        }
        if(log.isDebugEnabled())
            log.debug("<sync-ss shutdown>");
    }
}