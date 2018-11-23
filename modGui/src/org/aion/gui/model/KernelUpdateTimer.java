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

package org.aion.gui.model;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.aion.gui.util.AionConstants;
import org.aion.gui.util.DataUpdater;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

public class KernelUpdateTimer {
    private final ScheduledExecutorService timer;
    private ScheduledFuture<?> execution;
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GUI.name());

    public KernelUpdateTimer(ScheduledExecutorService timer) {
        this.timer = timer;
    }

    /** Start timer. If already started, this has no effect. */
    public void start() {
        LOG.debug("Started timer");
        if (execution == null) {
            execution =
                    timer.scheduleAtFixedRate(
                            new DataUpdater(),
                            AionConstants.BLOCK_MINING_TIME_MILLIS,
                            //                    3 * AionConstants.BLOCK_MINING_TIME_MILLIS,
                            AionConstants.BLOCK_MINING_TIME_MILLIS,
                            TimeUnit.MILLISECONDS);
        }
    }

    /** Stop timer. If already stopped, this has no effect. */
    public void stop() {
        LOG.debug("Stopped timer");
        if (execution != null) {
            execution.cancel(true);
        }
        execution = null;
    }
}
