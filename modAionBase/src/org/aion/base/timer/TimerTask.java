/**
 * ***************************************************************************** Copyright (c)
 * 2017-2018 Aion foundation.
 *
 * <p>This file is part of the aion network project.
 *
 * <p>The aion network project is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * <p>The aion network project is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with the aion network
 * project source files. If not, see <https://www.gnu.org/licenses/>.
 *
 * <p>The aion network project leverages useful source code from other open source projects. We
 * greatly appreciate the effort that was invested in these projects and we thank the individual
 * contributors for their work. For provenance information and contributors please see
 * <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * <p>Contributors to the aion source files in decreasing order of code volume: Aion foundation.
 * <ether.camp> team through the ethereumJ library. Ether.Camp Inc. (US) team through Ethereum
 * Harmony. John Tromp through the Equihash solver. Samuel Neves through the BLAKE2 implementation.
 * Zcash project team. Bitcoinj team.
 * ****************************************************************************
 */
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aion.base.timer;

/** @author jin */
public class TimerTask {

    public TimerTask(long timeOut) {
        this.timeOut = timeOut;
    }

    public Object lock = new Object();

    private long timeOut;
    private long startTime;
    private long timeoutAt;
    private long endTime;

    private boolean isTimeOut;
    private boolean isDone;

    public long getEndTime() {
        return this.endTime;
    }

    public long getTimeout() {
        return this.timeOut;
    }

    public void start() {
        this.startTime = System.nanoTime();
        this.endTime = this.startTime + this.timeOut;
    }

    public long getRemaining() {
        return Math.max(getEndTime() - System.nanoTime(), 1L);
    }

    public boolean isTimeOut() {
        return isTimeOut;
    }

    public synchronized void setDone() {
        this.isDone = true;
    }

    public boolean getDone() {
        return this.isDone;
    }

    protected synchronized void setTimeOut() {
        if (isTimeOut == false) {
            isTimeOut = true;
            timeoutAt = System.nanoTime();
        }
    }

    public long getTimeoutDuration() {
        return this.timeoutAt - this.startTime;
    }
}
