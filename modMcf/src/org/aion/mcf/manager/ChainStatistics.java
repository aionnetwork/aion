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
 * Contributors:
 *     Aion foundation.
 */
package org.aion.mcf.manager;

import java.util.LinkedList;
import java.util.List;

/**
 * Chain statistics.
 */
public class ChainStatistics {

    private static final int ExecTimeListLimit = 10000;

    private long startupTimeStamp;
    private boolean consensus = true;
    private List<Long> blockExecTime = new LinkedList<>();

    public void init() {
        startupTimeStamp = System.currentTimeMillis();
    }

    public long getStartupTimeStamp() {
        return startupTimeStamp;
    }

    public boolean isConsensus() {
        return consensus;
    }

    public void lostConsensus() {
        consensus = false;
    }

    public void addBlockExecTime(long time) {
        while (blockExecTime.size() > ExecTimeListLimit) {
            blockExecTime.remove(0);
        }
        blockExecTime.add(time);
    }

    public Long getExecAvg() {

        if (blockExecTime.isEmpty()) {
            return 0L;
        }

        long sum = 0;
        for (int i = 0; i < blockExecTime.size(); ++i) {
            sum += blockExecTime.get(i);
        }

        return sum / blockExecTime.size();
    }

    public List<Long> getBlockExecTime() {
        return blockExecTime;
    }
}
